package hu.szintai.hakiosk.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.di.ApplicationScope
import hu.szintai.hakiosk.ha.HaConversationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class VoicePhase { Idle, Listening, Thinking, Done, Error }

data class VoiceUi(
    val phase: VoicePhase = VoicePhase.Idle,
    /** What the recogniser heard (partial while listening, final afterwards). */
    val heard: String = "",
    /** Home Assistant's reply, or the error message. */
    val message: String = "",
)

/**
 * One-shot voice command: listen -> speech-to-text (Android `SpeechRecognizer`) ->
 * Home Assistant conversation agent -> show the reply. Only listens when asked
 * ([start]); there is no always-on wake word.
 */
@Singleton
class VoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversation: HaConversationClient,
    private val configRepository: ConfigRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow(VoiceUi())
    val state: StateFlow<VoiceUi> = _state.asStateFlow()

    private val clearRunnable = Runnable { _state.value = VoiceUi() }
    private val listenTimeout = Runnable {
        if (_state.value.phase == VoicePhase.Listening) recognizer?.stopListening()
    }

    /** Safe to call from any thread. */
    fun start() {
        main.post { startOnMain() }
    }

    fun cancel() {
        main.post {
            recognizer?.cancel()
            finish(VoiceUi())
        }
    }

    private fun startOnMain() {
        val phase = _state.value.phase
        if (phase == VoicePhase.Listening || phase == VoicePhase.Thinking) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            fail("Nincs beszédfelismerő szolgáltatás a készüléken")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Nincs mikrofon engedély")
            return
        }
        scope.launch {
            val config = configRepository.config.first()
            main.post { listen(config) }
        }
    }

    private fun listen(config: AppConfig) {
        main.removeCallbacks(clearRunnable)
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(listener(config))
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.voiceLanguage)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        _state.value = VoiceUi(VoicePhase.Listening)
        recognizer?.startListening(intent)
        main.postDelayed(listenTimeout, LISTEN_TIMEOUT_MS)
    }

    private fun listener(config: AppConfig) = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults.firstResult()
            if (text.isNotBlank()) _state.value = VoiceUi(VoicePhase.Listening, heard = text)
        }

        override fun onResults(results: Bundle?) {
            main.removeCallbacks(listenTimeout)
            val text = results.firstResult()
            if (text.isBlank()) {
                finish(VoiceUi())
                return
            }
            _state.value = VoiceUi(VoicePhase.Thinking, heard = text)
            scope.launch {
                val reply = try {
                    conversation.process(config, text).ifBlank { "Rendben" }
                } catch (e: Exception) {
                    Log.w(TAG, "conversation failed: ${e.message}")
                    main.post { fail("Nem sikerült a Home Assistant kapcsolat", heard = text) }
                    return@launch
                }
                main.post { finish(VoiceUi(VoicePhase.Done, heard = text, message = reply), REPLY_VISIBLE_MS) }
            }
        }

        override fun onError(error: Int) {
            main.removeCallbacks(listenTimeout)
            Log.i(TAG, "recogniser error=$error")
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> finish(VoiceUi())
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail("Nincs mikrofon engedély")
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> fail("Nincs hálózat a beszédfelismeréshez")
                else -> fail("Beszédfelismerési hiba ($error)")
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun fail(message: String, heard: String = "") = finish(VoiceUi(VoicePhase.Error, heard, message), ERROR_VISIBLE_MS)

    private fun finish(ui: VoiceUi, visibleMs: Long = 0) {
        main.removeCallbacks(clearRunnable)
        _state.value = ui
        if (ui.phase != VoicePhase.Idle) main.postDelayed(clearRunnable, visibleMs)
    }

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private companion object {
        const val TAG = "Voice"
        const val LISTEN_TIMEOUT_MS = 12_000L
        const val REPLY_VISIBLE_MS = 6_000L
        const val ERROR_VISIBLE_MS = 5_000L
    }
}

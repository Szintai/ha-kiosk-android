package hu.szintai.hakiosk.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
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
import hu.szintai.hakiosk.config.VoiceMode
import hu.szintai.hakiosk.di.ApplicationScope
import hu.szintai.hakiosk.ha.HaConversationClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

enum class VoicePhase { Idle, Listening, Thinking, Done, Error }

data class VoiceUi(
    val phase: VoicePhase = VoicePhase.Idle,
    /** What the recogniser heard (partial while listening, final afterwards). */
    val heard: String = "",
    /** Home Assistant's reply, or the error message. */
    val message: String = "",
    /** True while only waiting for the wake phrase in [VoiceMode.Continuous]; nothing to show. */
    val passive: Boolean = false,
)

/**
 * Voice command: listen -> speech-to-text (Android `SpeechRecognizer`) -> Home Assistant
 * conversation agent -> show the reply.
 *
 * - [start]: one command, triggered by wake / motion / the mic button.
 * - Continuous mode ([VoiceMode.Continuous]): the recogniser is restarted forever, but an
 *   utterance is only acted on when it contains the wake phrase (otherwise TV chatter would
 *   issue commands). Saying just the phrase arms the next utterance as the command.
 */
@Singleton
class VoiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversation: HaConversationClient,
    private val configRepository: ConfigRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recognizer: SpeechRecognizer? = null

    @Volatile private var config = AppConfig()
    private var continuous = false
    private var passiveSession = false
    private var armed = false // wake phrase heard alone -> next utterance is the command
    private var errorStreak = 0
    private var muted = false

    private val _state = MutableStateFlow(VoiceUi())
    val state: StateFlow<VoiceUi> = _state.asStateFlow()

    /** Continuous mode: the last sentence that did NOT contain the wake phrase (shown briefly, aids tuning). */
    private val _lastHeard = MutableStateFlow("")
    val lastHeard: StateFlow<String> = _lastHeard.asStateFlow()
    private val clearHeard = Runnable { _lastHeard.value = "" }

    /** Emits when the user is clearly present/interacting (wake phrase, command) so the display can wake. */
    private val _activity = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val activity: SharedFlow<Unit> = _activity.asSharedFlow()

    private val clearRunnable = Runnable {
        _state.value = VoiceUi()
        resumePassive(RESTART_MS)
    }
    private val listenTimeout = Runnable {
        if (_state.value.phase == VoicePhase.Listening && !passiveSession) recognizer?.stopListening()
    }

    init {
        scope.launch {
            configRepository.config.collect { cfg ->
                config = cfg
                main.post { applyMode(cfg) }
            }
        }
    }

    /** Safe to call from any thread: listen for one command now. */
    fun start() {
        main.post { startOnMain() }
    }

    fun cancel() {
        main.post {
            recognizer?.cancel()
            main.removeCallbacks(listenTimeout)
            finish(VoiceUi())
        }
    }

    // --- mode handling (main thread) --------------------------------------------------------

    private fun applyMode(cfg: AppConfig) {
        val wantContinuous = cfg.voiceMode == VoiceMode.Continuous
        if (wantContinuous == continuous) return
        continuous = wantContinuous
        setBeepsMuted(wantContinuous)
        if (wantContinuous) {
            resumePassive(0)
        } else if (passiveSession) {
            recognizer?.cancel()
            passiveSession = false
            _state.value = VoiceUi()
        }
    }

    /** The recogniser beeps on every (re)start, which is unbearable in continuous mode. */
    private fun setBeepsMuted(mute: Boolean) {
        if (mute == muted) return
        muted = mute
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
    }

    private fun resumePassive(delayMs: Long) {
        if (!continuous) return
        main.postDelayed({
            if (continuous && _state.value.phase == VoicePhase.Idle && !passiveSession) listen(passive = true)
        }, delayMs)
    }

    private fun startOnMain() {
        val ui = _state.value
        if (!ui.passive && (ui.phase == VoicePhase.Listening || ui.phase == VoicePhase.Thinking)) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            fail("Nincs beszédfelismerő szolgáltatás a készüléken")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Nincs mikrofon engedély")
            return
        }
        if (passiveSession) recognizer?.cancel() // hand over from passive to an explicit command
        passiveSession = false
        listen(passive = false)
    }

    private fun listen(passive: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        main.removeCallbacks(clearRunnable)
        passiveSession = passive
        val rec = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.voiceLanguage)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, !passive)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        _state.value = VoiceUi(VoicePhase.Listening, passive = passive)
        if (!passive) _activity.tryEmit(Unit)
        rec.startListening(intent)
        main.removeCallbacks(listenTimeout)
        if (!passive) main.postDelayed(listenTimeout, LISTEN_TIMEOUT_MS)
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            if (passiveSession) return
            val text = partialResults.firstResult()
            if (text.isNotBlank()) _state.value = VoiceUi(VoicePhase.Listening, heard = text)
        }

        override fun onResults(results: Bundle?) {
            main.removeCallbacks(listenTimeout)
            errorStreak = 0
            val text = results.firstResult()
            if (passiveSession) {
                passiveSession = false
                onPassiveResult(text)
            } else if (text.isBlank()) {
                finish(VoiceUi())
            } else {
                process(text)
            }
        }

        override fun onError(error: Int) {
            main.removeCallbacks(listenTimeout)
            val wasPassive = passiveSession
            passiveSession = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    errorStreak = 0
                    finish(VoiceUi())
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.cancel()
                    finish(VoiceUi(), delayMs = RESTART_MS * 3)
                }
                else -> {
                    Log.i(TAG, "recogniser error=$error passive=$wasPassive")
                    errorStreak++
                    if (wasPassive) {
                        // Stay quiet in the background; back off so we don't hammer the service.
                        finish(VoiceUi(), delayMs = min(30_000L, 1_000L shl min(errorStreak, 5)))
                    } else {
                        fail(errorText(error))
                    }
                }
            }
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /** Continuous mode: act only on utterances that contain one of the wake phrases. */
    private fun onPassiveResult(text: String) {
        val match = if (armed) 0 to 0 else WakePhrase.find(text, config.voiceWakePhrase)
        if (match != null) Log.i(TAG, "wake phrase matched in: \"$text\"")
        if (armed || match != null) {
            val command = if (armed) text else text.substring(match!!.second)
            val cleaned = command.trim(' ', ',', '.', '!', '?', ':')
            armed = false
            _activity.tryEmit(Unit)
            if (cleaned.isNotBlank()) {
                process(cleaned)
            } else {
                // Only the wake phrase: the next utterance is the command.
                armed = true
                listen(passive = false)
            }
        } else {
            Log.i(TAG, "passive heard (no wake phrase): \"$text\"")
            if (text.isNotBlank()) {
                _lastHeard.value = text
                main.removeCallbacks(clearHeard)
                main.postDelayed(clearHeard, HEARD_VISIBLE_MS)
            }
            finish(VoiceUi())
        }
    }

    private fun process(text: String) {
        _activity.tryEmit(Unit)
        armed = false
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

    private fun fail(message: String, heard: String = "") =
        finish(VoiceUi(VoicePhase.Error, heard, message), ERROR_VISIBLE_MS)

    /** Shows [ui] for [delayMs] (if it carries something to read), then returns to Idle and resumes passive listening. */
    private fun finish(ui: VoiceUi, delayMs: Long = 0) {
        main.removeCallbacks(clearRunnable)
        _state.value = ui
        if (ui.phase == VoicePhase.Idle) resumePassive(delayMs.coerceAtLeast(RESTART_MS)) else main.postDelayed(clearRunnable, delayMs)
    }

    private fun errorText(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Nincs mikrofon engedély"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Nincs hálózat a beszédfelismeréshez"
        else -> "Beszédfelismerési hiba ($error)"
    }

    private fun Bundle?.firstResult(): String =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private companion object {
        const val TAG = "Voice"
        const val LISTEN_TIMEOUT_MS = 12_000L
        const val REPLY_VISIBLE_MS = 6_000L
        const val ERROR_VISIBLE_MS = 5_000L
        const val RESTART_MS = 300L
        const val HEARD_VISIBLE_MS = 6_000L
    }
}

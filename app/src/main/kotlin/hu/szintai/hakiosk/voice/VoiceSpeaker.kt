package hu.szintai.hakiosk.voice

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads replies aloud with the Google text-to-speech engine (Android `TextToSpeech`), falling
 * back to the device default engine. Callbacks arrive on TTS threads; callers re-post to main.
 */
@Singleton
class VoiceSpeaker @Inject constructor(@ApplicationContext private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var queued: (() -> Unit)? = null
    private var usingFallback = false

    fun speak(text: String, languageTag: String, onDone: () -> Unit, onError: (String) -> Unit) {
        val job = { speakNow(text, languageTag, onDone, onError) }
        if (ready) job() else {
            queued = job
            if (tts == null) init(GOOGLE_TTS, onError)
        }
    }

    fun stop() {
        tts?.stop()
    }

    private fun init(engine: String?, onError: (String) -> Unit) {
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                queued?.invoke()
                queued = null
            } else if (engine != null && !usingFallback) {
                Log.i(TAG, "Google TTS unavailable (status=$status), using the default engine")
                usingFallback = true
                tts?.shutdown()
                init(null, onError)
            } else {
                queued = null
                onError("Nem érhető el beszédszintetizátor")
            }
        }, engine)
    }

    private fun speakNow(text: String, languageTag: String, onDone: () -> Unit, onError: (String) -> Unit) {
        val engine = tts ?: return onError("Nem érhető el beszédszintetizátor")
        val locale = Locale.forLanguageTag(languageTag)
        when (engine.setLanguage(locale)) {
            TextToSpeech.LANG_MISSING_DATA, TextToSpeech.LANG_NOT_SUPPORTED -> {
                Log.i(TAG, "no TTS voice for $languageTag")
                return onError("Nincs telepített hang ($languageTag): Beállítások → Nyelv → Beszédkimenet")
            }
        }
        engine.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = onDone()

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = onError("Hiba a beszédkimenetben")
        })
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply") == TextToSpeech.ERROR) {
            onError("Hiba a beszédkimenetben")
        }
    }

    private companion object {
        const val TAG = "VoiceSpeaker"
        const val GOOGLE_TTS = "com.google.android.tts"
    }
}

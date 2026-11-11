package hu.szintai.hakiosk.config

/**
 * Central, typed application configuration. Everything tunable lives here so the
 * settings screen (and later a remote source) has a single model to read and write.
 */
data class AppConfig(
    val haUrl: String = DEFAULT_HA_URL,
    val haToken: String = "",
    val dashboardPath: String = DEFAULT_DASHBOARD_PATH,
    /** Camera based presence detection (dim when idle, wake on motion). */
    val presenceEnabled: Boolean = true,
    /** Seconds without motion/touch before the display is dimmed. */
    val idleTimeoutSeconds: Int = 60,
    /** Share of the camera grid that has to change to count as motion (percent). */
    val motionMinAreaPercent: Int = 4,
    /** Window brightness while dimmed (percent, 1..100). */
    val dimBrightnessPercent: Int = 1,
    /** Report wake events / presence to Home Assistant (mobile_app integration). */
    val reportToHa: Boolean = true,
    /** When the tablet listens for a voice command (the mic button always works unless [VoiceMode.Off]). */
    val voiceMode: VoiceMode = VoiceMode.Off,
    /** Continuous mode only reacts to commands containing one of these phrases (comma separated), e.g. "hey jarvis, hé jarvis". */
    val voiceWakePhrase: String = "hey jarvis, hé jarvis, hej jarvis",
    /** Read the answer aloud (Android text-to-speech, Google engine) after a voice command. */
    val voiceSpeak: Boolean = true,
    /** BCP-47 tag for speech recognition; the HA conversation language is its first part. */
    val voiceLanguage: String = "hu-HU",
    /** Serve the front camera over HTTP, only while Home Assistant (or a browser) is actually watching. */
    val cameraShare: Boolean = false,
    val cameraPort: Int = 8099,
    /** Shared secret in the stream URL (`?t=...`); generated on first enable. */
    val cameraToken: String = "",
) {
    val voiceEnabled: Boolean get() = voiceMode != VoiceMode.Off

    val isConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()

    /** Full URL of the dashboard the WebView should open. */
    val dashboardUrl: String
        get() = haUrl.trimEnd('/') + "/" + dashboardPath.trimStart('/')

    companion object {
        const val DEFAULT_HA_URL = "http://192.168.0.11:8123"
        const val DEFAULT_DASHBOARD_PATH = "dashboard-kiosk/home"
    }
}

enum class VoiceMode(val key: String) {
    /** No voice control. */
    Off("off"),

    /** Listen once after the display wakes up from the dimmed state. */
    OnWake("on_wake"),

    /** Listen whenever motion is detected in front of the tablet (15 s cool-down). */
    OnMotion("on_motion"),

    /** Always listening; commands need the wake phrase. Audio streams to Google's recogniser. */
    Continuous("continuous");

    companion object {
        fun fromKey(key: String?): VoiceMode = entries.firstOrNull { it.key == key } ?: Off
    }
}

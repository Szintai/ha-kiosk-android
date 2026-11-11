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
) {
    val isConfigured: Boolean get() = haUrl.isNotBlank() && haToken.isNotBlank()

    /** Full URL of the dashboard the WebView should open. */
    val dashboardUrl: String
        get() = haUrl.trimEnd('/') + "/" + dashboardPath.trimStart('/')

    companion object {
        const val DEFAULT_HA_URL = "http://192.168.0.11:8123"
        const val DEFAULT_DASHBOARD_PATH = "dashboard-kiosk/home"
    }
}

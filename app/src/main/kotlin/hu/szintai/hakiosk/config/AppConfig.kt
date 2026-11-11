package hu.szintai.hakiosk.config

/**
 * Central, typed application configuration. Everything tunable lives here so the
 * settings screen (and later a remote source) has a single model to read and write.
 */
data class AppConfig(
    val haUrl: String = DEFAULT_HA_URL,
    val haToken: String = "",
    val dashboardPath: String = DEFAULT_DASHBOARD_PATH,
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

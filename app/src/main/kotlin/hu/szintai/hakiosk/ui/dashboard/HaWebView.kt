package hu.szintai.hakiosk.ui.dashboard

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import hu.szintai.hakiosk.BuildConfig
import hu.szintai.hakiosk.config.AppConfig

/**
 * Full-screen WebView showing the Home Assistant dashboard.
 *
 * Authentication: a long-lived access token is written to `localStorage.hassTokens`
 * *before* the page scripts run (document-start script), which is exactly what the
 * HA frontend reads on boot, so no manual login is needed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HaWebView(config: AppConfig, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()

                if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                    WebViewCompat.addDocumentStartJavaScript(
                        this,
                        KioskScripts.bootstrap(config.haToken),
                        setOf(originOf(config.haUrl)),
                    )
                }
                loadUrl(config.dashboardUrl)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun originOf(url: String): String {
    val uri = Uri.parse(url)
    val port = if (uri.port != -1) ":${uri.port}" else ""
    return "${uri.scheme}://${uri.host}$port"
}

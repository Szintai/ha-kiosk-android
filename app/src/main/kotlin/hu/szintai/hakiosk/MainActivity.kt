package hu.szintai.hakiosk

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.ui.navigation.AppNavHost
import hu.szintai.hakiosk.ui.theme.HaKioskTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var configRepository: ConfigRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        applyDebugConfig(intent)

        setContent {
            HaKioskTheme { AppNavHost() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugConfig(intent)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * Debug builds only: lets us provision the config over adb instead of typing a
     * long token on the tablet:
     * `adb shell am start -n hu.szintai.hakiosk/.MainActivity --es ha_url URL --es ha_token TOKEN`
     */
    private fun applyDebugConfig(intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        val url = intent.getStringExtra(EXTRA_HA_URL)
        val token = intent.getStringExtra(EXTRA_HA_TOKEN)
        val path = intent.getStringExtra(EXTRA_DASHBOARD_PATH)
        if (url == null && token == null && path == null) return
        lifecycleScope.launch {
            configRepository.update {
                it.copy(
                    haUrl = url ?: it.haUrl,
                    haToken = token ?: it.haToken,
                    dashboardPath = path ?: it.dashboardPath,
                )
            }
        }
    }

    private companion object {
        const val EXTRA_HA_URL = "ha_url"
        const val EXTRA_HA_TOKEN = "ha_token"
        const val EXTRA_DASHBOARD_PATH = "dashboard_path"
    }
}

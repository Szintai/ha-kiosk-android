package hu.szintai.hakiosk

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import hu.szintai.hakiosk.voice.VoicePhase
import hu.szintai.hakiosk.voice.VoiceUi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.presence.CameraMotionSource
import hu.szintai.hakiosk.presence.DisplayMode
import hu.szintai.hakiosk.presence.PresenceViewModel
import hu.szintai.hakiosk.ui.navigation.AppNavHost
import hu.szintai.hakiosk.ui.theme.HaKioskTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var configRepository: ConfigRepository

    private val presence: PresenceViewModel by viewModels()
    private val cameraSource by lazy { CameraMotionSource(applicationContext, presence::onFrame) }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* start() re-checks */ }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) cameraSource.start(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        applyDebugConfig(intent)

        setContent {
            HaKioskTheme {
                val mode by presence.mode.collectAsState()
                val voice by presence.voice.collectAsState()
                val settings by presence.config.collectAsState()
                Box(Modifier.fillMaxSize()) {
                    AppNavHost()
                    if (settings.voiceEnabled && mode == DisplayMode.Active) {
                        VoiceOverlay(voice, onMicClick = presence::startVoice)
                    }
                    if (mode == DisplayMode.Dim) DimOverlay()
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    presence.config.map { it.presenceEnabled }.distinctUntilChanged().collect { enabled ->
                        if (enabled) startCamera() else cameraSource.stop()
                    }
                }
                launch {
                    presence.mode.collect { applyBrightness(it) }
                }
                launch {
                    presence.config.map { it.voiceEnabled }.distinctUntilChanged().collect { enabled ->
                        if (enabled && !hasPermission(Manifest.permission.RECORD_AUDIO)) {
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) presence.onTouch()
        return super.dispatchTouchEvent(ev)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyDebugConfig(intent)
    }

    private fun hasPermission(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        if (hasPermission(Manifest.permission.CAMERA)) {
            cameraSource.start(this)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun applyBrightness(mode: DisplayMode) {
        val attrs = window.attributes
        attrs.screenBrightness = if (mode == DisplayMode.Dim) {
            presence.config.value.dimBrightnessPercent.coerceIn(1, 100) / 100f
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        window.attributes = attrs
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
        val idle = if (intent.hasExtra(EXTRA_IDLE_TIMEOUT)) intent.getIntExtra(EXTRA_IDLE_TIMEOUT, 60) else null
        val voice = if (intent.hasExtra(EXTRA_VOICE)) intent.getBooleanExtra(EXTRA_VOICE, false) else null
        if (url == null && token == null && path == null && idle == null && voice == null) return
        lifecycleScope.launch {
            configRepository.update {
                it.copy(
                    haUrl = url ?: it.haUrl,
                    haToken = token ?: it.haToken,
                    dashboardPath = path ?: it.dashboardPath,
                    idleTimeoutSeconds = idle ?: it.idleTimeoutSeconds,
                    voiceEnabled = voice ?: it.voiceEnabled,
                )
            }
        }
    }

    private companion object {
        const val EXTRA_HA_URL = "ha_url"
        const val EXTRA_HA_TOKEN = "ha_token"
        const val EXTRA_DASHBOARD_PATH = "dashboard_path"
        const val EXTRA_IDLE_TIMEOUT = "idle_timeout"
        const val EXTRA_VOICE = "voice_enabled"
    }
}

/** Mic button (bottom-left) plus a status pill showing what was heard and Home Assistant's reply. */
@androidx.compose.runtime.Composable
private fun VoiceOverlay(ui: VoiceUi, onMicClick: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(if (ui.phase == VoicePhase.Listening) Color(0xFFFF9F0A) else Color(0x55000000))
                .clickable(onClick = onMicClick),
            contentAlignment = Alignment.Center,
        ) { Text("\uD83C\uDFA4") }

        if (ui.phase != VoicePhase.Idle) {
            val line = when (ui.phase) {
                VoicePhase.Listening -> ui.heard.ifBlank { "Hallgatlak…" }
                VoicePhase.Thinking -> "\u201E${ui.heard}\u201D …"
                VoicePhase.Done -> "\u201E${ui.heard}\u201D \u2192 ${ui.message}"
                VoicePhase.Error -> ui.message
                VoicePhase.Idle -> ""
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xE61C1C1E))
                    .padding(horizontal = 22.dp, vertical = 14.dp),
            ) { Text(line, color = Color.White) }
        }
    }
}

/** Black cover shown while dimmed; swallows taps (the touch itself wakes the display). */
@androidx.compose.runtime.Composable
private fun DimOverlay() {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { } },
    )
}

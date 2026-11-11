package hu.szintai.hakiosk.presence

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hu.szintai.hakiosk.config.AppConfig
import hu.szintai.hakiosk.config.ConfigRepository
import hu.szintai.hakiosk.ha.HaReporter
import hu.szintai.hakiosk.ha.PresenceSnapshot
import hu.szintai.hakiosk.ha.WakeStatsStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Glue between the camera frames, the [PresenceController] state machine, the persisted wake
 * counter and the Home Assistant reporter. Camera callbacks arrive on the analysis thread, so
 * every state change goes through [lock].
 */
@HiltViewModel
class PresenceViewModel @Inject constructor(
    private val configRepository: ConfigRepository,
    private val stats: WakeStatsStore,
    private val reporter: HaReporter,
) : ViewModel() {

    private val lock = Any()
    private val detector = MotionDetector()
    private val trigger = MotionTrigger()
    private val controller = PresenceController(idleTimeoutMs = AppConfig().idleTimeoutSeconds * 1000L, startAtMs = now())

    @Volatile private var settings = AppConfig()
    @Volatile private var wakeCount = 0L

    private val _mode = MutableStateFlow(DisplayMode.Active)
    val mode: StateFlow<DisplayMode> = _mode.asStateFlow()

    private val _config = MutableStateFlow(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    init {
        viewModelScope.launch {
            wakeCount = stats.count()
            configRepository.config.collect { applyConfig(it) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(TICK_MS)
                handle(synchronized(lock) { controller.tick(now()) })
            }
        }
    }

    /** Called from the camera analysis thread with a luminance grid. */
    fun onFrame(grid: IntArray) {
        val s = settings
        if (!s.presenceEnabled) return
        val score = detector.score(grid) ?: return
        if (trigger.onFrame(score >= s.motionMinAreaPercent / 100f)) {
            handle(synchronized(lock) { controller.onMotion(now()) })
        }
    }

    /** Any touch counts as activity; a touch on the dim overlay wakes the display. */
    fun onTouch() {
        handle(synchronized(lock) { controller.onTouch(now()) })
    }

    private fun applyConfig(config: AppConfig) {
        settings = config
        _config.value = config
        synchronized(lock) {
            controller.idleTimeoutMs = config.idleTimeoutSeconds.coerceAtLeast(5) * 1000L
            if (!config.presenceEnabled) {
                controller.forceActive(now())
                _mode.value = DisplayMode.Active
                detector.reset()
                trigger.reset()
            }
        }
        publish(present = _mode.value == DisplayMode.Active)
    }

    private fun handle(event: PresenceEvent?) {
        when (event) {
            null -> return
            is PresenceEvent.Woke -> {
                _mode.value = DisplayMode.Active
                if (event.byMotion) {
                    viewModelScope.launch {
                        wakeCount = stats.increment()
                        publish(present = true)
                    }
                } else {
                    publish(present = true)
                }
            }
            is PresenceEvent.Dimmed -> {
                _mode.value = DisplayMode.Dim
                publish(present = false)
            }
        }
    }

    private fun publish(present: Boolean) {
        if (settings.presenceEnabled) reporter.update(PresenceSnapshot(wakeCount, present))
    }

    private fun now() = SystemClock.elapsedRealtime()

    private companion object {
        const val TICK_MS = 1_000L
    }
}

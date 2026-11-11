package hu.szintai.hakiosk.presence

enum class DisplayMode { Active, Dim }

sealed interface PresenceEvent {
    /** The display left [DisplayMode.Dim]. [byMotion] is false for a touch wake-up. */
    data class Woke(val atMs: Long, val byMotion: Boolean) : PresenceEvent

    data class Dimmed(val atMs: Long) : PresenceEvent
}

/**
 * Display state machine: Active <-> Dim. Time is passed in explicitly (monotonic ms) so the
 * logic is deterministic and unit-testable. Not thread-safe; callers synchronise.
 */
class PresenceController(
    var idleTimeoutMs: Long,
    startAtMs: Long = 0,
) {
    var mode: DisplayMode = DisplayMode.Active
        private set

    private var lastActivityMs = startAtMs

    fun onMotion(nowMs: Long): PresenceEvent? = activity(nowMs, byMotion = true)

    fun onTouch(nowMs: Long): PresenceEvent? = activity(nowMs, byMotion = false)

    fun tick(nowMs: Long): PresenceEvent? {
        if (mode == DisplayMode.Active && nowMs - lastActivityMs >= idleTimeoutMs) {
            mode = DisplayMode.Dim
            return PresenceEvent.Dimmed(nowMs)
        }
        return null
    }

    /** Back to Active without producing a wake event (e.g. presence detection switched off). */
    fun forceActive(nowMs: Long) {
        mode = DisplayMode.Active
        lastActivityMs = nowMs
    }

    private fun activity(nowMs: Long, byMotion: Boolean): PresenceEvent? {
        lastActivityMs = nowMs
        if (mode == DisplayMode.Dim) {
            mode = DisplayMode.Active
            return PresenceEvent.Woke(nowMs, byMotion)
        }
        return null
    }
}

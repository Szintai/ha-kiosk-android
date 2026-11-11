package hu.szintai.hakiosk.ha

/** What the tablet tells Home Assistant about itself. */
data class PresenceSnapshot(
    /** Total number of motion wake-ups since install (monotonic, `total_increasing`). */
    val wakeCount: Long,
    /** True while the display is active (someone is around), false while dimmed. */
    val present: Boolean,
)

interface HaReporter {
    /** Fire and forget; the implementation retries and only keeps the latest values. */
    fun update(snapshot: PresenceSnapshot)

    fun updateDevice(status: DeviceStatus)
}

package hu.szintai.hakiosk.presence

import kotlin.math.abs

/**
 * Frame-difference motion detector working on a tiny luminance grid
 * (e.g. 32x24 cells, 0..255 per cell). Pure Kotlin, no Android dependencies.
 *
 * Global brightness shifts (camera auto exposure, the screen dimming) are compensated
 * by subtracting the median change before thresholding, so only *local* change counts.
 */
class MotionDetector(
    private val cellThreshold: Int = 14,
    private val warmupFrames: Int = 3,
) {
    private var previous: IntArray? = null
    private var seen = 0

    /** Fraction (0..1) of cells that changed, or `null` while warming up / on size change. */
    fun score(grid: IntArray): Float? {
        val prev = previous
        previous = grid.copyOf()
        if (prev == null || prev.size != grid.size) {
            seen = 1
            return null
        }
        if (seen < warmupFrames) {
            seen++
            return null
        }
        val diff = IntArray(grid.size) { grid[it] - prev[it] }
        // Median, not mean: a large moving object must not drag the "global" shift with it.
        val shift = diff.sortedArray()[diff.size / 2]

        var changed = 0
        for (d in diff) {
            if (abs(d - shift) > cellThreshold) changed++
        }
        return changed.toFloat() / grid.size
    }

    fun reset() {
        previous = null
        seen = 0
    }
}

/** Requires [minConsecutive] positive frames in a row so single noisy frames don't wake the display. */
class MotionTrigger(private val minConsecutive: Int = 2) {
    private var streak = 0

    fun onFrame(motion: Boolean): Boolean {
        streak = if (motion) streak + 1 else 0
        return streak >= minConsecutive
    }

    fun reset() {
        streak = 0
    }
}

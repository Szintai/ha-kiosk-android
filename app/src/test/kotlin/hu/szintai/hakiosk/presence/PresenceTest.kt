package hu.szintai.hakiosk.presence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionDetectorTest {
    private fun frame(value: Int, size: Int = 100) = IntArray(size) { value }

    @Test
    fun `warms up before scoring`() {
        val d = MotionDetector(warmupFrames = 3)
        assertNull(d.score(frame(100)))
        assertNull(d.score(frame(100)))
        assertNull(d.score(frame(100)))
        assertNotNull(d.score(frame(100)))
    }

    @Test
    fun `static scene scores zero`() {
        val d = MotionDetector(warmupFrames = 1)
        d.score(frame(100))
        assertEquals(0f, d.score(frame(100))!!, 0f)
    }

    @Test
    fun `local change is detected`() {
        val d = MotionDetector(warmupFrames = 1)
        d.score(frame(100))
        val moved = frame(100).also { for (i in 0 until 20) it[i] = 220 }
        assertEquals(0.2f, d.score(moved)!!, 0.05f)
    }

    @Test
    fun `global brightness shift is ignored`() {
        val d = MotionDetector(warmupFrames = 1)
        d.score(frame(100))
        assertEquals(0f, d.score(frame(140))!!, 0f)
    }

    @Test
    fun `trigger needs consecutive frames`() {
        val t = MotionTrigger(minConsecutive = 2)
        assertFalse(t.onFrame(true))
        assertFalse(t.onFrame(false))
        assertFalse(t.onFrame(true))
        assertTrue(t.onFrame(true))
    }
}

class PresenceControllerTest {
    @Test
    fun `dims after idle timeout`() {
        val c = PresenceController(idleTimeoutMs = 60_000, startAtMs = 0)
        assertNull(c.tick(59_999))
        assertEquals(PresenceEvent.Dimmed(60_000), c.tick(60_000))
        assertEquals(DisplayMode.Dim, c.mode)
    }

    @Test
    fun `motion keeps display active`() {
        val c = PresenceController(60_000, 0)
        assertNull(c.onMotion(50_000))
        assertNull(c.tick(100_000))
        assertNotNull(c.tick(110_000))
    }

    @Test
    fun `motion wakes a dimmed display exactly once`() {
        val c = PresenceController(60_000, 0)
        c.tick(60_000)
        assertEquals(PresenceEvent.Woke(61_000, byMotion = true), c.onMotion(61_000))
        assertNull(c.onMotion(62_000))
        assertEquals(DisplayMode.Active, c.mode)
    }

    @Test
    fun `touch wake is not a motion wake`() {
        val c = PresenceController(60_000, 0)
        c.tick(60_000)
        assertEquals(PresenceEvent.Woke(70_000, byMotion = false), c.onTouch(70_000))
    }
}

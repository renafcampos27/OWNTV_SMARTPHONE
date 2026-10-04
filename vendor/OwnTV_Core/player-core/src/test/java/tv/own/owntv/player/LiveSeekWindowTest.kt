package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class LiveSeekWindowTest {
    @Test fun `unknown or invalid timelines cannot be scrubbed`() {
        assertNull(LiveSeekWindow.snapshot(0, -1, 0, 0))
        assertNull(LiveSeekWindow.snapshot(0, 120_000, -1, 0))
        assertNull(LiveSeekWindow.snapshot(0, 120_000, 130_000, 0))
    }

    @Test fun `rewind remains inside published window and forward returns to safe live default`() {
        val window = LiveSeekWindow.snapshot(110_000, 120_000, 110_000, 120_000)!!
        assertEquals(80_000L, window.targetAfter(-30_000))
        assertEquals(0L, window.targetAfter(-300_000))
        assertNull(window.targetAfter(30_000))
    }

    @Test fun `forward within rewind does not retune or jump early to live`() {
        val window = LiveSeekWindow.snapshot(30_000, 120_000, 110_000, 70_000)!!
        assertEquals(60_000L, window.targetAfter(30_000))
        assertEquals(80_000L, window.behindDefaultMs)
        assertNull(window.targetAfter(80_000))
    }

    @Test fun `rolling update uses latest bounds and clamps stale position and buffer`() {
        val window = LiveSeekWindow.snapshot(120_000, 90_000, 80_000, 140_000)!!
        assertEquals(90_000L, window.positionMs)
        assertEquals(90_000L, window.bufferedMs)
        assertEquals(60_000L, window.targetAfter(-30_000))
        assertEquals(0L, window.behindDefaultMs)
    }
}

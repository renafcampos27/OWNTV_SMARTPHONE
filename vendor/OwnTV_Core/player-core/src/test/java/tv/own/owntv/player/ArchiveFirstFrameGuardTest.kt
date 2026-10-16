package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFirstFrameGuardTest {
    @Test fun `first decoded frame without movement has finite timeout`() {
        val guard = ArchiveFirstFrameGuard()
        assertFalse(guard.stalled(0, 100, true, true, false, false))
        assertFalse(guard.stalled(7_999, 100, true, true, false, false))
        assertTrue(guard.stalled(8_000, 100, true, true, false, false))
    }

    @Test fun `pause and buffering earn no frozen playback time`() {
        val guard = ArchiveFirstFrameGuard()
        guard.stalled(0, 100, true, true, false, false)
        assertFalse(guard.stalled(7_000, 100, false, false, false, false))
        assertFalse(guard.stalled(20_000, 100, true, true, false, false))
        assertFalse(guard.stalled(27_000, 100, true, true, true, false))
        assertFalse(guard.stalled(40_000, 100, true, true, false, false))
        assertTrue(guard.stalled(48_000, 100, true, true, false, false))
    }

    @Test fun `advancing media and audio only exempt the video recovery`() {
        val guard = ArchiveFirstFrameGuard()
        guard.stalled(0, 100, true, true, false, false)
        assertFalse(guard.stalled(3_000, 2_101, true, true, false, false))
        assertTrue(guard.confirmed)
        val audio = ArchiveFirstFrameGuard()
        assertFalse(audio.stalled(20_000, 0, true, true, false, true))
        assertTrue(audio.confirmed)
    }
}

package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class VideoFrameHealthTest {
    @Test fun percentIncludesRenderedAndDroppedFrames() {
        assertEquals(25.0, VideoFrameWindow(300, 100, 30_000).percent, .001)
        assertEquals(0.0, VideoFrameWindow(0, 0, 30_000).percent, 0.0)
    }
    @Test fun initialBurstDoesNotWarnAndHealthyPlaybackStaysHealthy() {
        val health = VideoFrameHealth()
        repeat(61) { i -> assertFalse(health.poll(i * 500L, i * 30L, 200, true)) }
        assertFalse(health.severe)
    }
    @Test fun warningIsBoundedToOneUntilNewTune() {
        val health = VideoFrameHealth()
        var warnings = 0
        repeat(150) { i -> if (health.poll(i * 500L, i * 20L, i * 10L, true)) warnings++ }
        assertEquals(1, warnings)
        assertTrue(health.latest!!.elapsedMs <= 30_500)
        health.resetTune()
        repeat(70) { i -> if (health.poll(i * 500L, i * 20L, i * 10L, true)) warnings++ }
        assertEquals(2, warnings)
    }
    @Test fun severeRequiresTenSecondsAndMoreDropsThanRendered() {
        val health = VideoFrameHealth()
        repeat(20) { i -> health.poll(i * 500L, i.toLong(), i * 20L, true); assertFalse(health.severe) }
        health.poll(10_000, 20, 400, true)
        assertTrue(health.severe)
    }
    @Test fun bufferPauseGapDecoderResetAndCounterRollbackCannotBridgeWindows() {
        val health = VideoFrameHealth()
        repeat(20) { health.poll(it * 500L, it.toLong(), it * 20L, true) }
        health.poll(10_000, 20, 400, false)
        assertNull(health.latest)
        health.poll(20_000, 40, 800, true)
        assertFalse(health.severe)
        health.poll(20_500, 0, 0, true)
        assertEquals(0, health.latest!!.elapsedMs)
        health.resetWindow()
        assertNull(health.latest)
    }
    @Test fun recoveryIsOffByDefaultBoundedAndNeedsPersistentEligibleEvidence() {
        val recovery = VideoDegradationRecovery()
        assertFalse(recovery.acquire(0, false, true, true, 5000, true))
        assertFalse(recovery.acquire(0, true, true, true, 5000, true))
        assertTrue(recovery.acquire(2000, true, true, true, 5000, true))
        assertFalse(recovery.acquire(50_000, true, true, true, 5000, true))
        recovery.reset()
        assertFalse(recovery.acquire(60_000, true, true, true, 5000, true))
        assertTrue(recovery.acquire(62_000, true, true, true, 5000, true))
    }
    @Test fun insufficientBufferMissingSurfaceOrPauseResetsSevereDwell() {
        for (bad in listOf(Triple(false, 5000L, true), Triple(true, 1000L, true), Triple(true, 5000L, false))) {
            val recovery = VideoDegradationRecovery()
            assertFalse(recovery.acquire(0, true, true, true, 5000, true))
            assertFalse(recovery.acquire(2000, true, true, bad.first, bad.second, bad.third))
            assertFalse(recovery.acquire(3000, true, true, true, 5000, true))
            assertTrue(recovery.acquire(5000, true, true, true, 5000, true))
        }
    }
}

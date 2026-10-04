package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RebufferFlapDetectorTest {
    @Test
    fun `short immediate recovery burst does not trigger a reconnect`() {
        val detector = RebufferFlapDetector()
        repeat(20) { assertNull(detector.buffering(it * 50L, it * 25L)) }
        assertNull(detector.buffering(6_000L, 4_000L))
    }

    @Test
    fun `sustained oscillation without useful playback is detected after the full window`() {
        val detector = RebufferFlapDetector()
        repeat(12) { assertNull(detector.buffering(it * 500L, it * 50L)) }
        val evidence = detector.buffering(6_000L, 600L)
        assertNotNull(evidence)
        assertEquals(13, evidence!!.transitions)
        assertEquals(6_000L, evidence.elapsedMs)
    }

    @Test
    fun `occasional interruptions do not count as a sustained flap`() {
        val detector = RebufferFlapDetector()
        repeat(10) { assertNull(detector.buffering(it * 3_000L, it * 100L)) }
    }

    @Test
    fun `useful playback clears an earlier burst`() {
        val detector = RebufferFlapDetector()
        repeat(12) { assertNull(detector.buffering(it * 100L, 0L)) }
        assertNull(detector.buffering(6_000L, 3_000L))
        assertNull(detector.buffering(6_500L, 3_001L))
    }

    @Test
    fun `new source clears the previous sources evidence`() {
        val detector = RebufferFlapDetector()
        repeat(12) { assertNull(detector.buffering(it * 500L, 0L)) }
        detector.reset()
        assertNull(detector.buffering(6_000L, 0L))
    }

    @Test
    fun `backwards timestamp starts a fresh window`() {
        val detector = RebufferFlapDetector()
        repeat(12) { assertNull(detector.buffering(it * 500L, 10_000L)) }
        assertNull(detector.buffering(6_000L, 100L))
    }
}

package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.player.PlaybackEngines.Pressure

class MemoryPressureEpisodeTest {
    @Test fun lowEscalatesOnceAndRearamsOnlyAfterRecovery() {
        val episode = MemoryPressureEpisode()
        assertEquals(Pressure.LOW, episode.observe(Pressure.LOW))
        assertNull(episode.observe(Pressure.LOW))
        assertEquals(Pressure.CRITICAL, episode.observe(Pressure.CRITICAL))
        assertNull(episode.observe(Pressure.LOW))
        assertNull(episode.observe(Pressure.CRITICAL))
        assertNull(episode.observe(Pressure.NONE))
        assertEquals(Pressure.LOW, episode.observe(Pressure.LOW))
    }
    @Test fun sampledPressureHandlesInvalidThresholdAndOverflowSafely() {
        assertEquals(Pressure.CRITICAL, PlaybackEngines.pressureOf(true, Long.MAX_VALUE, 0))
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(false, 100, 0))
        assertEquals(Pressure.CRITICAL, PlaybackEngines.pressureOf(false, 50, 100))
        assertEquals(Pressure.LOW, PlaybackEngines.pressureOf(false, 200, 100))
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(false, 201, 100))
        assertEquals(Pressure.LOW, PlaybackEngines.pressureOf(false, Long.MAX_VALUE, Long.MAX_VALUE / 2 + 1))
    }
    @Test fun hidingTheUiIsNotMemoryPressure() {
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(20))
        assertEquals(Pressure.NONE, PlaybackEngines.pressureOf(40))
    }
}

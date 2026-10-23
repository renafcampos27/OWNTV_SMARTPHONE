package tv.own.owntv.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.player.PlaybackEngines.Pressure

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryPressureMonitoringTest {
    @Test fun timerIsAbsentWithoutRelevantPlaybackAndStopsOnPause() = runTest {
        val relevant = MutableStateFlow(false)
        var samples = 0
        val monitor = launch { monitorMemoryPressure(relevant, { samples++; Pressure.NONE }, {}) }
        runCurrent(); advanceTimeBy(60_000); runCurrent()
        assertEquals(0, samples)
        relevant.value = true; runCurrent()
        assertEquals(1, samples)
        advanceTimeBy(5_000); runCurrent()
        assertEquals(2, samples)
        relevant.value = false; runCurrent()
        advanceTimeBy(60_000); runCurrent()
        assertEquals(2, samples)
        monitor.cancelAndJoin()
    }
    @Test fun restartingPlaybackDoesNotRetrimTheSamePressureEpisode() = runTest {
        val relevant = MutableStateFlow(true)
        var pressure = Pressure.LOW
        val applied = mutableListOf<Pressure>()
        val monitor = launch { monitorMemoryPressure(relevant, { pressure }, applied::add) }
        runCurrent(); advanceTimeBy(10_000); runCurrent()
        assertEquals(listOf(Pressure.LOW), applied)
        relevant.value = false; runCurrent(); relevant.value = true; runCurrent()
        assertEquals(listOf(Pressure.LOW), applied)
        pressure = Pressure.NONE; advanceTimeBy(5_000); runCurrent()
        pressure = Pressure.CRITICAL; advanceTimeBy(5_000); runCurrent()
        assertEquals(listOf(Pressure.LOW, Pressure.CRITICAL), applied)
        monitor.cancelAndJoin()
    }
}

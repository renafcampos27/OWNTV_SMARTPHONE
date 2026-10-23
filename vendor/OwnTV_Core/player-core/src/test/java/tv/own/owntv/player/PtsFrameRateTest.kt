package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PtsFrameRateTest {
    private fun measure(fps: Double, frames: Int = 650): Float? {
        val sample = PtsFrameRate()
        repeat(frames) { sample.add((it * 1_000_000.0 / fps).toLong()) }
        return sample.rate()
    }
    @Test fun distinguishesFractionalAndIntegerRates() {
        for (fps in listOf(23.976023976, 24.0, 25.0, 29.97002997, 30.0, 59.94005994, 60.0, 119.88011988, 120.0))
            assertEquals(fps.toFloat(), measure(fps)!!, .0001f)
    }
    @Test fun requiresFourSecondsOfSteadyMediaTime() {
        assertNull(measure(60.0, 100))
        assertNotNull(measure(60.0, 300))
    }
    @Test fun discontinuityAndResetDiscardPreviousCadence() {
        val sample = PtsFrameRate()
        repeat(300) { sample.add(it * 20_000L) }
        assertEquals(50f, sample.rate()!!, 0f)
        sample.add(0)
        assertNull(sample.rate())
        repeat(301) { sample.add(it * 40_000L) }
        assertEquals(25f, sample.rate()!!, 0f)
        sample.reset()
        assertNull(sample.rate())
    }
    @Test fun repeatedTimestampDoesNotDestroySample() {
        val sample = PtsFrameRate()
        repeat(301) { sample.add(it * 20_000L); sample.add(it * 20_000L) }
        assertEquals(50f, sample.rate()!!, 0f)
    }
    @Test fun irregularCadenceAndBroadRoundingAreRejected() {
        assertNull(measure(57.0))
        assertNull(BroadcastFrameRate.snap(Double.NaN))
        assertNull(BroadcastFrameRate.snap(59.8))
    }
    @Test fun droppedFramesCannotHalveDeclaredRateButFractionalPrecisionIsPreserved() {
        assertEquals(60f, BroadcastFrameRate.preferMeasured(60f, 30f)!!, 0f)
        assertEquals(59.94006f, BroadcastFrameRate.preferMeasured(60f, 59.94006f)!!, 0f)
        assertEquals(50f, BroadcastFrameRate.preferMeasured(-1f, 50f)!!, 0f)
    }

}

package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class BufferUiSampleTest {
    @Test fun fractionalCacheChangesDoNotKeepPublishingToTheUi() {
        val sample = BufferUiSample()
        assertEquals(9000L, sample.sample(4000, 5.01))
        repeat(99) { assertNull(sample.sample(4000, 5.01 + it / 100.0)) }
        assertEquals(10000L, sample.sample(4000, 6.0))
    }

    @Test fun positionAndDrainingCacheRemainResponsive() {
        val sample = BufferUiSample()
        sample.sample(1000, 5.0)
        assertEquals(7000L, sample.sample(2000, 5.0))
        assertEquals(2000L, sample.sample(2000, 0.2))
    }

    @Test fun resetAndBackwardSeekCanPublishAnIdenticalAmountAgain() {
        val sample = BufferUiSample()
        sample.sample(10000, 4.0)
        assertEquals(4000L, sample.sample(0, 4.0))
        sample.reset()
        assertEquals(4000L, sample.sample(0, 4.0))
    }

    @Test fun invalidObservationsNeverReachTheUi() {
        val sample = BufferUiSample()
        for (value in listOf(-0.1, Double.NaN, Double.POSITIVE_INFINITY)) assertNull(sample.sample(0, value))
        assertEquals(0L, sample.sample(0, 0.0))
    }
}

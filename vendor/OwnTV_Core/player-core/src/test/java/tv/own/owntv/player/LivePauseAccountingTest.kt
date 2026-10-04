package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LivePauseAccountingTest {
    @Test fun `pauses exclude only elapsed paused time without resetting active budget`() {
        val b = LiveWaitBudget(0L, 1000L)
        assertEquals(600L, b.remaining(400L, null, null, false))
        assertEquals(600L, b.remaining(30_400L, null, null, true))
        assertEquals(400L, b.remaining(30_600L, null, null, false))
        assertEquals(400L, b.remaining(60_600L, null, null, true))
        assertEquals(0L, b.remaining(61_000L, null, null, true))
    }
    @Test fun `provider credit remains bounded and wait identity is unchanged through pause`() {
        val b = LiveWaitBudget(0L, 1000L)
        val wait = LiveProviderWait(7L, 2000L)
        assertEquals(2900L, b.remaining(100L, null, wait, false))
        assertEquals(2900L, b.remaining(10_100L, null, wait, true))
        assertEquals(1900L, b.remaining(11_100L, null, wait, true))
        assertEquals(0L, b.remaining(13_100L, null, wait, true))
    }
}

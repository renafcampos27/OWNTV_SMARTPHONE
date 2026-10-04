package tv.own.owntv.core.timeshift

import org.junit.Assert.*
import org.junit.Test

class LocalTimeshiftRecoveryTest {
    @Test fun silenceBudgetFollowsLongSegmentsWithFiniteBounds() {
        assertEquals(45_000L, LocalTimeshiftRecovery.noProgressMs(6.0))
        assertEquals(210_000L, LocalTimeshiftRecovery.noProgressMs(60.0))
        assertEquals(300_000L, LocalTimeshiftRecovery.noProgressMs(1e20))
        assertEquals(45_000L, LocalTimeshiftRecovery.noProgressMs(Double.NaN))
        assertEquals(45_000L, LocalTimeshiftRecovery.noProgressMs(-1.0))
    }
}

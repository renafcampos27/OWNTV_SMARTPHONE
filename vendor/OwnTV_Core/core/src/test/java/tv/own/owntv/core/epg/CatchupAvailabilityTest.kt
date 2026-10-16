package tv.own.owntv.core.epg

import org.junit.Assert.*
import org.junit.Test

class CatchupAvailabilityTest {
    @Test fun coverageDistinguishesMetadataFromConfirmedAvailability() {
        val now = 200L * 86_400_000
        assertEquals(CatchupAvailability.State.NO_ARCHIVE, CatchupAvailability.state(false, 7, now - 1000, now))
        assertEquals(CatchupAvailability.State.FUTURE, CatchupAvailability.state(true, 7, now + 1000, now))
        assertEquals(CatchupAvailability.State.UNKNOWN_WINDOW, CatchupAvailability.state(true, 0, now - 1000, now))
        assertEquals(CatchupAvailability.State.DECLARED, CatchupAvailability.state(true, 7, now - 5L * 86_400_000, now))
        assertEquals(CatchupAvailability.State.OUTSIDE_DECLARED, CatchupAvailability.state(true, 1, now - 5L * 86_400_000, now))
        assertTrue(GuideHistoryPolicy.canAttemptCatchup(true, now - 5L * 86_400_000, now))
        assertEquals(CatchupAvailability.State.OUTSIDE_LIMIT, CatchupAvailability.state(true, 7, now - GuideHistoryPolicy.MAX_LOOKBACK_MS - 1, now))
    }
}

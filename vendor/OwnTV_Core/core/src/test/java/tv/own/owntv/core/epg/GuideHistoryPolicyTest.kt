package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideHistoryPolicyTest {
    private val dayMs = 86_400_000L
    private val now = 30L * dayMs

    @Test fun announcedThirtyDayArchiveIsNotClippedToAWeek() {
        assertEquals(now - 30 * dayMs, GuideHistoryPolicy.windowStart(now, 30))
        assertEquals(now - 31 * dayMs, GuideHistoryPolicy.windowStart(now, Int.MAX_VALUE))
    }

    @Test fun explicitAttemptCanRequestFiveDaysWhenMetadataSaysOneWithoutClaimingAvailability() {
        assertEquals(now - 7 * dayMs, GuideHistoryPolicy.windowStart(now, 1))
        assertTrue(now - 5 * dayMs >= GuideHistoryPolicy.windowStart(now, 1))
        assertTrue(GuideHistoryPolicy.canAttemptCatchup(true, now - 5 * dayMs, now))
        assertFalse(GuideHistoryPolicy.canCatchup(true, 1, now - 5 * dayMs, now))
        assertFalse(GuideHistoryPolicy.canAttemptCatchup(false, now - 5 * dayMs, now))
        assertFalse(GuideHistoryPolicy.canAttemptCatchup(true, now + 1, now))
        assertFalse(GuideHistoryPolicy.canAttemptCatchup(true, now - 31 * dayMs - 1, now))
    }

    @Test
    fun liveOnlyChannelStillHasASevenDayBrowsableWindow() {
        val start = GuideHistoryPolicy.windowStart(now)
        assertEquals(23L * dayMs, start)
        assertTrue(now - 5L * dayMs >= start)
        assertFalse(GuideHistoryPolicy.canCatchup(false, 7, now - 5L * dayMs, now))
    }

    @Test
    fun missingArchiveDaysUseTheSameWeekAsLiveRewind() {
        for (missingDays in listOf(0, -1)) {
            assertTrue(GuideHistoryPolicy.canCatchup(true, missingDays, now - 6L * dayMs, now))
            assertTrue(GuideHistoryPolicy.canCatchup(true, missingDays, now - 7L * dayMs, now))
            assertFalse(GuideHistoryPolicy.canCatchup(true, missingDays, now - 7L * dayMs - 1, now))
        }
    }

    @Test
    fun shortProviderArchiveDoesNotShortenBrowsableHistory() {
        val oldStart = now - 2L * dayMs
        assertTrue(oldStart >= GuideHistoryPolicy.windowStart(now))
        assertFalse(GuideHistoryPolicy.canCatchup(true, 1, oldStart, now))
        assertTrue(GuideHistoryPolicy.canCatchup(true, 1, now - dayMs, now))
        assertFalse(GuideHistoryPolicy.canCatchup(true, 1, now - dayMs - 1, now))
    }

    @Test
    fun futureProgrammeNeverBecomesReplayable() {
        assertFalse(GuideHistoryPolicy.canCatchup(true, 7, now + 1, now))
        assertTrue(GuideHistoryPolicy.canCatchup(true, 7, now, now))
    }

    @Test
    fun browsingWindowMovesWithTimeWithoutGrowing() {
        val tomorrow = now + dayMs
        assertEquals(GuideHistoryPolicy.windowStart(now) + dayMs, GuideHistoryPolicy.windowStart(tomorrow))
        assertEquals(7L * dayMs, tomorrow - GuideHistoryPolicy.windowStart(tomorrow))
    }
}

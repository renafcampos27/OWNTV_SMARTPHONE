package tv.own.owntv.mobile.ui.screens.guide

import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuideTimePolicyTest {
    private val lisbon = TimeZone.getTimeZone("Europe/Lisbon")
    private fun ms(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()

    @Test fun todayRollsAtHalfHourAndMidnight() {
        val before = GuideTimePolicy.window(0, ms("2026-10-07T23:29:59+01:00"), lisbon)
        val halfHour = GuideTimePolicy.window(0, ms("2026-10-07T23:30:00+01:00"), lisbon)
        val nextDay = GuideTimePolicy.window(0, ms("2026-10-08T00:00:00+01:00"), lisbon)
        assertEquals(ms("2026-10-07T23:00:00+01:00"), before.start)
        assertEquals(ms("2026-10-07T23:30:00+01:00"), halfHour.start)
        assertEquals(before.end, nextDay.start)
        assertEquals(ms("2026-10-09T00:00:00+01:00"), nextDay.end)
    }

    @Test fun pastAndFutureDaysFollowNewLocalDateAfterMidnight() {
        val before = ms("2026-10-07T23:59:59+01:00")
        val after = ms("2026-10-08T00:00:00+01:00")
        assertEquals(ms("2026-10-06T00:00:00+01:00"), GuideTimePolicy.window(-1, before, lisbon).start)
        assertEquals(ms("2026-10-07T00:00:00+01:00"), GuideTimePolicy.window(-1, after, lisbon).start)
        assertEquals(ms("2026-10-09T00:00:00+01:00"), GuideTimePolicy.window(1, after, lisbon).start)
    }

    @Test fun fullDaysRespectBothDaylightSavingTransitions() {
        val spring = GuideTimePolicy.window(1, ms("2026-03-28T12:00:00Z"), lisbon)
        val autumn = GuideTimePolicy.window(1, ms("2026-10-24T12:00:00+01:00"), lisbon)
        assertEquals(23 * 60 * 60 * 1000L, spring.end - spring.start)
        assertEquals(25 * 60 * 60 * 1000L, autumn.end - autumn.start)
    }

    @Test fun currentProgrammeExpiresExactlyAtItsEnd() {
        val expiry = GuideTimePolicy.expiry(1_000, 90_000, 90_000)
        assertEquals(90_000L, expiry)
        assertTrue(GuideTimePolicy.isFresh(1_000, expiry, 89_999))
        assertFalse(GuideTimePolicy.isFresh(1_000, expiry, 90_000))
    }

    @Test fun emptyGuideRetriesAfterTtlOrEarlierNextStart() {
        assertEquals(31_000L, GuideTimePolicy.expiry(1_000, null, null))
        assertEquals(10_000L, GuideTimePolicy.expiry(1_000, null, 10_000))
        assertEquals(31_000L, GuideTimePolicy.expiry(1_000, null, 60_000))
        assertEquals(31_000L, GuideTimePolicy.expiry(1_000, 999, null))
        assertFalse(GuideTimePolicy.isFresh(1_000, 31_000, 31_000))
    }

    @Test fun clockMovingBackwardInvalidatesPreviousAnswer() {
        assertFalse(GuideTimePolicy.isFresh(10_000, 90_000, 9_999))
    }
}

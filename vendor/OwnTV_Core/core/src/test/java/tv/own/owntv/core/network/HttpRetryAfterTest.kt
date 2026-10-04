package tv.own.owntv.core.network

import org.junit.Assert.*
import org.junit.Test

class HttpRetryAfterTest {
    @Test fun secondsPreserveLongProviderWaitsAndSaturateOverflow() {
        assertEquals(120_000L, HttpRetryAfter.delayMs("120"))
        assertEquals(0L, HttpRetryAfter.delayMs("0"))
        assertEquals(Long.MAX_VALUE, HttpRetryAfter.delayMs("9223372036854775807"))
        assertEquals(Long.MAX_VALUE, HttpRetryAfter.delayMs("9999999999999999999999999"))
        assertNull(HttpRetryAfter.delayMs("-1"))
        assertNull(HttpRetryAfter.delayMs("1.5"))
    }
    @Test fun datesUseReceiptTimeAndPastDatesDoNotWait() {
        val instant = java.time.Instant.parse("2026-10-02T10:00:00Z").toEpochMilli()
        assertEquals(60_000L, HttpRetryAfter.delayMs("Fri, 2 Oct 2026 10:01:00 GMT", instant))
        assertEquals(0L, HttpRetryAfter.delayMs("Fri, 2 Oct 2026 09:59:00 GMT", instant))
        assertNull(HttpRetryAfter.delayMs("invalid", instant))
        assertNull(HttpRetryAfter.delayMs(null, instant))
        assertEquals(Long.MAX_VALUE, HttpRetryAfter.delayMs("Fri, 2 Oct 2026 10:01:00 GMT", Long.MIN_VALUE))
    }
    @Test fun obsoleteHttpDateFormatsRemainEligibleAndTwoDigitYearsUseTheReceiptCentury() {
        val instant = java.time.Instant.parse("2026-10-02T10:00:00Z").toEpochMilli()
        assertEquals(60_000L, HttpRetryAfter.delayMs("Friday, 02-Oct-26 10:01:00 GMT", instant))
        assertEquals(60_000L, HttpRetryAfter.delayMs("Fri Oct  2 10:01:00 2026", instant))
        assertEquals(0L, HttpRetryAfter.delayMs("Sunday, 06-Nov-94 08:49:37 GMT", instant))
    }
}

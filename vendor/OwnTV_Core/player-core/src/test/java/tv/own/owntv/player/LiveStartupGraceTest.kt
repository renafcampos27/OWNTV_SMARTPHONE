package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveStartupGraceTest {
    @Test fun `silence or an empty buffer never extends the opening deadline`() {
        val grace = LiveStartupGrace()
        repeat(30) { assertEquals(0L, grace.observe(LiveStartupProgress(1, 5_000, 0))) }
    }

    @Test fun `network progress can authorize a bounded prebuffer allowance before a full segment arrives`() {
        val grace = LiveStartupGrace()
        assertEquals(0L, grace.observe(LiveStartupProgress(1, 5_000, 0, 0)))
        assertEquals(5_000L, grace.observe(LiveStartupProgress(1, 5_000, 0, 100)))
        repeat(30) { assertEquals(0L, grace.observe(LiveStartupProgress(1, 5_000, 0, 101L + it))) }
    }

    @Test fun `buffer growth also authorizes allowance with detailed diagnostics disabled`() {
        assertEquals(5_000L, LiveStartupGrace().observe(LiveStartupProgress(1, 5_000, 2_000)))
    }

    @Test fun `internal retries cannot renew the whole allowance`() {
        val grace = LiveStartupGrace()
        assertEquals(10_000L, grace.observe(LiveStartupProgress(1, 30_000, 1_000)))
        assertEquals(0L, grace.observe(LiveStartupProgress(2, 30_000, 1_000)))
    }

    @Test fun `automatic startup does not add an artificial delay`() {
        assertEquals(0L, LiveStartupGrace().observe(LiveStartupProgress(1, 0, 4_000)))
    }
}

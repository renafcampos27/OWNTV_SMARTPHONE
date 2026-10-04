package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveReconnectHealthTest {
    @Test fun `budget is restored only at sixty seconds of stable playback`() {
        val health = LiveReconnectHealth()
        assertFalse(health.observe(100, true))
        assertFalse(health.observe(60_099, true))
        assertTrue(health.observe(60_100, true))
    }

    @Test fun `buffering pause or missing picture restarts the healthy window`() {
        val health = LiveReconnectHealth()
        health.observe(0, true)
        assertFalse(health.observe(59_999, false))
        assertFalse(health.observe(60_000, true))
        assertFalse(health.observe(119_999, true))
        assertTrue(health.observe(120_000, true))
    }

    @Test fun `repeated brief recoveries never replenish the retry budget`() {
        val health = LiveReconnectHealth()
        repeat(12) { n ->
            val t = n * 30_000L
            assertFalse(health.observe(t, true))
            assertFalse(health.observe(t + 20_000, true))
            assertFalse(health.observe(t + 29_999, false))
        }
    }
}

package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PrerollReachabilityTest {
    @Test fun `six second HLS segments survive several flat one second polls`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        for (now in 1_000L..12_000L step 1_000L) {
            assertNull(watcher.limitation(now, 0, 10_000, 6_000, 100, 0, 6_000))
        }
        assertNull(watcher.limitation(13_000, 0, 10_000, 12_000, 200, 0, 6_000))
    }

    @Test fun `in flight media with progressing bytes does not lose preroll to a flat buffer`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        for (now in 1_000L..19_000L step 1_000L) {
            assertNull(watcher.limitation(now, 0, 10_000, 2_000, now * 100, 1, 0))
        }
    }

    @Test fun `hung request and dribbling bytes still meet a finite deadline`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        assertEquals(PrerollReachability.Limitation.DEADLINE,
            watcher.limitation(20_000, 0, 10_000, 2_000, 100_000, 1, 0))
    }

    @Test fun `quiet raw stream gets one startup fallback after response activity ends`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        assertNull(watcher.limitation(1_000, 0, 10_000, 2_000, 100, 0, 0))
        assertNull(watcher.limitation(5_000, 0, 10_000, 2_000, 100, 0, 0))
        assertEquals(PrerollReachability.Limitation.QUIET_SOURCE,
            watcher.limitation(6_000, 0, 10_000, 2_000, 100, 0, 0))
    }

    @Test fun `bytes progress resets quiet interval without buffered duration growth`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        assertNull(watcher.limitation(1_000, 0, 10_000, 2_000, 100, 0, 0))
        assertNull(watcher.limitation(5_000, 0, 10_000, 2_000, 200, 0, 0))
        assertNull(watcher.limitation(9_000, 0, 10_000, 2_000, 200, 0, 0))
    }

    @Test fun `unknown HLS cadence has grace but never exceeds sixty seconds`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        assertNull(watcher.limitation(12_000, 0, 10_000, 2_000, 100, 0, null))
        assertEquals(PrerollReachability.Limitation.DEADLINE,
            watcher.limitation(60_000, 0, 10_000, 2_000, 200, 1, Long.MAX_VALUE))
    }

    @Test fun `satisfied threshold and off never request a fallback`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        assertNull(watcher.limitation(60_000, 0, 10_000, 10_000, 100, 0, 0))
        assertNull(watcher.limitation(60_000, 0, 0, 0, 0, 0, 0))
    }

    @Test fun `new source resets old deadline evidence`() {
        val watcher = PrerollReachability().apply { reset(0, 0) }
        watcher.limitation(20_000, 0, 10_000, 2_000, 100, 0, 0)
        watcher.reset(21_000, 100)
        assertNull(watcher.limitation(22_000, 21_000, 10_000, 2_000, 100, 0, 0))
    }

    @Test fun `transfer accounting tracks concurrent bodies and ignores invalid bytes`() {
        val transfers = StartupTransfers()
        transfers.started(); transfers.started()
        transfers.transferred(10); transfers.transferred(20); transfers.transferred(-1)
        assertEquals(2 to 30L, transfers.snapshot())
        transfers.ended()
        assertEquals(1 to 30L, transfers.snapshot())
        transfers.ended(); transfers.ended()
        assertEquals(0 to 30L, transfers.snapshot())
    }
}

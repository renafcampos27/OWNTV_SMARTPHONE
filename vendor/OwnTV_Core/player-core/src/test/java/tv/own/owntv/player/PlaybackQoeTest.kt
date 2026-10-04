package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackQoeTest {
    @Test fun initialBufferIsSeparateFromARebuffer() {
        var now = 0L
        val qoe = PlaybackQoe { now }
        qoe.buffering(true)
        now = 500L
        assertEquals(PlaybackQoe.BufferEpisode(500L, false), qoe.finishBuffer())
        qoe.confirmPlayback()
        qoe.buffering(true)
        now = 750L
        qoe.buffering(true) // A duplicate state callback must not restart the clock.
        now = 900L
        assertEquals(PlaybackQoe.BufferEpisode(400L, true), qoe.finishBuffer())
        assertNull(qoe.finishBuffer())
        assertEquals(PlaybackQoe.Snapshot(1, 400L, 0), qoe.snapshot())
    }

    @Test fun manualPauseAndDisabledPlaybackDoNotCountAsRecovery() {
        var now = 0L
        val qoe = PlaybackQoe { now }
        qoe.confirmPlayback()
        qoe.buffering(true)
        qoe.suspendMeasurement()
        now = 20_000L
        assertNull(qoe.finishBuffer())
        qoe.buffering(false)
        assertNull(qoe.finishBuffer())
        assertEquals(0, qoe.snapshot().rebufferCount)
    }

    @Test fun rebuildsRemainVisibleUntilAnExplicitNewTune() {
        val qoe = PlaybackQoe { 100L }
        qoe.confirmPlayback()
        qoe.rebuilt()
        qoe.rebuilt()
        assertEquals(2, qoe.snapshot().rebuildCount)
        qoe.reset()
        assertEquals(PlaybackQoe.Snapshot(0, 0L, 0), qoe.snapshot())
    }
}

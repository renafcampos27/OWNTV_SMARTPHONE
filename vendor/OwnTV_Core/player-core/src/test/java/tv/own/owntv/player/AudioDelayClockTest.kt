package tv.own.owntv.player

import androidx.media3.exoplayer.audio.AudioSink
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioDelayClockTest {

    @Test
    fun `positive delay runs the video clock ahead, so sound comes later`() {
        val clock = AudioDelayClock().apply { delayMs = 250 }
        assertEquals(10_250_000L, clock.shift(10_000_000L))
    }

    @Test
    fun `negative delay holds the video clock back`() {
        val clock = AudioDelayClock().apply { delayMs = -100 }
        assertEquals(9_900_000L, clock.shift(10_000_000L))
    }

    @Test
    fun `no position yet passes through`() {
        val clock = AudioDelayClock().apply { delayMs = 500 }
        assertEquals(AudioSink.CURRENT_POSITION_NOT_SET, clock.shift(AudioSink.CURRENT_POSITION_NOT_SET))
    }

    @Test
    fun `zero delay changes nothing`() {
        assertEquals(1_234L, AudioDelayClock().shift(1_234L))
    }
    @Test
    fun `delay is bounded in both directions and can be reset`() {
        val clock = AudioDelayClock()
        clock.delayMs = Int.MAX_VALUE
        assertEquals(15_000_000L, clock.shift(10_000_000L))
        clock.delayMs = Int.MIN_VALUE
        assertEquals(5_000_000L, clock.shift(10_000_000L))
        clock.delayMs = 0
        assertEquals(10_000_000L, clock.shift(10_000_000L))
    }

}

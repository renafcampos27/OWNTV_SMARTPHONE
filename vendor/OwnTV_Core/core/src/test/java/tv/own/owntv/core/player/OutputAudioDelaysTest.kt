package tv.own.owntv.core.player

import org.junit.Assert.*
import org.junit.Test

class OutputAudioDelaysTest {
    @Test fun defaultsAndUnknownNeverApplyCompensation() {
        assertEquals(0, OutputAudioDelays()[AudioOutputKind.BLUETOOTH])
        assertEquals(0, OutputAudioDelays().with(AudioOutputKind.UNKNOWN, 200)[AudioOutputKind.UNKNOWN])
    }
    @Test fun roundTripKeepsIndependentPositiveAndNegativeClasses() {
        val delays = OutputAudioDelays().with(AudioOutputKind.HDMI, -150).with(AudioOutputKind.BLUETOOTH, 250)
        assertEquals(delays, OutputAudioDelays.parse(delays.encode()))
        assertFalse(delays.encode().contains("UNKNOWN"))
    }
    @Test fun malformedAndFutureClassesAreIgnoredAndValuesClamped() {
        val delays = OutputAudioDelays.parse("FUTURE=20,HDMI=9000,USB=-9000,BAD,SPEAKER=no,ARC=20=50")
        assertEquals(5000, delays[AudioOutputKind.HDMI])
        assertEquals(-5000, delays[AudioOutputKind.USB])
        assertEquals(0, delays[AudioOutputKind.SPEAKER])
        assertEquals(0, delays[AudioOutputKind.ARC])
    }
    @Test fun effectiveOffsetClampsWithoutOverwritingBaseOrOverflowing() {
        assertEquals(350, OutputAudioDelays.effective(100, 250))
        assertEquals(5000, OutputAudioDelays.effective(4900, 300))
        assertEquals(-5000, OutputAudioDelays.effective(Int.MIN_VALUE, -100))
    }
}

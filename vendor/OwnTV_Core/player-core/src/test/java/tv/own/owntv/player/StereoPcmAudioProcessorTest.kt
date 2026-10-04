package tv.own.owntv.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StereoPcmAudioProcessorTest {
    private fun mix(channels: Int, samples: IntArray, mask: Int? = null, mapping: IntArray? = null): ShortArray {
        val processor = StereoPcmAudioProcessor()
        if (mask != null || mapping != null) processor.setChannelLayout(mask, mapping, channels)
        val inputChannels = mapping?.size ?: channels
        val format = processor.configure(AudioProcessor.AudioFormat(48000, inputChannels, C.ENCODING_PCM_16BIT))
        assertEquals(2, format.channelCount)
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output.order(ByteOrder.nativeOrder())
        return ShortArray(output.remaining() / 2) { output.short }
    }
    @Test fun monoDuplicatesEveryFrameWithoutChangingDuration() {
        assertArrayEquals(shortArrayOf(12000, 12000, -12000, -12000, 0, 0), mix(1, intArrayOf(12000, -12000, 0)))
    }
    @Test fun normalStereoIsAnExactBypass() {
        val processor = StereoPcmAudioProcessor()
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)))
        processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
        assertFalse(processor.isActive)
    }
    @Test fun centerDialogueAndLfeReachBothSpeakers() {
        for (channel in intArrayOf(2, 3)) {
            val samples = IntArray(6); samples[channel] = 16000
            val output = mix(6, samples)
            assertEquals(output[0], output[1]); assertTrue(output[0] > 0)
        }
    }
    @Test fun surroundAndFrontSidesStaySeparate() {
        for (channel in intArrayOf(0, 4)) {
            val samples = IntArray(6); samples[channel] = 16000
            val output = mix(6, samples)
            assertTrue(output[0] > 0); assertEquals(0, output[1].toInt())
        }
        for (channel in intArrayOf(1, 5)) {
            val samples = IntArray(6); samples[channel] = 16000
            val output = mix(6, samples)
            assertEquals(0, output[0].toInt()); assertTrue(output[1] > 0)
        }
    }
    @Test fun sixPointOneUsesBackLeftBackRightThenBackCenter() {
        val left = IntArray(7); left[4] = 16000
        assertEquals(0, mix(7, left)[1].toInt())
        val center = IntArray(7); center[6] = 16000
        val output = mix(7, center)
        assertEquals(output[0], output[1]); assertTrue(output[0] > 0)
    }
    @Test fun everySupportedMultichannelLayoutKeepsCorrelatedFullScaleWithinRange() {
        for (channels in 3..8) {
            val positive = mix(channels, IntArray(channels) { 32767 })
            val negative = mix(channels, IntArray(channels) { -32768 })
            assertTrue(positive.all { it.toInt() in 32760..32767 })
            assertTrue(negative.all { it.toInt() in -32768..-32760 })
        }
    }
    @Test fun explicitThreeChannelLfeMaskIsNotMistakenForCenter() {
        val matrix = stereoMixForPositions(intArrayOf(4, 8, 32))
        val expected = (16000 * matrix[2][0]).toInt()
        val output = mix(3, intArrayOf(0, 0, 16000), mask = 4 or 8 or 32)
        assertEquals(expected, output[0].toInt()); assertEquals(output[0], output[1])
    }
    @Test fun decoderChannelMappingKeepsOriginalPositions() {
        // A decoder remapped a 5.1 output to FL/FC. FC must still reach the right speaker.
        val output = mix(6, intArrayOf(0, 16000), mapping = intArrayOf(0, 2))
        assertTrue(output[0] > 0); assertEquals(output[0], output[1])
    }
    @Test fun flushingAFormatChangeReplacesTheMatrixAndRetainsSampleRate() {
        val p = StereoPcmAudioProcessor()
        p.configure(AudioProcessor.AudioFormat(48000, 6, C.ENCODING_PCM_16BIT)); p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val changed = p.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        assertEquals(44100, changed.sampleRate); assertEquals(2, changed.channelCount)
        p.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val input = ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder()).putShort(15000)
        input.flip(); p.queueInput(input)
        val output = p.output.order(ByteOrder.nativeOrder())
        assertEquals(15000, output.short.toInt()); assertEquals(15000, output.short.toInt())
    }
    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun heightChannelsFailExplicitly() {
        val p = StereoPcmAudioProcessor()
        p.setChannelLayout(4 or 8 or 8192, null, 3)
        p.configure(AudioProcessor.AudioFormat(48000, 3, C.ENCODING_PCM_16BIT))
    }
    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun floatInputCannotBypassRequiredPcm16Mixing() {
        StereoPcmAudioProcessor().configure(AudioProcessor.AudioFormat(48000, 6, C.ENCODING_PCM_FLOAT))
    }
    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun unsupportedChannelCountFailsExplicitly() {
        StereoPcmAudioProcessor().configure(AudioProcessor.AudioFormat(48000, 10, C.ENCODING_PCM_16BIT))
    }
}

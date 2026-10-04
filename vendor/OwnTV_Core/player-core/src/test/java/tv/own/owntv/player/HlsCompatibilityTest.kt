package tv.own.owntv.player

import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.settings.ChannelPlaybackOptions

class HlsCompatibilityTest {
    @Test fun `ordinary channel and explicit off preserve defaults`() {
        assertEquals(HlsCompatibility(), HlsCompatibility.from(null))
        assertEquals(HlsCompatibility(), HlsCompatibility.from(ChannelPlaybackOptions()))
        val off = ChannelPlaybackOptions(hlsDetectAccessUnits = false, hlsAllowNonIdrKeyframes = false, hlsPrepareFromSegments = false)
        assertEquals(HlsCompatibility(), HlsCompatibility.from(off))
        assertEquals(0, HlsCompatibility.from(off).extractorFlags)
    }
    @Test fun `exceptions are independent and do not carry over to next channel`() {
        val detection = HlsCompatibility.from(ChannelPlaybackOptions(hlsDetectAccessUnits = true))
        assertEquals(DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS, detection.extractorFlags)
        assertFalse(detection.allowNonIdrKeyframes)
        assertFalse(detection.prepareFromSegments)
        val keys = HlsCompatibility.from(ChannelPlaybackOptions(hlsAllowNonIdrKeyframes = true))
        assertEquals(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES, keys.extractorFlags)
        assertNotEquals(detection, HlsCompatibility.from(null))
        assertNotEquals(keys, HlsCompatibility.from(null))
        val segments = HlsCompatibility.from(ChannelPlaybackOptions(hlsPrepareFromSegments = true))
        assertEquals(0, segments.extractorFlags)
        assertTrue(segments.prepareFromSegments)
    }
}

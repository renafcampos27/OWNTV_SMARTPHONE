package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftwareAudioDecoderPolicyTest {
    @Test fun `software audio sorts software first but preserves platform video order`() {
        org.junit.Assert.assertEquals(0, decoderPriority("audio/mp4a-latm", false, true, false, true))
        org.junit.Assert.assertEquals(1, decoderPriority("audio/mp4a-latm", true, false, false, true))
        org.junit.Assert.assertEquals(0, decoderPriority("video/avc", true, false, false, true))
        org.junit.Assert.assertEquals(0, decoderPriority("video/avc", false, true, false, true))
    }

    @Test fun `disabling hardware sorts video software first without deleting fallback`() {
        org.junit.Assert.assertEquals(0, decoderPriority("video/avc", false, true, true, false))
        org.junit.Assert.assertEquals(1, decoderPriority("video/avc", true, false, true, false))
    }
    @Test fun `audio preference never changes video or text decoding`() {
        assertTrue(preferSoftwareDecoder("audio/mp4a-latm", false, true))
        assertTrue(preferSoftwareDecoder("audio/ac3", false, true))
        assertFalse(preferSoftwareDecoder("video/avc", false, true))
        assertFalse(preferSoftwareDecoder("video/hevc", false, true))
        assertFalse(preferSoftwareDecoder("text/vtt", false, true))
    }

    @Test fun `default keeps platform order and existing global override remains effective`() {
        assertFalse(preferSoftwareDecoder("audio/mp4a-latm", false, false))
        assertFalse(preferSoftwareDecoder("video/avc", false, false))
        assertTrue(preferSoftwareDecoder("video/avc", true, false))
        assertTrue(preferSoftwareDecoder("audio/mp4a-latm", true, false))
    }
}

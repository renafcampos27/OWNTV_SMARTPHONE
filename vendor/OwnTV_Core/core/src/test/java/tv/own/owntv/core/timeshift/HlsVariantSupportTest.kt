package tv.own.owntv.core.timeshift

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.recording.HlsMediaPlaylist
import tv.own.owntv.core.recording.HlsRecordingPlan

class HlsVariantSupportTest {
    private fun master(token: String = "old") = HlsMediaPlaylist.parse(
        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=8000000,CODECS=\"hvc1.1.6.L153,mp4a.40.2\",RESOLUTION=3840x2160\nhigh.m3u8?token=$token\n" +
        "#EXT-X-STREAM-INF:BANDWIDTH=2000000,CODECS=\"avc1.4d401f,mp4a.40.2\",RESOLUTION=1920x1080\nlow.m3u8?token=$token\n")
    @Test fun explicitCodecAndSizeLimitsChooseAnEligibleVariantAndKeepItsIdentity() {
        val supported: (HlsMediaPlaylist.Variant) -> Boolean = { variant ->
            HlsVariantSupport.supports(variant) { mime, width, height ->
                mime != "video/hevc" && (width == null || width <= 1920) && (height == null || height <= 1080)
            }
        }
        val selected = HlsRecordingPlan.selectVariant(master(), supported = supported)!!
        assertEquals("low.m3u8?token=old", selected.uri)
        assertEquals(1920, selected.width)
        assertEquals(1080, selected.height)
        assertEquals("low.m3u8?token=new", HlsRecordingPlan.selectVariant(master("new"), HlsRecordingPlan.identity(selected), supported)!!.uri)
        // Recording's existing default selection stays independent of local-timeshift policy.
        assertEquals("high.m3u8?token=old", HlsRecordingPlan.selectVariant(master())!!.uri)
    }
    @Test fun absentOrUnknownMetadataAndUnavailableCapabilitiesRemainEligible() {
        assertTrue(HlsVariantSupport.supports(HlsMediaPlaylist.Variant("unknown", 1, null, null)) { _, _, _ -> false })
        assertTrue(HlsVariantSupport.supports(HlsMediaPlaylist.Variant("unknown", 1, null, "future-codec")) { _, _, _ -> false })
        assertTrue(HlsVariantSupport.supports(HlsMediaPlaylist.Variant("dv-base-fallback", 1, null, "dvhe.08.06")) { _, _, _ -> false })
        assertTrue(HlsVariantSupport.supports(master().variants.first()) { _, _, _ -> null })
    }
    @Test fun anExplicitAudioRefusalAlsoDisqualifiesAVideoVariant() {
        assertFalse(HlsVariantSupport.supports(master().variants.first()) { mime, _, _ -> mime != "audio/mp4a-latm" })
        assertNull(HlsRecordingPlan.selectVariant(master(), supported = { false }))
    }
}

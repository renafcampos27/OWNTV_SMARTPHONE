package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class HlsRecordingFormatsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `master variants are never returned as media segments and best supported variant is fixed`() {
        val text = """
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="external",NAME="Portuguese",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=12000000,AUDIO="external",CODECS="hvc1.1.6,mp4a.40.2"
            ultra.m3u8?token=secret
            #EXT-X-STREAM-INF:BANDWIDTH=6000000,CODECS="avc1.640028,mp4a.40.2"
            full.m3u8?token=old
            #EXT-X-STREAM-INF:BANDWIDTH=3000000
            low.m3u8
        """.trimIndent()
        val master = HlsMediaPlaylist.parse(text)
        assertTrue(master.isMaster)
        assertFalse(master.invalid)
        assertTrue(master.segments.isEmpty())
        val selected = HlsRecordingPlan.selectVariant(master)!!
        assertEquals("full.m3u8?token=old", selected.uri)
        val identity = HlsRecordingPlan.identity(selected)
        assertFalse(identity.contains("secret"))
        assertFalse(identity.contains("m3u8"))
        val rotated = HlsMediaPlaylist.parse(text.replace("token=old", "token=new"))
        assertEquals("full.m3u8?token=new", HlsRecordingPlan.selectVariant(rotated, identity)!!.uri)
        assertNull(HlsRecordingPlan.selectVariant(HlsMediaPlaylist.parse(text.replace("full.m3u8", "changed.m3u8")), identity))
    }

    @Test fun `external audio only master explicitly has no safely recordable variant`() {
        val master = HlsMediaPlaylist.parse("""
            #EXTM3U
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="sound",URI="audio.m3u8"
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,AUDIO="sound"
            video.m3u8
        """.trimIndent())
        assertNull(HlsRecordingPlan.selectVariant(master))
    }

    @Test fun `fMP4 init ranges and implicit adjacent ranges retain exact offsets and discontinuity`() {
        val playlist = HlsMediaPlaylist.parse("""
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:20
            #EXT-X-DISCONTINUITY-SEQUENCE:3
            #EXT-X-MAP:URI="resource.mp4?token=init",BYTERANGE="100@0"
            #EXTINF:6,
            #EXT-X-BYTERANGE:200@100
            resource.mp4
            #EXTINF:6,
            #EXT-X-BYTERANGE:250
            resource.mp4
            #EXT-X-DISCONTINUITY
            #EXT-X-GAP
            #EXTINF:6,
            #EXT-X-BYTERANGE:200@550
            resource.mp4
        """.trimIndent())
        assertFalse(playlist.invalid)
        assertEquals(HlsMediaPlaylist.ByteRange(100, 0), playlist.segments[0].init!!.range)
        assertEquals("bytes=100-299", playlist.segments[0].range!!.header)
        assertEquals(HlsMediaPlaylist.ByteRange(250, 300), playlist.segments[1].range)
        assertEquals(listOf(3L, 3L, 4L), playlist.segments.map { it.discontinuity })
        assertTrue(playlist.segments[2].gap)
    }

    @Test fun `invalid implicit byte range and malformed init are refused`() {
        assertTrue(HlsMediaPlaylist.parse("#EXTM3U\n#EXTINF:6,\n#EXT-X-BYTERANGE:100\na.mp4").invalid)
        assertTrue(HlsMediaPlaylist.parse("#EXTM3U\n#EXT-X-MAP:BYTERANGE=100@0\n#EXTINF:6,\na.mp4").invalid)
        val changedResource = "#EXTM3U\n#EXTINF:6,\n#EXT-X-BYTERANGE:100@0\na.mp4\n#EXTINF:6,\n#EXT-X-BYTERANGE:100\nb.mp4"
        assertTrue(HlsMediaPlaylist.parse(changedResource).invalid)
        assertNull(HlsMediaPlaylist.byteRange("100@9223372036854775800", null))
    }

    @Test fun `encryption remains refused if later segments switch to METHOD NONE`() {
        val playlist = HlsMediaPlaylist.parse("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=key\n#EXTINF:6,\na.ts\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:6,\nb.ts")
        assertTrue(playlist.isEncrypted)
    }

    @Test fun `partial only low latency playlist does not pretend to be a complete media capture`() {
        val playlist = HlsMediaPlaylist.parse("#EXTM3U\n#EXT-X-PART:DURATION=1,URI=part.mp4\n#EXT-X-ENDLIST")
        assertTrue(playlist.segments.isEmpty())
    }

    @Test fun `complete fMP4 boxes are detected and truncated boxes rejected`() {
        val complete = temporary.newFile()
        complete.writeBytes(box("styp", byteArrayOf(1, 2)) + box("moof", byteArrayOf(3)) + box("mdat", byteArrayOf(4, 5)))
        assertTrue(HlsSegmentTransfer.hasMp4Box(complete, "moof"))
        assertTrue(HlsSegmentTransfer.hasMp4Box(complete, "mdat"))
        val broken = temporary.newFile()
        broken.writeBytes(box("moof", byteArrayOf(1, 2)).dropLast(1).toByteArray())
        assertFalse(HlsSegmentTransfer.hasMp4Box(broken, "moof"))
        assertFalse(HlsSegmentTransfer.isTransportStream(complete))
    }

    @Test fun `a transport stream requires complete aligned packets`() {
        val ts = temporary.newFile()
        val bytes = ByteArray(188 * 2).apply { this[0] = 0x47; this[188] = 0x47 }
        ts.writeBytes(bytes)
        assertTrue(HlsSegmentTransfer.isTransportStream(ts))
        ts.appendBytes(byteArrayOf(0))
        assertFalse(HlsSegmentTransfer.isTransportStream(ts))
    }

    @Test fun `window completeness distinguishes known archive shortfall from minor segment rounding`() {
        assertTrue(HlsCaptureCompleteness.missingWindow(60_000, 6_000, 0, 120_000, 500_000, true))
        assertFalse(HlsCaptureCompleteness.missingWindow(116_000, 6_000, 0, 120_000, 500_000, true))
        assertTrue(HlsCaptureCompleteness.missingWindow(60_000, 6_000, 100_000, 200_000, 108_000, false))
        assertFalse(HlsCaptureCompleteness.missingWindow(98_000, 6_000, 100_000, 200_000, 101_000, false))
    }

    private fun box(type: String, payload: ByteArray): ByteArray = java.nio.ByteBuffer.allocate(payload.size + 8)
        .putInt(payload.size + 8).put(type.toByteArray(Charsets.US_ASCII)).put(payload).array()
}

package tv.own.owntv.player

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class SdrVariantsTest {
    @Test fun mixedManifestKeepsOnlyExplicitSdrUris() {
        val text = """#EXTM3U
            |#EXT-X-STREAM-INF:BANDWIDTH=2000000,VIDEO-RANGE=SDR
            |sdr.m3u8
            |#EXT-X-STREAM-INF:VIDEO-RANGE=PQ,BANDWIDTH=4000000
            |hdr.m3u8
            |#EXT-X-STREAM-INF:BANDWIDTH=4000000,VIDEO-RANGE=HLG
            |hlg.m3u8
        """.trimMargin()
        assertEquals(setOf("sdr.m3u8"), SdrVariants.sdrUris(text))
    }
    @Test fun hdrOnlySdrOnlyAndUnspecifiedRangesNeverRemoveAllVariants() {
        for (range in listOf("PQ", "SDR", "UNKNOWN"))
            assertNull(SdrVariants.sdrUris("#EXTM3U\n#EXT-X-STREAM-INF:VIDEO-RANGE=$range\na.m3u8"))
        assertNull(SdrVariants.sdrUris("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=4\na.m3u8\n#EXT-X-STREAM-INF:VIDEO-RANGE=PQ\nb.m3u8"))
    }
    @Test fun mediaPlaylistAndCommentsDoNotInventVariants() {
        assertNull(SdrVariants.sdrUris("#EXTM3U\n#EXTINF:4\nsegment.ts"))
        assertEquals(setOf("a.m3u8"), SdrVariants.sdrUris("#EXTM3U\n#EXT-X-STREAM-INF:VIDEO-RANGE=SDR\n#comment\na.m3u8\n#EXT-X-STREAM-INF:VIDEO-RANGE=PQ\nb.m3u8"))
    }
    @Test fun boundedCapturePassesEveryByteButDisablesFilteringOnOverflow() {
        val data = ByteArray(1000) { (it % 127).toByte() }
        val input = BoundedManifestCapture(ByteArrayInputStream(data), 100)
        assertArrayEquals(data, input.readBytes())
        assertNull(input.text())
    }
    @Test fun exactLimitAndSingleByteReadsAreCapturedOnce() {
        val input = BoundedManifestCapture(ByteArrayInputStream("abcd".toByteArray()), 4)
        assertEquals('a'.code, input.read())
        assertArrayEquals("bcd".toByteArray(), input.readBytes())
        assertEquals("abcd", input.text())
    }
}

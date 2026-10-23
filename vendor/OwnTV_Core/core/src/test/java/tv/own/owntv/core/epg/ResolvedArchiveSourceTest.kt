package tv.own.owntv.core.epg

import org.junit.Assert.*
import org.junit.Test

class ResolvedArchiveSourceTest {
    @Test fun formatComesFromArchiveEvidenceAndNeverInventsAlternatives() {
        val hls = ResolvedArchiveSource.of("https://host/archive.m3u8?token=secret")
        assertEquals("hls", hls.manifestTypes[hls.url])
        assertTrue(hls.alternatives.isEmpty())
        assertTrue(ResolvedArchiveSource.of("https://host/timeshift/1.ts").manifestTypes.isEmpty())
        assertTrue(ResolvedArchiveSource.of("https://host/streaming/timeshift.php?format=m3u8").manifestTypes.isEmpty())
        assertEquals("mpd", ResolvedArchiveSource.of("https://host/archive.mpd").manifestTypes.values.single())
    }
}

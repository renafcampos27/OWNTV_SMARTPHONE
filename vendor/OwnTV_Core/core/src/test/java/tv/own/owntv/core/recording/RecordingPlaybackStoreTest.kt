package tv.own.owntv.core.recording

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingPlaybackStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun capture(container: String = "ts") = HlsCaptureSession(temporary.newFolder()).apply {
        this.container = container
        playback.configureTarget(6.0)
    }
    private fun commit(session: HlsCaptureSession, seq: Long, dc: Long = 0) {
        val scratch = File(session.directory, "segment.download").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        session.commit(HlsMediaPlaylist.Segment("https://provider.invalid/private?token=secret", 6.0, seq, discontinuity = dc), scratch)
    }

    @Test fun onlyCommittedSegmentsArePublishedAndTimelineGrowsFromTheStart() {
        val session = capture()
        File(session.directory, "segment.download").writeBytes(byteArrayOf(1))
        assertNull(session.playback.acquire())
        commit(session, 100)
        val first = session.playback.playlist()
        assertTrue(first.contains("#EXT-X-PLAYLIST-TYPE:EVENT"))
        assertTrue(first.contains("TIME-OFFSET=0"))
        assertTrue(first.contains("0.ts"))
        assertFalse(first.contains("secret"))
        assertFalse(first.contains("segment.download"))
        commit(session, 101)
        val second = session.playback.playlist()
        assertTrue(second.contains("0.ts") && second.contains("1.ts"))
        assertFalse(second.contains("#EXT-X-ENDLIST"))
        session.discard()
    }

    @Test fun gapsAndDiscontinuitiesAreSignalledWithoutPublishingMissingFiles() {
        val session = capture()
        commit(session, 100)
        session.skip(101, 6_000)
        commit(session, 102, 1)
        val playlist = session.playback.playlist()
        assertEquals(1, Regex("#EXT-X-DISCONTINUITY\\n").findAll(playlist).count())
        assertEquals(2, Regex("#EXTINF").findAll(playlist).count())
        session.discard()
    }

    @Test fun finalizationPreservesFilesUntilBothViewerAndResponseHaveReleasedThem() {
        val session = capture()
        commit(session, 1)
        val viewer = session.playback.acquire()!!
        val response = session.playback.open(0)!!
        session.playback.finish()
        assertTrue(session.playback.playlist().contains("#EXT-X-ENDLIST"))
        assertNull(session.playback.acquire())
        session.discard()
        viewer.close()
        assertTrue(session.inputs().single().isFile)
        assertEquals(1, response.input.read())
        response.close()
        val deadline = System.nanoTime() + 2_000_000_000L
        while (session.directory.exists() && System.nanoTime() < deadline) Thread.sleep(10)
        assertFalse(session.directory.exists())
    }

    @Test fun pausedOrFailedCaptureKeepsItsSegmentsAfterViewerCloses() {
        val session = capture()
        commit(session, 1)
        val viewer = session.playback.acquire()!!
        session.playback.finish()
        viewer.close()
        assertTrue(session.directory.isDirectory)
        assertTrue(session.inputs().single().isFile)
        session.discard()
    }

    @Test fun fragmentedMp4UsesOnlyCommittedInitializationAndMedia() {
        val session = capture("fmp4")
        val init = File(session.directory, "init.download").apply { writeBytes(byteArrayOf(8, 9)) }
        session.commitInit(init)
        commit(session, 1)
        val viewer = session.playback.acquire()!!
        assertTrue(session.playback.playlist().contains("#EXT-X-MAP:URI=\"-1.mp4\""))
        assertTrue(session.playback.playlist().contains("0.m4s"))
        session.playback.open(-1)!!.use { assertEquals("video/mp4", it.type); assertEquals(8, it.input.read()) }
        viewer.close()
        session.discard()
    }

    @Test fun localHttpServesRangesAndRejectsUnpublishedPathsWithoutUpstreamRequests() {
        val session = capture()
        commit(session, 1)
        val playback = session.playback.playback()!!
        val client = OkHttpClient()
        fun fetch(url: String, range: String? = null) = client.newCall(Request.Builder().url(url).apply {
            if (range != null) header("Range", range)
        }.build()).execute()
        try {
            fetch(playback.url).use { assertEquals(200, it.code); assertTrue(it.body.string().contains("0.ts")) }
            fetch(playback.url.replace("index.m3u8", "0.ts"), "bytes=1-2").use {
                assertEquals(206, it.code); assertTrue(it.body.bytes().contentEquals(byteArrayOf(2, 3)))
            }
            fetch(playback.url.replace("index.m3u8", "segment.download")).use { assertEquals(404, it.code) }
            fetch(playback.url.replace("index.m3u8", "99.ts")).use { assertEquals(404, it.code) }
            fetch(playback.url.replace("index.m3u8", "0.ts"), "bytes=99-").use { assertEquals(416, it.code) }
            commit(session, 2)
            fetch(playback.url).use { assertTrue(it.body.string().contains("1.ts")) }
            session.playback.finish()
            fetch(playback.url).use { assertTrue(it.body.string().contains("#EXT-X-ENDLIST")) }
        } finally {
            playback.close(); session.discard()
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun oversizedSegmentDoesNotMutateAnAlreadyPublishedTargetDuration() {
        val session = capture()
        commit(session, 1)
        val viewer = session.playback.acquire()!!
        val scratch = File(session.directory, "segment.download").apply { writeBytes(byteArrayOf(5)) }
        session.commit(HlsMediaPlaylist.Segment("next.ts", 10.0, 2), scratch)
        val playlist = session.playback.playlist()
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:6"))
        assertFalse(playlist.contains("1.ts"))
        assertTrue(playlist.contains("#EXT-X-ENDLIST"))
        assertEquals(2, session.entries.size)
        viewer.close(); session.discard()
    }
}

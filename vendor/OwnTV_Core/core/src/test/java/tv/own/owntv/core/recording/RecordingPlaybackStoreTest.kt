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

    private fun entry(sequence: Long, durationMs: Long = 6_000, dc: Long = 0) =
        HlsCaptureSession.Entry(sequence, durationMs, "segment-$sequence.bin", "a".repeat(64), 4, dc)

    @Test fun unchangedRevisionReusesImmutablePlaylistAndCommitInvalidatesIt() {
        val store = RecordingPlaybackStore(temporary.newFolder())
        store.configureTarget(6.0)
        store.publish(entry(10, 5_007), "ts", false)
        val first = store.playlist()
        assertSame(first, store.playlist())
        assertTrue(first.contains("#EXTINF:5.007,"))
        store.publish(entry(11), "ts", false)
        val next = store.playlist()
        assertNotSame(first, next)
        assertSame(next, store.playlist())
        assertFalse(first.contains("1.ts"))
        assertTrue(next.contains("0.ts") && next.contains("1.ts"))
        store.publish(entry(11), "ts", false)
        assertSame(next, store.playlist())
        store.finish()
        val ended = store.playlist()
        assertTrue(ended.contains("#EXT-X-ENDLIST"))
        assertSame(ended, store.playlist())
        store.finish()
        assertSame(ended, store.playlist())
        store.discard()
    }

    @Test fun configuringTargetInvalidatesAlreadyBuiltPlaylistWithoutLosingHistory() {
        val store = RecordingPlaybackStore(temporary.newFolder())
        store.publish(entry(1, 1_001), "ts", false)
        val initial = store.playlist()
        assertTrue(initial.contains("#EXT-X-TARGETDURATION:2"))
        store.configureTarget(6.0)
        val configured = store.playlist()
        assertNotSame(initial, configured)
        assertTrue(configured.contains("#EXT-X-TARGETDURATION:6"))
        assertTrue(configured.contains("#EXTINF:1.001,"))
        store.discard()
    }

    @Test fun longTimelinePreservesEverySegmentAndDiscontinuity() {
        val store = RecordingPlaybackStore(temporary.newFolder())
        store.configureTarget(6.0)
        (0L until 2_000).forEach { store.publish(entry(it, dc = if (it < 1_000) 0 else 1), "ts", false) }
        val playlist = store.playlist()
        assertEquals(2_000, Regex("#EXTINF:").findAll(playlist).count())
        assertEquals(1, Regex("#EXT-X-DISCONTINUITY").findAll(playlist).count())
        assertTrue(playlist.contains("\n0.ts\n")); assertTrue(playlist.contains("\n1999.ts\n"))
        assertSame(playlist, store.playlist())
        store.discard()
    }

    @Test fun concurrentRequestsAndCommitsProduceConsistentSnapshotsAndFinalRevision() {
        val store = RecordingPlaybackStore(temporary.newFolder())
        store.configureTarget(6.0)
        store.publish(entry(0), "ts", false)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val writer = pool.submit {
                start.await()
                (1L..400L).forEach { store.publish(entry(it), "ts", false) }
                store.finish()
            }
            val readers = (1..3).map { pool.submit {
                start.await()
                repeat(200) {
                    val text = store.playlist()
                    val media = text.lineSequence().filter { it.endsWith(".ts") }.toList()
                    assertTrue(media.isNotEmpty())
                    assertEquals(media.indices.map { "$it.ts" }, media)
                    assertEquals(media.size, Regex("#EXTINF:").findAll(text).count())
                }
            } }
            start.countDown()
            writer.get(10, java.util.concurrent.TimeUnit.SECONDS)
            readers.forEach { it.get(10, java.util.concurrent.TimeUnit.SECONDS) }
            val complete = store.playlist()
            assertEquals(401, Regex("#EXTINF:").findAll(complete).count())
            assertTrue(complete.contains("#EXT-X-ENDLIST"))
            assertSame(complete, store.playlist())
        } finally { pool.shutdownNow(); store.discard() }
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

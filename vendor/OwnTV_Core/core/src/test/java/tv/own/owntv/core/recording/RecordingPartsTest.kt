package tv.own.owntv.core.recording

import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.own.owntv.core.storage.MediaTarget

class RecordingPartsTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun session(format: String = "ts") = HlsCaptureSession(temporary.newFolder()).apply { container = format }
    private fun target() = MediaTarget.Path(File(temporary.newFolder(), "recording.ts"))
    private fun commit(session: HlsCaptureSession, sequence: Long, dc: Long = 0) {
        val scratch = File(session.directory, "segment.download").apply { writeBytes(byteArrayOf(sequence.toByte(), 2, 3, 4)) }
        session.commit(HlsMediaPlaylist.Segment("https://provider.invalid/private?token=secret", 6.0, sequence, discontinuity = dc), scratch)
    }
    private fun entry(sequence: Long, bytes: Long = 4, dc: Long = 0) =
        HlsCaptureSession.Entry(sequence, 6_000, "segment-$sequence.bin", "a".repeat(64), bytes, dc)

    @Test fun plannerBoundsSizeAndDurationWithoutCuttingSegments() {
        val entries = (0L..10).map { entry(it) }
        val groups = RecordingPartsPlan.groups(entries, maxBytes = 12)
        assertEquals(listOf(3, 3, 3, 2), groups.map { it.size })
        assertEquals(entries, groups.flatten())
        assertEquals(listOf(5, 5, 1), RecordingPartsPlan.groups(entries).map { it.size })
    }
    @Test fun plannerAllowsAggregateAboveFat32LimitButNeverAnOversizedPart() {
        val groups = RecordingPartsPlan.groups((0L..79).map { entry(it, RecordingPartsPlan.MAX_PART_BYTES) })
        assertTrue(groups.sumOf { it.sumOf { part -> part.bytes } } > 0xffffffffL)
        assertTrue(groups.all { it.single().bytes <= RecordingPartsPlan.MAX_PART_BYTES })
        assertTrue(runCatching { RecordingPartsPlan.groups(listOf(entry(0, RecordingPartsPlan.MAX_PART_BYTES + 1))) }.isFailure)
    }
    @Test fun gapsAndDiscontinuitiesStartNewParts() {
        val entries = listOf(entry(1), entry(2), entry(4), entry(5, dc = 1))
        assertEquals(listOf(2, 1, 1), RecordingPartsPlan.groups(entries).map { it.size })
    }
    @Test fun manifestRejectsTraversalInvalidSizesAndUnknownContainers() {
        val index = RecordingPartsPlan.Index("a".repeat(64), "ts", 0,
            listOf(RecordingPartsPlan.Part("part-000000.ts", 4, 6_000, false)))
        assertEquals(index, RecordingPartsPlan.decode(index.encode()))
        val traversal = JSONObject(index.encode()).apply { getJSONArray("parts").getJSONObject(0).put("name", "../secret") }
        assertTrue(runCatching { RecordingPartsPlan.decode(traversal.toString()) }.isFailure)
        val oversized = JSONObject(index.encode()).apply { getJSONArray("parts").getJSONObject(0).put("bytes", RecordingPartsPlan.MAX_PART_BYTES + 1) }
        assertTrue(runCatching { RecordingPartsPlan.decode(oversized.toString()) }.isFailure)
        assertTrue(runCatching { RecordingPartsPlan.decode(JSONObject(index.encode()).put("container", "mp4").toString()) }.isFailure)
    }
    @Test fun tsAssemblyPreservesContentAndOneDurableIndex() = runBlocking {
        val session = session(); (1L..7).forEach { commit(session, it) }
        val target = target()
        val (index, bytes) = RecordingParts.finish(null, target, 1, session) { }
        assertEquals(28L, bytes)
        assertEquals(28L, RecordingParts.confirmedBytes(null, index, 1))
        assertTrue(RecordingParts.isIndex(index))
        val folder = File(index.stored).parentFile
        assertArrayEquals(session.inputs().take(5).flatMap { it.readBytes().toList() }.toByteArray(), File(folder, "part-000000.ts").readBytes())
        val manifest = RecordingPartsPlan.decode(File(index.stored).readText())
        assertEquals(2, manifest.parts.size)
        assertTrue(manifest.playlist().contains("#EXT-X-ENDLIST"))
        assertFalse(manifest.encode().contains("secret"))
        assertFalse(File(folder, "index.pending").exists())
        assertEquals(28L + File(index.stored).length(), RecordingParts.stagedBytes(null, target, 1))
        assertEquals(0L, RecordingParts.stagedBytes(null, index, 1))
        session.discard()
    }
    @Test fun restartReusesConfirmedPackageWithoutRewritingParts() = runBlocking {
        val session = session(); commit(session, 1)
        val target = target()
        val first = RecordingParts.finish(null, target, 1, session) { }
        val restored = HlsCaptureSession(session.directory)
        val second = RecordingParts.finish(null, target, 1, restored) { error("Must not rewrite") }
        assertEquals(first.first.stored, second.first.stored)
        assertEquals(first.second, second.second)
        session.discard()
    }
    @Test fun interruptedAssemblyKeepsSourcesAndRetriesWithoutPublishingPartialIndex() = runBlocking {
        val session = session(); (1L..7).forEach { commit(session, it) }
        val target = target()
        assertTrue(runCatching { RecordingParts.finish(null, target, 1, session) { if (it > 20) error("Disconnected") } }.isFailure)
        val folder = File(target.file.parentFile, ".owntv-parts-1")
        assertFalse(File(folder, RecordingPartsPlan.INDEX_NAME).exists())
        assertTrue(session.inputs().all { it.isFile })
        assertEquals(20L, RecordingParts.stagedBytes(null, target, 1))
        val restored = HlsCaptureSession(session.directory)
        val (index, bytes) = RecordingParts.finish(null, target, 1, restored) { }
        assertEquals(28L, bytes)
        assertTrue(index.exists())
        session.discard()
    }
    @Test fun changedIdentityCannotOverwriteAnAlreadyPublishedPackage() = runBlocking {
        val session = session(); commit(session, 1)
        val target = target()
        val (index, _) = RecordingParts.finish(null, target, 1, session) { }
        val original = File(index.stored).readText()
        commit(session, 2)
        assertTrue(runCatching { RecordingParts.finish(null, target, 1, session) { } }.isFailure)
        assertEquals(original, File(index.stored).readText())
        session.discard()
    }
    @Test fun sourceDamageRefusesPublicationAndPreservesCapture() = runBlocking {
        val session = session(); commit(session, 1)
        session.inputs().single().writeBytes(byteArrayOf(9, 2, 3, 4))
        val target = target()
        assertTrue(runCatching { RecordingParts.finish(null, target, 1, session) { } }.isFailure)
        assertFalse(File(target.file.parentFile, ".owntv-parts-1/${RecordingPartsPlan.INDEX_NAME}").exists())
        assertTrue(session.inputs().single().exists())
        assertEquals(4L, RecordingParts.stagedBytes(null, target, 1))
        session.discard()
    }
    @Test fun fragmentedMp4HasSeparateInitializationAndContinuousVodPlaylist() = runBlocking {
        val session = session("fmp4")
        val init = File(session.directory, "init.download").apply { writeBytes(byteArrayOf(8, 9)) }
        session.commitInit(init); commit(session, 1); commit(session, 2, 1)
        val (index, bytes) = RecordingParts.finish(null, target(), 1, session) { }
        val manifest = RecordingPartsPlan.decode(File(index.stored).readText())
        assertEquals(10L, bytes)
        assertTrue(manifest.playlist().contains("#EXT-X-MAP:URI=\"-1.mp4\""))
        assertTrue(manifest.playlist().contains("#EXT-X-DISCONTINUITY"))
        assertArrayEquals(byteArrayOf(8, 9), File(File(index.stored).parentFile, "init.mp4").readBytes())
        session.discard()
    }
    @Test fun localPlaybackServesAllPartsRangesAndTracksReaders() = runBlocking {
        val session = session(); (1L..7).forEach { commit(session, it) }
        val (index, _) = RecordingParts.finish(null, target(), 1, session) { }
        val readers = AtomicInteger()
        val playback = RecordingParts.openPlayback(null, index, readers)
        val client = OkHttpClient()
        try {
            assertEquals(1, readers.get())
            client.newCall(Request.Builder().url(playback.url).build()).execute().use {
                assertEquals(200, it.code); assertTrue(it.body.string().contains("#EXT-X-PLAYLIST-TYPE:VOD"))
            }
            for ((id, size) in listOf(0 to 20, 1 to 8)) {
                client.newCall(Request.Builder().url(playback.url.replace("index.m3u8", "$id.ts")).build()).execute().use {
                    assertEquals(200, it.code); assertEquals(size, it.body.bytes().size)
                }
            }
            client.newCall(Request.Builder().url(playback.url.replace("index.m3u8", "1.ts")).header("Range", "bytes=0-3").build()).execute().use {
                assertEquals(206, it.code); assertArrayEquals(byteArrayOf(6, 2, 3, 4), it.body.bytes())
            }
        } finally {
            playback.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
        val deadline = System.nanoTime() + 2_000_000_000L
        while (readers.get() != 0 && System.nanoTime() < deadline) Thread.sleep(10)
        assertEquals(0, readers.get())
        session.discard()
    }
    @Test fun missingPartRefusesPlaybackInsteadOfOpeningIncompleteMedia() = runBlocking {
        val session = session(); commit(session, 1)
        val (index, _) = RecordingParts.finish(null, target(), 1, session) { }
        File(File(index.stored).parentFile, "part-000000.ts").delete()
        val readers = AtomicInteger()
        assertTrue(runCatching { RecordingParts.openPlayback(null, index, readers) }.isFailure)
        assertEquals(0, readers.get())
        session.discard()
    }
    @Test fun deletionHandlesPartialPackagesMissingIndexAndRetries() = runBlocking {
        val session = session(); commit(session, 1)
        val target = target()
        val (index, _) = RecordingParts.finish(null, target, 1, session) { }
        File(index.stored).delete()
        File(File(index.stored).parentFile, "index.pending").writeText("interrupted")
        assertTrue(RecordingParts.delete(null, index, 1))
        assertFalse(File(index.stored).parentFile!!.exists())
        assertTrue(RecordingParts.delete(null, index, 1))
        assertTrue(RecordingParts.delete(null, target, 1))
        session.discard()
    }
    @Test fun deletionRefusesForeignContentsAndWrongRecordingIdentity() = runBlocking {
        val session = session(); commit(session, 1)
        val (index, _) = RecordingParts.finish(null, target(), 1, session) { }
        val foreign = File(File(index.stored).parentFile, "keep.txt").apply { writeText("preserve") }
        assertFalse(RecordingParts.delete(null, index, 1))
        assertTrue(index.exists()); assertTrue(foreign.exists())
        assertTrue(runCatching { RecordingParts.delete(null, index, 2) }.isFailure)
        foreign.delete()
        assertTrue(RecordingParts.delete(null, index, 1))
        session.discard()
    }
}

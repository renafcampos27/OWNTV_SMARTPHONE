package tv.own.owntv.core.timeshift

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LocalSegmentStoreTest {
    private fun store(bytes: Long = 400, duration: Long = 10_000, free: Long = Long.MAX_VALUE) =
        LocalSegmentStore(Files.createTempDirectory("timeshift-test").toFile(), bytes, duration, 0) { free }
    private fun append(store: LocalSegmentStore, value: Int = 1, discontinuity: Boolean = false): LocalSegmentStore.Segment {
        val file = File(store.directory, "incoming.part")
        file.writeBytes(ByteArray(188) { value.toByte() })
        return store.commit(file, 5_000, discontinuity)
    }
    @Test fun evictionKeepsOnlyConfirmedWindow() {
        val store = store()
        try {
            append(store); append(store); append(store)
            assertEquals(listOf(1L, 2L), store.snapshot().map { it.id })
            assertEquals(376L, store.bytesOnDisk())
            assertTrue(store.playlist().contains("#EXT-X-MEDIA-SEQUENCE:1"))
            assertFalse(store.playlist().contains("0.ts"))
        } finally { store.close() }
    }
    @Test fun readerSurvivesEvictionUntilClose() {
        val store = store()
        try {
            val first = append(store, 42)
            val reader = store.open(first.id)!!
            append(store); append(store)
            assertNull(store.open(first.id))
            assertEquals(42, reader.input.read())
            assertTrue(File(store.directory, "0.ts").exists())
            reader.close()
            assertFalse(File(store.directory, "0.ts").exists())
            assertTrue(store.bytesOnDisk() <= store.maxBytes)
        } finally { store.close() }
    }
    @Test fun pinnedFilesPreventExceedingQuota() {
        val store = store(300)
        try {
            val reader = store.open(append(store).id)!!
            try { store.prepareIncoming(188); fail() } catch (_: IllegalStateException) { }
            assertEquals(188L, store.bytesOnDisk())
            reader.close()
            store.prepareIncoming(188)
        } finally { store.close() }
    }
    @Test fun closePreservesPinnedReaderAndDeletesAfterRelease() {
        val store = store()
        val reader = store.open(append(store).id)!!
        store.close()
        assertNotNull(reader.input.read())
        assertNull(store.open(0))
        reader.close(); reader.close()
        assertFalse(store.directory.exists())
    }
    @Test fun incompleteFileNeverAppearsInPlaylist() {
        val store = store()
        try {
            append(store)
            File(store.directory, "incoming.part").writeBytes(byteArrayOf(1))
            assertEquals(1, store.snapshot().size)
            assertFalse(store.playlist().contains("incoming"))
        } finally { store.close() }
    }
    @Test fun durationCapAndDiscontinuitySequenceAreStable() {
        val store = store(2000, 10_000)
        try {
            append(store, discontinuity = true); append(store); append(store)
            assertEquals(2, store.snapshot().size)
            assertTrue(store.playlist().contains("#EXT-X-DISCONTINUITY-SEQUENCE:1"))
        } finally { store.close() }
    }
    @Test fun diskReserveRejectsNewDownload() {
        val store = store(free = 10)
        try { store.prepareIncoming(188); fail() } catch (_: IllegalStateException) { }
        finally { store.close() }
    }
    @Test fun progressiveReservationCountsWrittenTemporaryBytesOnlyOnce() {
        val store = store(free = 40)
        try {
            store.prepareIncoming(100, writtenBytes = 60)
            try { store.prepareIncoming(101, writtenBytes = 60); fail() } catch (_: IllegalStateException) { }
        } finally { store.close() }
    }
    @Test fun smallIncomingReservationPreservesHistoryButGrowthStillHonorsPinnedQuota() {
        val store = store(400)
        try {
            val reader = store.open(append(store).id)!!
            append(store)
            store.prepareIncoming(20)
            assertEquals(2, store.snapshot().size)
            try { store.prepareIncoming(300, writtenBytes = 20); fail() } catch (_: IllegalStateException) { }
            assertEquals(188L, store.bytesOnDisk())
            assertEquals(1, reader.input.read())
            reader.close()
        } finally { store.close() }
    }
    @Test fun outsideDirectoryCannotBeCommitted() {
        val store = store()
        val outside = File.createTempFile("outside", ".part")
        outside.writeBytes(byteArrayOf(1))
        try { store.commit(outside, 1, false); fail() } catch (_: IllegalArgumentException) { }
        finally { store.close(); outside.delete() }
    }
}

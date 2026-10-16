package tv.own.owntv.mobile.backup

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class BackupTransferFilesTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun cancellationWhileAcquiringTheDirectoryDoesNotOrphanIt() = runBlocking {
        var staging: File? = null
        val worker = launch {
            val owner = currentCoroutineContext()
            val files = BackupTransferFiles(temp.root, "session-current") { directory ->
                File(directory, "transfer-cancelled").apply { mkdirs() }.also { staging = it; owner.cancel() }
            }
            files.use { fail("A cancelled caller must not receive the directory") }
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertNotNull(staging)
        assertFalse(staging!!.exists())
    }
    @Test fun successAndReadFailureBothRemoveOnlyTheirOwnTemporaryDirectory() = runBlocking {
        val files = BackupTransferFiles(temp.root, "session-current")
        var staging: File? = null
        files.use { staging = it; File(it, "backup.own").writeText("data") }
        assertFalse(staging!!.exists())
        try { files.use { staging = it; File(it, "partial").writeText("partial"); throw IOException() } }
        catch (_: IOException) { }
        assertFalse(staging!!.exists())
    }
    @Test fun cancellationStopsCopyAndCleansUpEvenWithACancelledJob() = runBlocking {
        val files = BackupTransferFiles(temp.root, "session-current")
        var staging: File? = null
        var copied = 0
        val worker = launch {
            files.use { directory ->
                staging = directory
                val context = currentCoroutineContext()
                val input = object : ByteArrayInputStream(ByteArray(512 * 1024)) {
                    override fun read(buffer: ByteArray, off: Int, len: Int): Int {
                        val count = super.read(buffer, off, len)
                        copied += count.coerceAtLeast(0)
                        context.cancel()
                        return count
                    }
                }
                File(directory, "partial.own").outputStream().use { out -> input.use { copyBackupBytes(it, out) } }
            }
        }
        worker.join()
        assertTrue(worker.isCancelled)
        assertTrue(copied in 1..(128 * 1024))
        assertFalse(staging!!.exists())
    }
    @Test fun restartClearsAbandonedSecretsButPreservesThisProcessAndUnrelatedFiles() = runBlocking {
        val old = File(temp.root, "session-old/transfer-old").apply { mkdirs() }
        File(old, "backup.own").writeText("sensitive")
        val unrelated = File(temp.root, "unrelated").apply { writeText("keep") }
        val files = BackupTransferFiles(temp.root, "session-current")
        val active = files.create()
        File(active, "import.own").writeText("waiting for password")
        files.prepare()
        assertFalse(old.exists()); assertTrue(unrelated.exists()); assertTrue(active.exists())
        files.delete(active)
    }
    @Test fun cleanupCannotEscapeTheGeneratedDirectory() = runBlocking {
        val files = BackupTransferFiles(temp.root, "session-current")
        val outside = File(temp.root, "keep").apply { writeText("original") }
        try { files.delete(outside); fail("Expected a scope check") } catch (_: IllegalArgumentException) { }
        assertEquals("original", outside.readText())
    }
    @Test fun operationAlwaysReleasesBusyAndCancellationIsNotAnOrdinaryError() = runBlocking {
        var busy = true; var failures = 0
        runBackupOperation({ failures++ }, { busy = false }) { throw IOException() }
        assertFalse(busy); assertEquals(1, failures)
        busy = true
        try { runBackupOperation({ failures++ }, { busy = false }) { throw CancellationException() } }
        catch (_: CancellationException) { }
        assertFalse(busy); assertEquals(1, failures)
    }
    @Test fun bytesAreCopiedWithoutChangingTheInput() = runBlocking {
        val bytes = ByteArray(400_000) { (it % 251).toByte() }
        val out = ByteArrayOutputStream()
        copyBackupBytes(ByteArrayInputStream(bytes), out)
        assertArrayEquals(bytes, out.toByteArray())
    }
}

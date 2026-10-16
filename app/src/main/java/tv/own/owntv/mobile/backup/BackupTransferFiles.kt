package tv.own.owntv.mobile.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.UUID

/** Each process owns a separate cache root; restart removes abandoned transfers, never user files. */
internal class BackupTransferFiles(
    private val root: File,
    private val session: String = PROCESS_SESSION,
    private val createDirectory: (File) -> File = { Files.createTempDirectory(it.toPath(), "transfer-").toFile() },
) {
    init { require(session.matches(Regex("session-[A-Za-z0-9-]+"))) }
    private val directory get() = File(root, session)

    suspend fun prepare() = withContext(Dispatchers.IO) {
        startupMutex.withLock {
            check(root.mkdirs() || root.isDirectory)
            root.listFiles()?.filter { it.name.startsWith("session-") && it.name != session }?.forEach {
                require(it.canonicalFile.parentFile == root.canonicalFile)
                check(it.deleteRecursively())
            }
            check(directory.mkdirs() || directory.isDirectory)
        }
    }

    suspend fun create(): File {
        var created: File? = null
        try {
            return withContext(Dispatchers.IO) {
                prepare()
                createDirectory(directory).also { created = it }
            }
        } catch (failure: Throwable) {
            // withContext can discard an acquired resource when its caller is cancelled on return.
            try { created?.let { delete(it) } } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    suspend fun delete(file: File) = withContext(NonCancellable + Dispatchers.IO) {
        // All deletions are scoped to this process's generated transfer directories.
        require(file.canonicalFile.parentFile == directory.canonicalFile && file.name.startsWith("transfer-"))
        check(!file.exists() || file.deleteRecursively() || !file.exists())
    }

    suspend fun <T> use(block: suspend (File) -> T): T {
        val file = create()
        try { return block(file) } finally { delete(file) }
    }

    companion object {
        private val PROCESS_SESSION = "session-${UUID.randomUUID()}"
        private val startupMutex = Mutex()
    }
}

internal suspend fun runBackupOperation(
    onFailure: (Exception) -> Unit,
    onFinished: () -> Unit,
    block: suspend () -> Unit,
) {
    try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (failure: Exception) { onFailure(failure) }
    finally { onFinished() }
}

/** Check cancellation between chunks; closing streams remains the caller's responsibility. */
internal suspend fun copyBackupBytes(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(128 * 1024)
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
    }
    currentCoroutineContext().ensureActive()
    output.flush()
}

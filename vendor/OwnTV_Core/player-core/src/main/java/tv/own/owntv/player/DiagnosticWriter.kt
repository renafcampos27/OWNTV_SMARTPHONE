package tv.own.owntv.player

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

/** One bounded, non-blocking producer queue. Only the worker touches the destination. */
internal class DiagnosticWriter(
    capacity: Int = 512,
    private val write: (List<String>) -> Unit,
) {
    private val queue = ArrayBlockingQueue<String>(capacity)
    val dropped = AtomicLong()
    val failures = AtomicLong()
    private val worker = Thread({
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val batch = ArrayList<String>(8)
                    batch.add(queue.take())
                    queue.drainTo(batch, 7)
                    try { write(batch) } catch (_: Exception) { failures.incrementAndGet() }
                } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            }
        }, "owntv-diagnostics").apply { isDaemon = true; start() }
    fun close() { worker.interrupt() }
    fun offer(line: String) {
        if (!queue.offer(line)) dropped.incrementAndGet()
    }
}

/** Called only by the single writer; retains a bounded tail on rotation, including UTF-8 lines. */
internal fun appendDiagnosticBatch(file: File, lines: List<String>, maxBytes: Int) {
    require(maxBytes >= 1024)
    file.parentFile?.mkdirs()
    // Bound even one exceptionally long event and an entire batch.
    val incoming = lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
    val data = completeDiagnosticTail(incoming, maxBytes / 2)
    if (file.length() + data.size > maxBytes) {
        val tail = if (file.exists()) RandomAccessFile(file, "r").use { input ->
            val count = minOf(input.length(), (maxBytes / 2).toLong()).toInt()
            input.seek(input.length() - count)
            ByteArray(count).also { input.readFully(it) }
        } else byteArrayOf()
        file.writeBytes(completeDiagnosticTail(tail, maxBytes / 2, discardFirstLine = true))
    }
    file.appendBytes(data)
}

private fun completeDiagnosticTail(bytes: ByteArray, limit: Int, discardFirstLine: Boolean = false): ByteArray {
    if (bytes.size <= limit && !discardFirstLine) return bytes
    val start = (bytes.size - limit).coerceAtLeast(0)
    val newline = (start until bytes.size).firstOrNull { bytes[it] == 10.toByte() } ?: return byteArrayOf()
    return bytes.copyOfRange(newline + 1, bytes.size)
}


/** Bounded persisted history, including after process restart; never reads the whole file. */
internal fun readDiagnosticTail(file: File, limit: Int): String {
    require(limit > 0)
    if (!file.exists()) return ""
    return RandomAccessFile(file, "r").use { input ->
        val count = minOf(input.length(), limit.toLong()).toInt()
        val truncated = input.length() > count
        input.seek(input.length() - count)
        val bytes = ByteArray(count).also { input.readFully(it) }
        val start = if (truncated) bytes.indexOf(10.toByte()).let { if (it < 0) bytes.size else it + 1 } else 0
        bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
    }
}

package tv.own.owntv.core.timeshift

import kotlinx.coroutines.*
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.UUID

/** Private loopback endpoint. No upstream requests, paths or credentials are exposed to readers. */
class LocalTimeshiftServer(
    private val playlist: () -> String,
    private val open: (Long) -> LocalSegmentStore.Reader?,
) : Closeable {
    constructor(store: LocalSegmentStore) : this(store::playlist, store::open)
    private val socket = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val owner = SupervisorJob()
    private val scope = CoroutineScope(owner + Dispatchers.IO)
    private val token = UUID.randomUUID().toString()
    private val clients = Collections.newSetFromMap(ConcurrentHashMap<Socket, Boolean>())
    private val slots = Semaphore(4)
    val url: String = "http://127.0.0.1:${socket.localPort}/$token/index.m3u8"
    init {
        scope.launch {
            try {
                while (isActive) {
                    val client = socket.accept()
                    if (!slots.tryAcquire()) { client.close(); continue }
                    if (!isActive) { client.close(); slots.release(); break }
                    clients.add(client)
                    val task = launch {
                        try { client.use { serve(it) } } catch (_: Exception) { }
                        finally { if (clients.remove(client)) slots.release() }
                    }
                    task.invokeOnCompletion { client.close(); if (clients.remove(client)) slots.release() }
                }
            } catch (_: Exception) { }
        }
    }
    private fun serve(client: Socket) {
        client.soTimeout = 5_000
        val input = client.getInputStream().bufferedReader(Charsets.US_ASCII)
        val line = boundedLine(input)?.takeIf { it.length <= 4096 } ?: return
        val words = line.split(' ')
        if (words.size != 3 || words[0] !in listOf("GET", "HEAD")) return
        // Fixed bounded headers; close every connection so no request can outlive its file lease.
        var range: String? = null
        var headerBytes = 0
        var ended = false
        for (i in 0 until 40) {
            val header = boundedLine(input) ?: return
            headerBytes += header.length
            if (headerBytes > 8192) return
            if (header.isEmpty()) { ended = true; break }
            if (header.startsWith("Range:", true)) range = header.substringAfter(':').trim()
        }
        if (!ended) return
        val path = words[1].substringBefore('?')
        val name = path.removePrefix("/$token/")
        val output = client.getOutputStream()
        fun headers(code: Int, type: String, size: Long, extra: String = "") {
            val status = when (code) { 200 -> "OK"; 206 -> "Partial Content"; 416 -> "Range Not Satisfiable"; else -> "Not Found" }
            output.write("HTTP/1.1 $code $status\r\nContent-Type: $type\r\nContent-Length: $size\r\nCache-Control: no-store\r\nConnection: close\r\n$extra\r\n".toByteArray(Charsets.US_ASCII))
        }
        if (!path.startsWith("/$token/")) { headers(404, "text/plain", 0); return }
        if (name == "index.m3u8") {
            val bytes = playlist().toByteArray(Charsets.UTF_8)
            headers(200, "application/vnd.apple.mpegurl", bytes.size.toLong())
            if (words[0] != "HEAD") output.write(bytes)
            return
        }
        val id = name.substringBeforeLast('.').takeIf { name.substringAfterLast('.') in listOf("ts", "m4s", "mp4") }?.toLongOrNull()
        val reader = id?.let(open)
        if (reader == null) { headers(404, "text/plain", 0); return }
        reader.use {
            val bounds = byteRange(range, reader.size)
            if (bounds == null) { headers(416, reader.type, 0, "Content-Range: bytes */${reader.size}\r\n"); return }
            val (first, last) = bounds
            headers(if (range == null) 200 else 206, reader.type, last - first + 1,
                if (range == null) "" else "Content-Range: bytes $first-$last/${reader.size}\r\n")
            if (words[0] == "HEAD") return
            reader.input.channel.position(first)
            val bytes = ByteArray(64 * 1024)
            var remaining = last - first + 1
            while (remaining > 0) {
                val n = reader.input.read(bytes, 0, minOf(bytes.size.toLong(), remaining).toInt())
                if (n < 0) return
                output.write(bytes, 0, n); remaining -= n
            }
        }
    }
    private fun boundedLine(input: java.io.Reader): String? {
        val text = StringBuilder()
        while (text.length <= 4096) {
            val c = input.read()
            if (c < 0) return null
            if (c == 10) return text.toString().removeSuffix("\r")
            text.append(c.toChar())
        }
        return null
    }
    suspend fun closeAndAwait() { close(); owner.join() }
    override fun close() {
        socket.close()
        scope.cancel()
        clients.forEach { runCatching { it.close() } }
    }
    companion object {
        internal fun byteRange(header: String?, size: Long): Pair<Long, Long>? {
            if (size <= 0) return null
            if (header == null) return 0L to size - 1
            if (!header.startsWith("bytes=") || ',' in header) return null
            val parts = header.removePrefix("bytes=").split('-')
            if (parts.size != 2) return null
            if (parts[0].isEmpty()) {
                val tail = parts[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
                return (size - tail).coerceAtLeast(0) to size - 1
            }
            val first = parts[0].toLongOrNull()?.takeIf { it in 0 until size } ?: return null
            val last = if (parts[1].isEmpty()) size - 1 else parts[1].toLongOrNull() ?: return null
            return if (last < first) null else first to minOf(last, size - 1)
        }
    }
}

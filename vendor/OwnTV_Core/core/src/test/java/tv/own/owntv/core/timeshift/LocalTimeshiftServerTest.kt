package tv.own.owntv.core.timeshift

import org.junit.Assert.*
import org.junit.Test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.file.Files

class LocalTimeshiftServerTest {
    @Test fun byteRangesAreBounded() {
        assertEquals(0L to 99L, LocalTimeshiftServer.byteRange(null, 100))
        assertEquals(20L to 99L, LocalTimeshiftServer.byteRange("bytes=20-", 100))
        assertEquals(95L to 99L, LocalTimeshiftServer.byteRange("bytes=-5", 100))
        assertEquals(0L to 99L, LocalTimeshiftServer.byteRange("bytes=0-1000", 100))
        listOf("bytes=100-", "bytes=2-1", "bytes=0-1,3-4", "bytes=-0", "wrong").forEach {
            assertNull(LocalTimeshiftServer.byteRange(it, 100))
        }
    }
    @Test fun invalidSegmentNeverMakesTheSessionReady() = runBlocking {
        val source = LocalSegmentStore(Files.createTempDirectory("timeshift-invalid").toFile())
        repeat(3) { index ->
            val part = File(source.directory, "incoming.part")
            val bytes = ByteArray(188)
            if (index < 2) bytes[0] = 0x47
            part.writeBytes(bytes); source.commit(part, 5_000, false)
        }
        val upstream = LocalTimeshiftServer(source)
        val destination = Files.createTempDirectory("timeshift-invalid-target").toFile()
        val session = LocalTimeshiftSession(destination, OkHttpClient(), upstream.url, emptyMap(), reserveBytes = 0)
        try {
            try { session.awaitReady(); fail() } catch (_: IllegalStateException) { }
            assertEquals(LocalTimeshiftSession.Status.FAILED, session.status.value)
            assertEquals(10_000L, session.retainedDurationMs())
        } finally { session.close(); upstream.closeAndAwait(); source.close() }
        assertFalse(destination.exists())
    }

    @Test fun cancellationClosesBlockedUpstreamReadBeforeReturning() = runBlocking {
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        val responseStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val peer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async {
            server.accept().use { socket ->
                socket.soTimeout = 5000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n\r\n#EXTM3U\n".toByteArray()); flush()
                }
                responseStarted.complete(Unit)
                assertEquals(-1, socket.getInputStream().read())
            }
        }
        val destination = Files.createTempDirectory("timeshift-cancel").toFile()
        val session = LocalTimeshiftSession(destination, OkHttpClient(), "http://127.0.0.1:${server.localPort}/index.m3u8", emptyMap(), reserveBytes = 0)
        try {
            kotlinx.coroutines.withTimeout(5000) { responseStarted.await() }
            kotlinx.coroutines.withTimeout(5000) { session.close(); peer.await() }
            assertEquals(LocalTimeshiftSession.Status.CLOSED, session.status.value)
            assertFalse(destination.exists())
        } finally { session.close(); server.close(); peer.cancel() }
    }

    @Test fun producerServesCompleteLocalSegmentsAndCloses() = runBlocking {
        val source = LocalSegmentStore(Files.createTempDirectory("timeshift-source").toFile())
        repeat(3) {
            val part = File(source.directory, "incoming.part")
            val bytes = ByteArray(564)
            bytes[0] = 0x47; bytes[188] = 0x47; bytes[376] = 0x47
            part.writeBytes(bytes)
            source.commit(part, 5_000, false)
        }
        val upstream = LocalTimeshiftServer(source)
        val client = OkHttpClient()
        val destination = Files.createTempDirectory("timeshift-target").toFile()
        val session = LocalTimeshiftSession(destination, client, upstream.url, emptyMap(), reserveBytes = 0)
        try {
            session.awaitReady()
            assertEquals(15_000L, session.retainedDurationMs())
            assertEquals(1692L, session.bytesOnDisk())
            fun fetch(url: String) = client.newCall(Request.Builder().url(url).build()).execute()
            fetch(session.url).use { response ->
                assertEquals(200, response.code)
                val manifest = response.body.string()
                assertTrue(manifest.contains("2.ts"))
                assertFalse(manifest.contains(upstream.url))
            }
            val segmentUrl = session.url.replace("index.m3u8", "0.ts")
            client.newCall(Request.Builder().url(segmentUrl).header("Range", "bytes=188-375").build()).execute().use {
                assertEquals(206, it.code)
                assertEquals(188, it.body.bytes().size)
            }
            fetch(session.url.replace("index.m3u8", "../incoming.part")).use { assertEquals(404, it.code) }
            fetch(session.url.replace("index.m3u8", "999.ts")).use { assertEquals(404, it.code) }
        } finally {
            session.close()
            upstream.closeAndAwait(); source.close()
            client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll()
        }
        assertFalse(destination.exists())
        assertEquals(LocalTimeshiftSession.Status.CLOSED, session.status.value)
    }
}

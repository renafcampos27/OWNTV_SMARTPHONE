package tv.own.owntv.core.timeshift

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class LocalTimeshiftUpstreamTest {
    private val transport = ByteArray(564).also { it[0] = 0x47; it[188] = 0x47; it[376] = 0x47 }
    private fun manifest(reference: String, range: String = "") =
        "#EXTM3U\n#EXT-X-TARGETDURATION:60\n#EXT-X-MEDIA-SEQUENCE:12\n#EXTINF:60,\n$range$reference\n#EXT-X-ENDLIST\n"
    private class Upstream(handler: (HttpExchange) -> Unit) : AutoCloseable {
        private val executor = Executors.newCachedThreadPool()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@Upstream.executor
            createContext("/") { exchange -> exchange.use { handler(it) } }
            start()
        }
        val origin = "http://127.0.0.1:${server.address.port}"
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
    private fun HttpExchange.reply(code: Int, bytes: ByteArray) {
        responseHeaders.add("Content-Type", if (requestURI.path.endsWith(".ts")) "video/mp2t" else "application/vnd.apple.mpegurl")
        sendResponseHeaders(code, bytes.size.toLong())
        responseBody.write(bytes)
    }
    private suspend fun ended(session: LocalTimeshiftSession) = withTimeout(5000) {
        session.status.first { it == LocalTimeshiftSession.Status.ENDED || it == LocalTimeshiftSession.Status.FAILED }
    }
    @Test fun transientSegmentFailureRetriesItsSequenceWithRenewedSignedReferencesAndCooldown() = runBlocking {
        val origins = AtomicInteger()
        val attempts = Collections.synchronizedList(mutableListOf<Pair<String, Long>>())
        val identities = Collections.synchronizedList(mutableListOf<String?>())
        Upstream { exchange ->
            identities.add(exchange.requestHeaders.getFirst("X-Source"))
            when (exchange.requestURI.path) {
                "/start" -> {
                    val key = origins.incrementAndGet()
                    exchange.responseHeaders.add("Location", "/master.m3u8?key=$key")
                    exchange.reply(302, byteArrayOf(0))
                }
                "/master.m3u8" -> exchange.reply(200,
                    "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1000,CODECS=\"avc1.4d401f,mp4a.40.2\"\n/cdn/index.m3u8?${exchange.requestURI.rawQuery}\n".toByteArray())
                "/cdn/index.m3u8" -> exchange.reply(200, manifest("segment.ts?${exchange.requestURI.rawQuery}").toByteArray())
                "/cdn/segment.ts" -> {
                    attempts.add(exchange.requestURI.toString() to System.nanoTime())
                    if (attempts.size == 1) {
                        exchange.responseHeaders.add("Retry-After", "1")
                        exchange.reply(503, byteArrayOf(0))
                    } else exchange.reply(200, transport)
                }
                else -> exchange.reply(404, byteArrayOf(0))
            }
        }.use { upstream ->
            val directory = Files.createTempDirectory("timeshift-signed").toFile()
            val session = LocalTimeshiftSession(directory, OkHttpClient(), "${upstream.origin}/start", mapOf("X-Source" to "test-owner"), reserveBytes = 0)
            try {
                session.awaitReady()
                assertEquals(LocalTimeshiftSession.Status.ENDED, ended(session))
                assertEquals(2, origins.get())
                assertEquals(2, attempts.size)
                assertTrue(identities.all { it == "test-owner" })
                assertNotEquals(attempts[0].first, attempts[1].first)
                assertTrue("Provider cooldown must precede the next upstream retry", attempts[1].second - attempts[0].second >= 950_000_000L)
                assertEquals(60_000L, session.retainedDurationMs())
                assertEquals(transport.size.toLong(), session.bytesOnDisk())
                OkHttpClient().newCall(Request.Builder().url(session.url).build()).execute().use {
                    assertTrue(it.body.string().contains("#EXT-X-ENDLIST"))
                }
            } finally { session.close() }
            assertFalse(directory.exists())
        }
    }
    @Test fun longProviderWaitTerminatesWithoutClampingOrSendingAnotherRequest() = runBlocking {
        val requests = AtomicInteger()
        Upstream { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Retry-After", "120")
            exchange.reply(429, byteArrayOf(0))
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-cooldown").toFile(), OkHttpClient(), upstream.origin, emptyMap(), reserveBytes = 0)
            try {
                try { session.awaitReady(); fail() } catch (_: java.io.IOException) { }
                assertEquals(LocalTimeshiftSession.Status.FAILED, session.status.value)
                assertEquals(1, requests.get())
            } finally { session.close() }
        }
    }
    @Test fun endListAfterAWindowShorterThanStartupThresholdStillPublishesTheTail() = runBlocking {
        val refreshes = AtomicInteger()
        Upstream { exchange ->
            if (exchange.requestURI.path == "/index.m3u8") {
                val end = if (refreshes.incrementAndGet() > 1) "#EXT-X-ENDLIST\n" else ""
                exchange.reply(200, ("#EXTM3U\n#EXT-X-TARGETDURATION:1\n#EXTINF:1,\na.ts\n#EXTINF:1,\nb.ts\n" + end).toByteArray())
            } else exchange.reply(200, transport)
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-short-tail").toFile(), OkHttpClient(), "${upstream.origin}/index.m3u8", emptyMap(), reserveBytes = 0)
            try {
                session.awaitReady()
                assertEquals(LocalTimeshiftSession.Status.ENDED, ended(session))
                assertEquals(2000L, session.retainedDurationMs())
            } finally { session.close() }
        }
    }
    @Test fun authenticationAndBandwidthRefusalsAreNotRetriedAsTransportFailures() = runBlocking {
        for (code in listOf(401, 402, 458, 509)) {
            val requests = AtomicInteger()
            Upstream { exchange -> requests.incrementAndGet(); exchange.reply(code, byteArrayOf(0)) }.use { upstream ->
                val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-refusal").toFile(), OkHttpClient(), upstream.origin, emptyMap(), reserveBytes = 0)
                try {
                    try { session.awaitReady(); fail() } catch (_: java.io.IOException) { }
                    assertEquals(1, requests.get())
                    assertEquals(LocalTimeshiftSession.Status.FAILED, session.status.value)
                } finally { session.close() }
            }
        }
    }
    @Test fun validTransportBytesWithTheWrongContentRangeAreNeverCommitted() = runBlocking {
        var receivedRange: String? = null
        Upstream { exchange ->
            if (exchange.requestURI.path == "/index.m3u8") exchange.reply(200, manifest("segment.ts", "#EXT-X-BYTERANGE:188@188\n").toByteArray())
            else {
                receivedRange = exchange.requestHeaders.getFirst("Range")
                exchange.responseHeaders.add("Content-Range", "bytes 0-187/564")
                exchange.reply(206, transport.copyOf(188))
            }
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-range").toFile(), OkHttpClient(), "${upstream.origin}/index.m3u8", emptyMap(), reserveBytes = 0)
            try {
                try { session.awaitReady(); fail() } catch (_: IllegalStateException) { }
                assertEquals("bytes=188-375", receivedRange)
                assertEquals(0L, session.bytesOnDisk())
                assertEquals(LocalTimeshiftSession.Status.FAILED, session.status.value)
            } finally { session.close() }
        }
    }
    @Test fun repeatedSegmentFailureStopsAtThreeAttemptsWithoutAdvancingIntoTheTail() = runBlocking {
        val requests = AtomicInteger()
        Upstream { exchange ->
            if (exchange.requestURI.path == "/index.m3u8") exchange.reply(200, manifest("segment.ts").toByteArray())
            else {
                requests.incrementAndGet()
                exchange.responseHeaders.add("Retry-After", "0")
                exchange.reply(503, byteArrayOf(0))
            }
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-retry-bound").toFile(), OkHttpClient(), "${upstream.origin}/index.m3u8", emptyMap(), reserveBytes = 0)
            try {
                try { session.awaitReady(); fail() } catch (_: java.io.IOException) { }
                assertEquals(3, requests.get())
                assertEquals(0L, session.retainedDurationMs())
            } finally { session.close() }
        }
    }
    @Test fun expiredSequenceBecomesADiscontinuityAndTheRefreshedTailIsCapturedOnce() = runBlocking {
        val refreshes = AtomicInteger()
        val expiredRequests = AtomicInteger()
        val newRequests = AtomicInteger()
        Upstream { exchange ->
            when (exchange.requestURI.path) {
                "/index.m3u8" -> {
                    val text = if (refreshes.incrementAndGet() == 1) manifest("old.ts").replace("#EXT-X-ENDLIST\n", "")
                        else manifest("new.ts").replace("MEDIA-SEQUENCE:12", "MEDIA-SEQUENCE:13")
                    exchange.reply(200, text.toByteArray())
                }
                "/old.ts" -> { expiredRequests.incrementAndGet(); exchange.responseHeaders.add("Retry-After", "0"); exchange.reply(404, byteArrayOf(0)) }
                else -> { newRequests.incrementAndGet(); exchange.reply(200, transport) }
            }
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-expired").toFile(), OkHttpClient(), "${upstream.origin}/index.m3u8", emptyMap(), reserveBytes = 0)
            try {
                withTimeout(3000) { session.awaitReady() }
                assertEquals(LocalTimeshiftSession.Status.ENDED, ended(session))
                assertEquals(1, expiredRequests.get())
                assertEquals(1, newRequests.get())
                assertEquals(60_000L, session.retainedDurationMs())
                OkHttpClient().newCall(Request.Builder().url(session.url).build()).execute().use {
                    assertTrue(it.body.string().contains("#EXT-X-DISCONTINUITY\n"))
                }
            } finally { session.close() }
        }
    }
    @Test fun closingDuringProviderWaitCancelsWithoutAnotherUpstreamRequest() = runBlocking {
        val responseSent = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        Upstream { exchange ->
            requests.incrementAndGet()
            exchange.responseHeaders.add("Retry-After", "30")
            exchange.reply(503, byteArrayOf(0))
            responseSent.complete(Unit)
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-cancel-wait").toFile(), OkHttpClient(), upstream.origin, emptyMap(), reserveBytes = 0)
            try {
                withTimeout(3000) { responseSent.await() }
                withTimeout(2000) { session.close() }
                assertEquals(1, requests.get())
                assertEquals(LocalTimeshiftSession.Status.CLOSED, session.status.value)
            } finally { session.close() }
        }
    }
    @Test fun sequenceOverflowFailsBeforeItCanCreateAHotCommitLoop() = runBlocking {
        val segments = AtomicInteger()
        Upstream { exchange ->
            if (exchange.requestURI.path == "/index.m3u8") exchange.reply(200,
                manifest("segment.ts").replace("MEDIA-SEQUENCE:12", "MEDIA-SEQUENCE:${Long.MAX_VALUE}").toByteArray())
            else { segments.incrementAndGet(); exchange.reply(200, transport) }
        }.use { upstream ->
            val session = LocalTimeshiftSession(Files.createTempDirectory("timeshift-sequence-bound").toFile(), OkHttpClient(), "${upstream.origin}/index.m3u8", emptyMap(), reserveBytes = 0)
            try {
                try { withTimeout(3000) { session.awaitReady() }; fail() } catch (_: IllegalStateException) { }
                assertEquals(0, segments.get())
                assertEquals(LocalTimeshiftSession.Status.FAILED, session.status.value)
            } finally { session.close() }
        }
    }
}

package tv.own.owntv.player

import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class ProviderRetryGuardTest {
    @Test fun `503 zero does not acquire an extra HTTP followup outside the player budget`() = refusal(503, "0")
    @Test fun `429 retains the whole provider deadline`() = refusal(429, "120")
    @Test fun `408 does not retry the completed HTTP request implicitly`() = refusal(408, "0")

    private fun refusal(status: Int, retryAfter: String) {
        ServerSocket(0).use { server ->
            val requests = AtomicInteger()
            server.soTimeout = 300
            val worker = thread(isDaemon = true) {
                while (!server.isClosed) {
                    val socket = try { server.accept() } catch (_: java.net.SocketTimeoutException) { continue } catch (_: Exception) { break }
                    socket.use {
                        it.soTimeout = 2_000
                        val input = it.getInputStream().bufferedReader()
                        while (input.readLine()?.takeIf(String::isNotEmpty) != null) { }
                        requests.incrementAndGet()
                        it.getOutputStream().write("HTTP/1.1 $status Refused\r\nRetry-After: $retryAfter\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    }
                }
            }
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).addNetworkInterceptor(ProviderRetryGuard()).build()
            try {
                val error = runCatching { client.newCall(Request.Builder().url("http://127.0.0.1:${server.localPort}/live").build()).execute().close() }.exceptionOrNull()
                assertTrue(error is ProviderHttpRefusal)
                error as ProviderHttpRefusal
                assertEquals(status, error.responseCode)
                assertEquals(retryAfter, error.responseHeaders.entries.first { it.key.equals("Retry-After", true) }.value.first())
                assertEquals(1, requests.get())
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdownNow()
                server.close()
                worker.join(2_000)
            }
        }
    }
}

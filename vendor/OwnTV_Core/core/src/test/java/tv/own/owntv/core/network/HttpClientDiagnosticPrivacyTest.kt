package tv.own.owntv.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket

class HttpClientDiagnosticPrivacyTest {
    @Test fun `unknown response encoding values cannot inject private diagnostic data`() {
        assertEquals("gzip,chunked", HttpClient.diagnosticEncoding("GZIP, chunked"))
        assertEquals("other", HttpClient.diagnosticEncoding("private-token=https://user:secret@host"))
        assertEquals("", HttpClient.diagnosticEncoding(null))
    }
    @Test fun `origin omits every path query fragment and userinfo regardless of token name`() {
        val urls = listOf(
            "https://user:secret@example.invalid:8443/live/user/secret/1.m3u8?opaque=token#fragment",
            "https://example.invalid/timeshift/user/secret/60/date/1.ts?signature=secret",
            "https://example.invalid/list/secret?providerCustomCredential=secret",
        )
        assertEquals("https://example.invalid:8443", HttpClient.diagnosticOrigin(urls.first()))
        for (url in urls.drop(1)) assertEquals("https://example.invalid", HttpClient.diagnosticOrigin(url))
        assertEquals("invalid-url", HttpClient.diagnosticOrigin("not a valid URL with secret"))
    }
    @Test fun `failure diagnostics never include messages causes or signed URLs`() {
        val error = IOException("https://user:secret@example.invalid/private?unknownToken=secret",
            IllegalArgumentException("nested-secret"))
        val message = HttpClient.diagnosticFailure(error, true)
        assertEquals("type=IOException status=0 retrying=true", message)
        assertFalse(message.contains("secret"))
        assertEquals("type=HttpStatusException status=403 retrying=false",
            HttpClient.diagnosticFailure(HttpClient.HttpStatusException(403, "secret"), false))
    }
    @Test(timeout = 10000) fun `request identity remains intact while status exception exposes only origin`() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val client = OkHttpClient()
        val peer = async(Dispatchers.IO) {
            server.accept().use { socket ->
                socket.soTimeout = 3000
                val input = socket.getInputStream().bufferedReader()
                val request = input.readLine()
                val headers = buildList { while (true) { val line = input.readLine(); if (line.isNullOrEmpty()) break; add(line) } }
                socket.getOutputStream().apply { write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); flush() }
                request to headers
            }
        }
        try {
            val url = "http://127.0.0.1:${server.localPort}/live/test-user/test-secret/1.m3u8?customToken=test-secret"
            try {
                HttpClient(client).get(url, userAgent = "TestAgent", headers = mapOf("Referer" to "https://example.invalid/private")) { it.readBytes() }
                fail("status should remain an error")
            } catch (error: HttpClient.HttpStatusException) {
                assertEquals(403, error.code)
                assertEquals("HTTP 403 for http://127.0.0.1:${server.localPort}", error.message)
            }
            val (request, headers) = withTimeout(3000) { peer.await() }
            assertTrue(request.contains("/live/test-user/test-secret/1.m3u8?customToken=test-secret"))
            assertTrue(headers.any { it.equals("User-Agent: TestAgent", true) })
            assertTrue(headers.any { it.equals("Referer: https://example.invalid/private", true) })
        } finally {
            peer.cancel(); server.close(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }
}

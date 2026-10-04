package tv.own.owntv.core.network

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class HttpClientCancellationTest {
    @Test fun cancellationDisconnectsExecuteBeforeResponseHeaders() = verifyCancellation(false)
    @Test fun cancellationDisconnectsReadBeforeResponseBodyCompletes() = verifyCancellation(true)

    private fun verifyCancellation(sendPartialBody: Boolean) = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val started = CompletableDeferred<Unit>()
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        val peer = async(Dispatchers.IO) {
            server.accept().use { socket ->
                socket.soTimeout = 3000
                val input = socket.getInputStream().bufferedReader()
                while (!input.readLine().isNullOrEmpty()) { }
                if (sendPartialBody) socket.getOutputStream().apply {
                    write("HTTP/1.1 200 OK\r\nContent-Length: 10000\r\n\r\npartial".toByteArray()); flush()
                }
                started.complete(Unit)
                assertEquals(-1, socket.getInputStream().read())
            }
        }
        val worker = launch(Dispatchers.IO) {
            HttpClient(client).get("http://127.0.0.1:${server.localPort}/catalogue", maxAttempts = 3) { it.readBytes() }
        }
        try {
            withTimeout(3000) { started.await() }
            withTimeout(2000) { worker.cancelAndJoin(); peer.await() }
            assertTrue(worker.isCancelled)
        } finally {
            worker.cancel(); peer.cancel(); server.close()
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }
}

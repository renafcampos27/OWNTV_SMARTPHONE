package tv.own.owntv.player

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class PlaybackHttpCallsTest {
    @Test fun `headers after invalidation close and cancel even before the retirement worker runs`() {
        val calls = PlaybackHttpCalls()
        val call = calls.factory(client).newCall(request)
        calls.retireAll() // Worker has not yet had a chance to cancel this retired call.
        assertFalse(call.isCanceled())
        val response = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(okio.Buffer().writeUtf8("late").let { buffer ->
                object : okhttp3.ResponseBody() {
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength() = 4L
                    override fun source(): okio.BufferedSource = buffer
                }
            }).build()
        assertFalse(calls.opened(call, response))
        assertTrue("A rejected response must cancel its retired transport too", call.isCanceled())
        assertFalse("No missing acknowledgment may hold the handover gate", calls.hasRetiredCalls())
    }

    @Test(timeout = 10000) fun `async retirement invalidates immediately without waiting for body I O`() {
        val calls = PlaybackHttpCalls()
        val oldFactory = calls.factory(client)
        val call = oldFactory.newCall(request)
        val entered = java.util.concurrent.CountDownLatch(1)
        val complete = java.util.concurrent.CountDownLatch(1)
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 1L
            override fun source(): okio.BufferedSource = okio.Buffer().writeUtf8("x")
            override fun close() {
                entered.countDown()
                check(complete.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        calls.opened(call, okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build())
        try {
            calls.retireAsync() // Returns while the worker is deliberately blocked in close().
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(calls.hasRetiredCalls())
            assertTrue(oldFactory.newCall(request).isCanceled())
            repeat(20) { calls.retireAsync() } // Must coalesce; none waits for the blocked worker.
            val next = calls.factory(client).newCall(request)
            complete.countDown()
            kotlinx.coroutines.runBlocking { assertTrue(SourceDrain.await { calls.hasRetiredCalls() }) }
            assertFalse(next.isCanceled())
            calls.cancelAll()
        } finally { complete.countDown() }
    }

    @Test(timeout = 10000) fun `async retirement retries a transient close failure once`() {
        val done = java.util.concurrent.CountDownLatch(1)
        val calls = PlaybackHttpCalls { if (it.startsWith("close_pass")) done.countDown() }
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val call = calls.factory(client).newCall(request)
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 1L
            override fun source(): okio.BufferedSource = okio.Buffer().writeUtf8("x")
            override fun close() {
                if (attempts.incrementAndGet() == 1) throw IllegalStateException("transient")
            }
        }
        calls.opened(call, okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build())
        calls.retireAsync()
        assertTrue(done.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(2, attempts.get())
        assertFalse(calls.hasRetiredCalls())
    }

    @Test(timeout = 10000) fun `late headers keep ownership until their rejected body finishes closing`() {
        val calls = PlaybackHttpCalls()
        val call = calls.factory(client).newCall(request)
        calls.cancelAll()
        assertFalse(calls.hasRetiredCalls())
        val entered = java.util.concurrent.CountDownLatch(1)
        val complete = java.util.concurrent.CountDownLatch(1)
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 1L
            override fun source(): okio.BufferedSource = okio.Buffer().writeUtf8("x")
            override fun close() {
                calls.finished(call)
                entered.countDown()
                check(complete.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val response = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build()
        try {
            val closing = worker.submit<Boolean> { calls.opened(call, response) }
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(calls.hasRetiredCalls())
            complete.countDown()
            assertFalse(closing.get(3, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(calls.hasRetiredCalls())
        } finally { complete.countDown(); worker.shutdownNow() }
    }

    @Test(timeout = 10000) fun `persistent close failure stops after two attempts and retains the gate`() {
        val done = java.util.concurrent.CountDownLatch(1)
        val calls = PlaybackHttpCalls { if (it.startsWith("close_pass")) done.countDown() }
        val attempts = java.util.concurrent.atomic.AtomicInteger()
        val call = calls.factory(client).newCall(request)
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 1L
            override fun source(): okio.BufferedSource = okio.Buffer().writeUtf8("x")
            override fun close() { attempts.incrementAndGet(); throw IllegalStateException("persistent") }
        }
        calls.opened(call, okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build())
        calls.retireAsync()
        assertTrue(done.await(3, java.util.concurrent.TimeUnit.SECONDS))
        assertEquals(2, attempts.get())
        assertTrue(calls.hasRetiredCalls())
    }

    @Test(timeout = 20000) fun `revisited channels open only after the previous streaming body is released`() = kotlinx.coroutines.runBlocking<Unit> {
        val calls = PlaybackHttpCalls()
        val requests = TuneHttpRequests()
        val server = java.net.ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val transport = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request()).also { calls.opened(chain.call(), it) }
        }.eventListenerFactory { call ->
            val token = call.request().tag(TuneToken::class.java)!!
            object : okhttp3.EventListener() {
                override fun callStart(call: Call) { requests.started(token) }
                override fun callEnd(call: Call) { requests.finished(token); calls.finished(call) }
                override fun callFailed(call: Call, ioe: java.io.IOException) {
                    requests.finished(token); calls.finished(call)
                }
            }
        }.build()
        var previous: okhttp3.Response? = null
        try {
            val serving = worker.submit {
                repeat(30) {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val input = socket.getInputStream().bufferedReader()
                        while (!input.readLine().isNullOrEmpty()) { }
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\nx".toByteArray())
                            flush()
                        }
                        // An unfinished segment stays open until the client retires it.
                        runCatching { while (input.read() != -1) { } }
                    }
                }
            }
            repeat(30) { index ->
                val token = TuneToken(index.toLong() + 1, 1)
                repeat(3) { calls.cancelAll() } // Repeated retirement before all terminal callbacks is harmless.
                // Intentionally do not close via the loader: retirement owns orphaned bodies too.
                previous = null
                assertTrue(SourceDrain.await { calls.hasRetiredCalls() })
                assertEquals(0, requests.otherSources(token))
                val url = okhttp3.HttpUrl.Builder().scheme("http")
                    .host(server.inetAddress.hostAddress!!).port(server.localPort)
                    .addPathSegment("${index % 3}.ts").build()
                previous = calls.factory(transport).newCall(
                    Request.Builder().url(url).tag(TuneToken::class.java, token).build()
                ).execute()
                assertEquals('x'.code.toByte(), previous.body.source().readByte())
            }
            calls.cancelAll()
            assertTrue(SourceDrain.await { calls.hasRetiredCalls() })
            serving.get(3, java.util.concurrent.TimeUnit.SECONDS)
        } finally {
            calls.cancelAll()
            previous?.close()
            server.close()
            worker.shutdownNow()
            transport.connectionPool.evictAll()
            transport.dispatcher.executorService.shutdownNow()
        }
    }


    @Test(timeout = 15000) fun `cancelled DNS cannot block a new channel while terminal event is delayed`() = kotlinx.coroutines.runBlocking<Unit> {
        val calls = PlaybackHttpCalls()
        val requests = TuneHttpRequests()
        val dnsEntered = java.util.concurrent.CountDownLatch(1)
        val releaseDns = java.util.concurrent.CountDownLatch(1)
        val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
        val server = java.net.ServerSocket(0, 5, java.net.InetAddress.getLoopbackAddress())
        val old = TuneToken(1, 1)
        val next = TuneToken(2, 1)
        val delayFirstLookup = java.util.concurrent.atomic.AtomicBoolean(true)
        val transport = OkHttpClient.Builder().dns { host ->
            if (host == "delayed.invalid") {
                if (delayFirstLookup.compareAndSet(true, false)) {
                    dnsEntered.countDown()
                    releaseDns.await(10, java.util.concurrent.TimeUnit.SECONDS)
                }
                listOf(java.net.InetAddress.getLoopbackAddress())
            } else okhttp3.Dns.SYSTEM.lookup(host)
        }.eventListenerFactory { call ->
            val token = call.request().tag(TuneToken::class.java)!!
            object : okhttp3.EventListener() {
                override fun callStart(call: Call) { requests.started(token) }
                override fun callEnd(call: Call) { requests.finished(token); calls.finished(call) }
                override fun callFailed(call: Call, ioe: java.io.IOException) { requests.finished(token); calls.finished(call) }
            }
        }.addInterceptor { chain ->
            chain.proceed(chain.request()).also { assertTrue(calls.opened(chain.call(), it)) }
        }.build()
        try {
            val cancelled = workers.submit<Boolean> {
                try {
                    calls.factory(transport).newCall(Request.Builder()
                        .url("http://delayed.invalid:${server.localPort}/old.m3u8").tag(TuneToken::class.java, old).build())
                        .execute().use { }
                    false
                } catch (_: java.io.IOException) { true }
            }
            assertTrue(dnsEntered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            calls.cancelAll()
            assertEquals(1, requests.otherSources(next)) // The diagnostic event has NOT finished.
            assertFalse(calls.hasRetiredCalls()) // But cancellation has closed all local resources.
            assertTrue(SourceDrain.await { calls.hasRetiredCalls() })
            val served = workers.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream().bufferedReader()
                    val line = input.readLine()
                    assertTrue(line.contains("/new.m3u8")) // The old request never reaches the server.
                    while (!input.readLine().isNullOrEmpty()) { }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nOK".toByteArray())
                        flush()
                    }
                }
            }
            val url = okhttp3.HttpUrl.Builder().scheme("http").host("delayed.invalid")
                .port(server.localPort).addPathSegment("new.m3u8").build()
            calls.factory(transport).newCall(Request.Builder().url(url).tag(TuneToken::class.java, next).build())
                .execute().use { assertEquals("OK", it.body.string()) }
            served.get(3, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(1, requests.otherSources(next))
            releaseDns.countDown()
            assertTrue(cancelled.get(3, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(0, requests.otherSources(next))
        } finally {
            releaseDns.countDown()
            calls.cancelAll()
            server.close()
            workers.shutdownNow()
            transport.connectionPool.evictAll()
            transport.dispatcher.executorService.shutdownNow()
        }
    }

    @Test fun `failed cancellation remains a gate until a successful retry closes it`() {
        val calls = PlaybackHttpCalls()
        val real = client.newCall(request)
        var refuse = true
        val broken = object : Call by real {
            override fun cancel() {
                if (refuse) throw IllegalStateException("simulated cancellation failure")
                real.cancel()
            }
        }
        calls.factory(Call.Factory { broken }).newCall(request)
        calls.cancelAll()
        assertTrue(calls.hasRetiredCalls())
        refuse = false
        calls.cancelAll()
        assertFalse(calls.hasRetiredCalls())
    }


    @Test(timeout = 10000) fun `external cancellation flag does not release the gate before its acknowledgment`() {
        val calls = PlaybackHttpCalls()
        val real = client.newCall(request)
        val entered = java.util.concurrent.CountDownLatch(1)
        val complete = java.util.concurrent.CountDownLatch(1)
        val flag = java.util.concurrent.atomic.AtomicBoolean(false)
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val external = object : Call by real {
            override fun isCanceled() = flag.get()
            override fun cancel() {
                if (!flag.compareAndSet(false, true)) return
                entered.countDown()
                check(complete.await(5, java.util.concurrent.TimeUnit.SECONDS))
                real.cancel() // Emits canceled only when transport cancellation actually completes.
            }
        }
        val call = calls.factory(Call.Factory { external }).newCall(request)
        try {
            val cancelling = worker.submit { call.cancel() }
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(call.isCanceled())
            calls.cancelAll() // A second cancel returns immediately; transport is not acknowledged.
            assertTrue(calls.hasRetiredCalls())
            complete.countDown()
            cancelling.get(3, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(calls.hasRetiredCalls())
        } finally { complete.countDown(); worker.shutdownNow() }
    }

    @Test(timeout = 10000) fun `terminal callback while body close is still running cannot release the gate`() {
        val calls = PlaybackHttpCalls()
        val call = calls.factory(client).newCall(request)
        val entered = java.util.concurrent.CountDownLatch(1)
        val complete = java.util.concurrent.CountDownLatch(1)
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 1L
            override fun source(): okio.BufferedSource = okio.Buffer().writeUtf8("x")
            override fun close() {
                calls.finished(call)
                entered.countDown()
                check(complete.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
        }
        val response = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build()
        assertTrue(calls.opened(call, response))
        try {
            val closing = worker.submit { calls.cancelAll() }
            assertTrue(entered.await(3, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue(calls.hasRetiredCalls())
            calls.finished(call) // Duplicate terminal notifications are not a body-close acknowledgment.
            assertTrue(calls.hasRetiredCalls())
            complete.countDown()
            closing.get(3, java.util.concurrent.TimeUnit.SECONDS)
            assertFalse(calls.hasRetiredCalls())
        } finally { complete.countDown(); worker.shutdownNow() }
    }

    @Test fun `terminal callback followed by failed body close retains ownership until retry succeeds`() {
        val calls = PlaybackHttpCalls()
        val call = calls.factory(client).newCall(request)
        var refuse = true
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 1L
            override fun source(): okio.BufferedSource = okio.Buffer().writeUtf8("x")
            override fun close() {
                calls.finished(call)
                if (refuse) throw IllegalStateException("simulated body-close failure")
            }
        }
        val response = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build()
        assertTrue(calls.opened(call, response))
        calls.cancelAll()
        assertTrue(calls.hasRetiredCalls())
        refuse = false
        calls.cancelAll()
        assertFalse(calls.hasRetiredCalls())
    }

    private val client = OkHttpClient()
    private val request = Request.Builder().url("https://example.invalid/live.m3u8").build()

    @Test fun `exit cancels old calls but reopening the same URL survives`() {
        val calls = PlaybackHttpCalls()
        val oldFactory = calls.factory(client)
        val old = oldFactory.newCall(request)
        calls.cancelAll()
        val reopened = calls.factory(client).newCall(request)
        assertTrue(old.isCanceled())
        assertFalse(reopened.isCanceled())
        assertTrue(oldFactory.newCall(request).isCanceled())
        assertFalse(reopened.isCanceled())
    }

    @Test fun `stop racing with call creation cannot leak a late call`() {
        val calls = PlaybackHttpCalls()
        val factory = calls.factory(Call.Factory { req ->
            client.newCall(req).also { calls.cancelAll() }
        })
        assertTrue(factory.newCall(request).isCanceled())
    }

    @Test fun `stopping one engine leaves other engines and unrelated traffic alone`() {
        val first = PlaybackHttpCalls()
        val second = PlaybackHttpCalls()
        val active = first.factory(client).newCall(request)
        val tile = second.factory(client).newCall(request)
        val metadata = client.newCall(request)
        first.cancelAll()
        assertTrue(active.isCanceled())
        assertFalse(tile.isCanceled())
        assertFalse(metadata.isCanceled())
    }

    @Test fun `completed calls are forgotten and repeated exits remain safe`() {
        val calls = PlaybackHttpCalls()
        val finished = calls.factory(client).newCall(request)
        calls.finished(finished)
        calls.cancelAll()
        calls.cancelAll()
        assertFalse(finished.isCanceled())
        assertFalse(calls.factory(client).newCall(request).isCanceled())
    }

    @Test(timeout = 10000) fun `exit interrupts a stream body waiting for more bytes`() {
        val calls = PlaybackHttpCalls()
        val workers = java.util.concurrent.Executors.newFixedThreadPool(2)
        val reading = java.util.concurrent.CountDownLatch(1)
        val finishServer = java.util.concurrent.CountDownLatch(1)
        val server = java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
        try {
            workers.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 3000
                    val input = socket.getInputStream().bufferedReader()
                    while (!input.readLine().isNullOrEmpty()) { /* consume request headers */ }
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\nx".toByteArray())
                        flush()
                    }
                    finishServer.await(5, java.util.concurrent.TimeUnit.SECONDS)
                }
            }
            val url = okhttp3.HttpUrl.Builder().scheme("http")
                .host(server.inetAddress.hostAddress!!).port(server.localPort).build()
            val result = workers.submit<Boolean> {
                try {
                    calls.factory(client).newCall(Request.Builder().url(url).build()).execute().use { response ->
                        val body = response.body.source()
                        body.readByte()
                        reading.countDown()
                        body.readByte() // server deliberately leaves the streaming body unfinished
                    }
                    false
                } catch (_: java.io.IOException) { true }
            }
            assertTrue(reading.await(3, java.util.concurrent.TimeUnit.SECONDS))
            calls.cancelAll()
            assertTrue(result.get(2, java.util.concurrent.TimeUnit.SECONDS))
        } finally {
            finishServer.countDown()
            server.close()
            workers.shutdownNow()
        }
    }


    @Test fun `thirty source replacements retire all variants and reject delayed segments`() {
        val calls = PlaybackHttpCalls()
        val previousCalls = mutableListOf<Call>()
        val previousFactories = mutableListOf<Call.Factory>()
        repeat(30) { index ->
            calls.cancelAll() // the reprepare boundary, including same-URL retries
            val factory = calls.factory(client)
            val variant = request.newBuilder()
                .url("https://example.invalid/sic-${index % 3}.m3u8").build()
            val current = List(3) { factory.newCall(variant) } // playlist, video and audio
            assertTrue(previousCalls.all { it.isCanceled() })
            previousFactories.forEach { stale ->
                assertTrue(stale.newCall(variant).isCanceled())
            }
            // A late callEnd from the old source must not remove or cancel the new source's calls.
            previousCalls.forEach { calls.finished(it) }
            assertTrue(current.none { it.isCanceled() })
            previousCalls.addAll(current)
            previousFactories.add(factory)
        }
        calls.cancelAll()
        assertTrue(previousCalls.all { it.isCanceled() })
    }

    @Test fun `response racing with retirement is closed instead of delivered to the old loader`() {
        val calls = PlaybackHttpCalls()
        val old = calls.factory(client).newCall(request)
        calls.cancelAll()
        var closed = false
        val body = object : okhttp3.ResponseBody() {
            private val stream = okio.Buffer().writeUtf8("unread body")
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 11L
            private val tracked = object : okio.ForwardingSource(stream) {
                override fun close() { closed = true; stream.close() }
            }.buffer()
            override fun source(): okio.BufferedSource = tracked
        }
        val response = okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200).message("OK").body(body).build()
        assertFalse(calls.opened(old, response))
        assertTrue(closed)
        assertFalse(calls.factory(client).newCall(request).isCanceled())
    }

    @Test fun `a refused body close cannot leave the remaining retired bodies open`() {
        val calls = PlaybackHttpCalls()
        val first = calls.factory(client).newCall(request)
        val second = calls.factory(client).newCall(request)
        var secondClosed = false
        fun response(fail: Boolean): okhttp3.Response {
            val body = object : okhttp3.ResponseBody() {
                private val bytes = okio.Buffer().writeUtf8("x")
                private val tracked = object : okio.ForwardingSource(bytes) {
                    override fun close() {
                        if (fail) throw IllegalStateException()
                        secondClosed = true
                        bytes.close()
                    }
                }.buffer()
                override fun contentType(): okhttp3.MediaType? = null
                override fun contentLength() = 1L
                override fun source(): okio.BufferedSource = tracked
            }
            return okhttp3.Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build()
        }
        assertTrue(calls.opened(first, response(true)))
        assertTrue(calls.opened(second, response(false)))
        calls.cancelAll()
        assertTrue(secondClosed)
        assertTrue(first.isCanceled())
        assertTrue(second.isCanceled())
    }

}

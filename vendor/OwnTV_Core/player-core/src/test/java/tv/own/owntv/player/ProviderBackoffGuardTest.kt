package tv.own.owntv.player

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ProviderBackoffGuardTest {
    private val origin = "https://panel.example/live/user/password/a.m3u8"

    @After fun clearWait() { LiveStreamQuirks.clearHostBackOff(origin) }

    @Test fun `a new channel on the same account waits before any network work`() {
        val requests = java.util.concurrent.atomic.AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor(ProviderBackoffGuard()).addInterceptor { chain ->
            requests.incrementAndGet()
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(200).message("OK").body("ok".toResponseBody()).build()
        }.build()
        LiveStreamQuirks.rememberHostBackOff(origin, 30)
        val another = Request.Builder().url("https://panel.example/live/user/password/b.m3u8").build()
        val error = runCatching { client.newCall(another).execute().close() }.exceptionOrNull()
        assertTrue(error is ProviderHttpRefusal)
        assertEquals(429, (error as ProviderHttpRefusal).responseCode)
        assertEquals(0, requests.get())
        LiveStreamQuirks.clearHostBackOff(origin)
        client.newCall(another).execute().close()
        assertEquals(1, requests.get())
    }

    @Test fun `redirected segment respects its original provider account wait`() {
        LiveStreamQuirks.rememberHostBackOff(origin, 30, code = 503)
        val client = OkHttpClient.Builder().addInterceptor(ProviderBackoffGuard()).build()
        val request = Request.Builder().url("https://unreachable.invalid/cdn/segment.ts")
            .tag(ProviderWaitOrigin::class.java, ProviderWaitOrigin(origin)).build()
        val error = runCatching { client.newCall(request).execute() }.exceptionOrNull()
        assertTrue(error is ProviderHttpRefusal)
        assertEquals(503, (error as ProviderHttpRefusal).responseCode)
    }

    @Test fun `another account and another provider do not inherit the wait`() {
        LiveStreamQuirks.rememberHostBackOff(origin, 30)
        assertFalse(LiveStreamQuirks.isHostBackingOff("https://panel.example/live/other/password/b.m3u8"))
        assertFalse(LiveStreamQuirks.isHostBackingOff("https://other.example/live/user/password/b.m3u8"))
        assertTrue(LiveStreamQuirks.isHostBackingOff("https://panel.example/timeshift/user/password/60/date/b.m3u8"))
    }

    @Test fun `a shorter subsequent refusal cannot shorten the existing deadline`() {
        LiveStreamQuirks.rememberHostBackOff(origin, 30, nowMs = 1000)
        LiveStreamQuirks.rememberHostBackOff(origin, 2, nowMs = 2000)
        assertTrue(LiveStreamQuirks.isHostBackingOff(origin, nowMs = 30000))
        assertFalse(LiveStreamQuirks.isHostBackingOff(origin, nowMs = 31000))
    }
}

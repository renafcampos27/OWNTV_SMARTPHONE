package tv.own.owntv.core.network

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The OkHttp client the **playback** engines stream through.
 *
 * It is derived from the app-wide singleton with [OkHttpClient.newBuilder], so it keeps every piece of
 * shared configuration — proxy selector/authenticator, custom DNS, timeouts, HTTP/1.1, the default
 * User-Agent interceptor — and picks up runtime proxy/DNS changes exactly as before. What it does *not*
 * share is the **connection pool**: this one owns its sockets.
 *
 * The live engine evicts idle playback sockets on stop, without treating an idle socket as proof of
 * an occupied provider session. Evicting the singleton's pool would also discard the reusable
 * connections used by EPG downloads, panel API calls and image loads.
 * [evictAll] here touches stream connections only.
 */
class StreamingHttpClient(base: OkHttpClient) {

    /** Sized for the handful of concurrent stream/segment connections one playback session opens; the
     *  idle keep-alive matches OkHttp's own default, since a zap back to the previous channel benefits
     *  from a warm socket and only a *stop* clears the pool. */
    private val pool = ConnectionPool(MAX_IDLE_CONNECTIONS, KEEP_ALIVE_MINUTES, TimeUnit.MINUTES)

    val client: OkHttpClient = base.newBuilder().connectionPool(pool).build()
    private val evictionQueued = AtomicBoolean(false)
    private val evictionRequested = AtomicBoolean(false)

    /** Coalesced, reusable worker. Never close sockets on the UI thread. */
    fun evictAllAsync() {
        evictionRequested.set(true)
        if (!evictionQueued.compareAndSet(false, true)) return
        evictionExecutor.execute {
            do {
                evictionRequested.set(false)
                runCatching { evictAll() }
                evictionQueued.set(false)
            } while (evictionRequested.get() && evictionQueued.compareAndSet(false, true))
        }
    }

    /** Close the idle stream sockets. Connections with a call in flight are untouched — OkHttp only
     *  evicts what nothing is using. */
    fun evictAll() {
        pool.evictAll()
    }

    private companion object {
        val evictionExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "owntv-http-evict").apply { isDaemon = true }
        }
        const val MAX_IDLE_CONNECTIONS = 5
        const val KEEP_ALIVE_MINUTES = 5L
    }
}

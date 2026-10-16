package tv.own.owntv.core.network

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A changing resolver supplies its configuration identity so stale answers cannot cross settings. */
interface DnsCacheIdentity {
    fun cacheIdentity(): Any
    fun lookupForIdentity(hostname: String, identity: Any): List<InetAddress>
}

/** Bounds resolver waits, worker count and positive fallback storage. A timeout cannot stop native DNS. */
class BoundedDns(
    private val timeoutMs: Long = 5_000L,
    private val staleTimeoutMs: Long = 1_000L,
    val delegate: Dns = Dns.SYSTEM,
    private val maxEntries: Int = 128,
    private val staleTtlMs: Long = 5 * 60_000L,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : Dns {
    private data class Key(val hostname: String, val resolver: Any)
    private data class Answer(val addresses: List<InetAddress>, val at: Long)
    private val lock = Any()
    private val cache = LinkedHashMap<Key, Answer>(16, .75f, true)
    private val inFlight = HashMap<Key, FutureTask<List<InetAddress>>>()

    init { require(timeoutMs > 0 && staleTimeoutMs > 0 && maxEntries > 0 && staleTtlMs > 0) }

    override fun lookup(hostname: String): List<InetAddress> {
        if (Thread.currentThread().isInterrupted) throw UnknownHostException("DNS lookup interrupted")
        val key = Key(hostname.lowercase(), (delegate as? DnsCacheIdentity)?.cacheIdentity() ?: delegate)
        val pending: FutureTask<List<InetAddress>>
        val stale: List<InetAddress>?
        synchronized(lock) {
            val now = nowMs()
            cache.entries.removeAll { now - it.value.at >= staleTtlMs }
            stale = cache[key]?.addresses
            pending = inFlight[key]?.takeUnless { it.isDone } ?: object : FutureTask<List<InetAddress>>(Callable {
                val answer = ((delegate as? DnsCacheIdentity)?.lookupForIdentity(hostname, key.resolver)
                    ?: delegate.lookup(hostname)).toList()
                if (answer.isEmpty()) throw UnknownHostException("DNS returned no addresses")
                synchronized(lock) {
                    cache[key] = Answer(answer, nowMs())
                    while (cache.size > maxEntries) cache.remove(cache.keys.first())
                }
                answer
            }) {
                override fun done() { synchronized(lock) { if (inFlight[key] === this) inFlight.remove(key) } }
            }.also {
                inFlight[key] = it
                try { workers.execute(it) } catch (_: RejectedExecutionException) {
                    inFlight.remove(key)
                    it.cancel(false)
                }
            }
        }
        if (pending.isCancelled) return fallback(key) ?: throw UnknownHostException("DNS resolver capacity exhausted")
        try {
            return pending.get(if (stale != null) staleTimeoutMs else timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw UnknownHostException("DNS lookup interrupted").apply { initCause(e) }
        } catch (e: ExecutionException) {
            return fallback(key) ?: throw (e.cause as? UnknownHostException ?: UnknownHostException("DNS lookup failed").apply { initCause(e.cause) })
        } catch (_: TimeoutException) {
            return fallback(key) ?: throw UnknownHostException("No DNS answer within ${timeoutMs}ms")
        }
    }

    private fun fallback(key: Key): List<InetAddress>? = synchronized(lock) {
        cache[key]?.takeIf { nowMs() - it.at < staleTtlMs }?.addresses
    }

    private companion object {
        // Shared cap includes every player/client: stuck native lookups cannot grow threads or a queue.
        val workers = ThreadPoolExecutor(0, 4, 30, TimeUnit.SECONDS, SynchronousQueue<Runnable>(),
            { task -> Thread(task, "owntv-dns").apply { isDaemon = true } })
    }
}

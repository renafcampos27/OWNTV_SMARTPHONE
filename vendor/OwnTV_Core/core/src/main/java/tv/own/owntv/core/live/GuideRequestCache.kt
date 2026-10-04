package tv.own.owntv.core.live

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Shared raw requests: one cancelled waiter does not cancel the remaining consumers. */
internal class GuideRequestCache<K, V>(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val ttlMs: Long,
    private val maximumSize: Int = 256,
) {
    private data class Entry<V>(val expiresAt: Long, val value: V)
    private val cached = LinkedHashMap<K, Entry<V>>()
    private val pending = mutableMapOf<K, Deferred<V>>()
    private var epoch = 0L

    @Synchronized fun clear() {
        epoch++
        cached.clear()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    suspend fun get(key: K, load: suspend () -> V): V {
        val request = synchronized(this) {
            cached[key]?.takeIf { it.expiresAt > clock() }?.let { return it.value }
            pending[key] ?: run {
                val revision = epoch
                val created = scope.async(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    val value = load()
                    currentCoroutineContext().ensureActive()
                    synchronized(this@GuideRequestCache) {
                        if (revision == epoch) {
                            cached[key] = Entry(clock() + ttlMs, value)
                            while (cached.size > maximumSize) cached.remove(cached.keys.first())
                        }
                    }
                    value
                }
                pending[key] = created
                created.invokeOnCompletion {
                    synchronized(this@GuideRequestCache) { if (pending[key] === created) pending.remove(key) }
                }
                created
            }
        }
        return request.await()
    }
}

/** Now expires at the next visible programme boundary, even inside the regular cache TTL. */
internal fun guideCacheExpiry(now: Long, ttlMs: Long, boundaries: List<Long>): Long =
    minOf(now + ttlMs, boundaries.filter { it > now }.minOrNull() ?: Long.MAX_VALUE)

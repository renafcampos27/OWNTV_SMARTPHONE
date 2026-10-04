package tv.own.owntv.core.live

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GuideRequestCacheTest {
    private fun <T> runCacheTest(block: suspend CoroutineScope.() -> T): T = runBlocking {
        val producers = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { block(producers) } finally { producers.cancel() }
    }
    private fun clock(): Long = System.nanoTime() / 1_000_000L

    @Test fun equivalentRequestsShareProducerAndOneWaiterMayCancel() = runCacheTest {
        val cache = GuideRequestCache<String, Int>(this, ::clock, 1000)
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        suspend fun load(): Int { calls++; entered.complete(Unit); delay(100); return 42 }
        val first = async { cache.get("channel", ::load) }
        val second = async { cache.get("channel", ::load) }
        entered.await()
        first.cancelAndJoin()
        assertEquals(42, second.await())
        assertEquals(1, calls)
        assertEquals(42, cache.get("channel", ::load))
    }
    @Test fun invalidationRejectsAProducerThatWasStillRunning() = runCacheTest {
        val cache = GuideRequestCache<String, Int>(this, ::clock, 1000)
        val entered = CompletableDeferred<Unit>()
        val first = async { cache.get("channel") { entered.complete(Unit); delay(100); 1 } }
        entered.await(); cache.clear()
        assertTrue(first.runCatching { await() }.isFailure)
        assertEquals(2, cache.get("channel") { 2 })
    }
    @Test fun failedRequestIsNotCachedAsEmptyResult() = runCacheTest {
        val cache = GuideRequestCache<String, List<Int>>(this, ::clock, 1000)
        assertTrue(runCatching { cache.get("channel") { throw IllegalStateException() } }.isFailure)
        assertEquals(listOf(3), cache.get("channel") { listOf(3) })
    }
    @Test fun differentSourceIdentityNeverSharesRows() = runCacheTest {
        val cache = GuideRequestCache<Pair<Long, String>, Int>(this, ::clock, 1000)
        assertEquals(1, cache.get(1L to "42") { 1 })
        assertEquals(2, cache.get(2L to "42") { 2 })
    }
    @Test fun boundaryExpiresBeforeRegularTtl() {
        assertEquals(150L, guideCacheExpiry(100, 1000, listOf(150, 300)))
        assertEquals(1100L, guideCacheExpiry(100, 1000, listOf(80)))
        assertEquals(200L, guideCacheExpiry(100, 100, listOf(500)))
    }
}

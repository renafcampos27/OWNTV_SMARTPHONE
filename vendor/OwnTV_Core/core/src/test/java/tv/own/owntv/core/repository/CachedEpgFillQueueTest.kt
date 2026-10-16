package tv.own.owntv.core.repository

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CachedEpgFillQueueTest {
    @Test fun nearbyRequestsShareOneParseAndResultsRemainSpecificToEachRequest() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val parsed = mutableListOf<Set<String>>()
            val queue = CachedEpgFillQueue(scope, { ids, _ -> parsed += ids; setOf("a") }, 20)
            val a = async(start = CoroutineStart.UNDISPATCHED) { queue.request(setOf("a")) }
            val b = async(start = CoroutineStart.UNDISPATCHED) { queue.request(setOf("b")) }
            assertTrue(a.await()); assertFalse(b.await())
            assertEquals(listOf(setOf("a", "b")), parsed)
        } finally { scope.cancel() }
    }

    @Test fun subsetJoinsInFlightAndNewIdsWaitForNextSerializedParse() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val parsed = mutableListOf<Set<String>>()
            var concurrent = 0
            val queue = CachedEpgFillQueue(scope, { ids, _ ->
                assertEquals(0, concurrent); concurrent++
                parsed += ids
                if (parsed.size == 1) { entered.complete(Unit); release.await() }
                concurrent--
                ids
            }, 0)
            val first = async(start = CoroutineStart.UNDISPATCHED) { queue.request(setOf("a", "b")) }
            entered.await()
            val subset = async(start = CoroutineStart.UNDISPATCHED) { queue.request(setOf("a")) }
            val other = async(start = CoroutineStart.UNDISPATCHED) { queue.request(setOf("c")) }
            release.complete(Unit)
            assertTrue(first.await()); assertTrue(subset.await()); assertTrue(other.await())
            assertEquals(listOf(setOf("a", "b"), setOf("c")), parsed)
        } finally { scope.cancel() }
    }

    @Test fun cancellingTheOnlyCallerWithdrawsInterestWithoutPoisoningTheQueue() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>(); val observed = CompletableDeferred<Boolean>()
            var calls = 0
            val queue = CachedEpgFillQueue(scope, { ids, needed ->
                if (++calls == 1) { entered.complete(Unit); release.await(); observed.complete(needed()) }
                ids
            }, 0)
            val first = async(start = CoroutineStart.UNDISPATCHED) { queue.request(setOf("a")) }
            entered.await(); first.cancelAndJoin(); release.complete(Unit)
            assertFalse(observed.await())
            assertTrue(queue.request(setOf("b")))
        } finally { scope.cancel() }
    }

    @Test fun aFailedBatchDoesNotCancelTheCallerOrPreventLaterRequests() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            var first = true
            val queue = CachedEpgFillQueue(scope, { ids, _ ->
                if (first) { first = false; throw java.io.IOException() }
                ids
            }, 0)
            assertTrue(runCatching { queue.request(setOf("a")) }.isFailure)
            assertTrue(currentCoroutineContext().isActive)
            assertTrue(queue.request(setOf("a")))
        } finally { scope.cancel() }
    }
}

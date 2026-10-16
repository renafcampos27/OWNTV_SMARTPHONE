package tv.own.owntv.core.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext

/** Coalesces nearby manual matches and serializes cache fills without blocking the UI dispatcher. */
internal class CachedEpgFillQueue(
    private val scope: CoroutineScope,
    private val fill: suspend (Set<String>, () -> Boolean) -> Set<String>,
    private val batchDelayMs: Long = 50,
) {
    private data class Request(val ids: Set<String>, val answer: CompletableDeferred<Boolean>)
    private data class Batch(val ids: Set<String>, val requests: MutableList<Request>)
    private val lock = Any()
    private val pending = mutableListOf<Request>()
    private var active: Batch? = null
    private var running = false

    suspend fun request(ids: Set<String>): Boolean {
        if (ids.isEmpty()) return true
        val request = Request(ids, CompletableDeferred<Boolean>())
        synchronized(lock) {
            val batch = active
            if (batch != null && batch.ids.containsAll(ids)) batch.requests += request
            else pending += request
            if (!running) {
                running = true
                scope.launch { drain() }
            }
        }
        try { return request.answer.await() }
        finally { if (!currentCoroutineContext().isActive) request.answer.cancel() }
    }

    private suspend fun drain() {
        while (true) {
            delay(batchDelayMs)
            val batch = synchronized(lock) {
                val requests = pending.filter { it.answer.isActive }.toMutableList()
                pending.clear()
                if (requests.isEmpty()) { running = false; return }
                Batch(requests.flatMapTo(HashSet()) { it.ids }, requests).also { active = it }
            }
            val result = runCatching {
                fill(batch.ids) { synchronized(lock) { batch.requests.any { it.answer.isActive } } }
            }
            synchronized(lock) {
                active = null
                batch.requests.forEach { request ->
                    result.fold(
                        onSuccess = { found -> request.answer.complete(request.ids.any { it in found }) },
                        onFailure = { request.answer.completeExceptionally(it) },
                    )
                }
            }
        }
    }
}

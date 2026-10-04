package tv.own.owntv.core.network

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Completion handlers run too late when a coroutine is blocked in HTTP execute/read. This suspended
 * child observes cancellation immediately and closes the call, including its response body. It runs
 * no dispatcher work: cancellation resumes its finally on the cancelling thread. Normal completion
 * only removes the observer and leaves successful connections available for reuse.
 */
internal suspend fun <T> cancelBlockingCallWithCoroutine(cancelCall: () -> Unit, block: suspend () -> T): T = coroutineScope {
    val owner = currentCoroutineContext().job
    val watcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
        try { awaitCancellation() } finally { if (!owner.isActive) cancelCall() }
    }
    try {
        currentCoroutineContext().ensureActive()
        block()
    } finally {
        watcher.cancel()
    }
}

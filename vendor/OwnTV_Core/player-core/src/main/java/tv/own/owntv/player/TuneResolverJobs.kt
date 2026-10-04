package tv.own.owntv.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Main-thread owned resolver work; replacing a tune cancels the operation, not merely its result. */
internal class TuneResolverJobs(private val scope: CoroutineScope) {
    private var job: Job? = null
    val active: Boolean get() = job?.isActive == true

    fun launch(block: suspend CoroutineScope.() -> Unit) {
        cancel()
        val next = scope.launch(start = CoroutineStart.LAZY, block = block)
        job = next
        next.start()
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

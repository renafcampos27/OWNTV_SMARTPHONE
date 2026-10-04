package tv.own.owntv.core.sync.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** At most one persisted successor per closing drain, never a chain for every enqueue action. */
internal object DurableDrainWakeup {
    private class Queue {
        val gate = DrainWakeupGate()
        val enqueue = Mutex()
    }
    private val queues = ConcurrentHashMap<String, Queue>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private fun queue(name: String) = queues.getOrPut(name) { Queue() }
    fun gate(name: String): DrainWakeupGate = queue(name).gate

    fun kick(context: Context, name: String, request: OneTimeWorkRequest, replace: Boolean = false) {
        val appContext = context.applicationContext
        val state = queue(name)
        scope.launch {
            state.enqueue.withLock {
                try {
                    val manager = WorkManager.getInstance(appContext)
                    // Reconstruct cold-process knowledge from durable WorkManager state, off Main.
                    val unfinished = if (state.gate.needsWorkSnapshot()) {
                        manager.getWorkInfosForUniqueWork(name).get().any { !it.state.isFinished }
                    } else false
                    val action = state.gate.request(unfinished)
                    if (!replace && action == DrainWakeupGate.Enqueue.NONE) return@withLock
                    val policy = when {
                        replace -> ExistingWorkPolicy.REPLACE // Existing explicit Wi-Fi-setting change only.
                        action == DrainWakeupGate.Enqueue.SUCCESSOR -> ExistingWorkPolicy.APPEND_OR_REPLACE
                        else -> ExistingWorkPolicy.KEEP
                    }
                    manager.enqueueUniqueWork(name, policy, request).result.get()
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    state.gate.enqueueFailed()
                    throw cancelled
                } catch (failed: Exception) {
                    state.gate.enqueueFailed()
                    android.util.Log.w("DrainWakeup", "queue wakeup unavailable name=$name type=${failed.javaClass.simpleName}")
                }
            }
        }
    }
}

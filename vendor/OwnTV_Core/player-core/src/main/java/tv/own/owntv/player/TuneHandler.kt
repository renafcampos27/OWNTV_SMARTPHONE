package tv.own.owntv.player

import android.os.Handler
import android.os.Looper

/** Main-thread timers retain the source which scheduled them, including self-rescheduling watchdogs. */
internal class TuneHandler(
    private val ownership: TuneOwnership,
    private val enqueue: (Runnable, Long) -> Boolean,
    private val cancel: (Runnable) -> Unit,
) {
    constructor(ownership: TuneOwnership) : this(ownership, Handler(Looper.getMainLooper()))
    private constructor(ownership: TuneOwnership, handler: Handler) : this(ownership, handler::postDelayed, handler::removeCallbacks)
    private val pending = mutableMapOf<Runnable, MutableSet<Runnable>>()

    fun post(task: Runnable) = postDelayed(task, 0)
    fun postDelayed(task: Runnable, delayMs: Long) = schedule(ownership.current, task, delayMs)
    fun postFor(token: TuneToken, task: Runnable) = schedule(token, task, 0)

    private fun schedule(token: TuneToken, task: Runnable, delayMs: Long): Boolean {
        if (!ownership.accepts(token)) return false
        lateinit var wrapped: Runnable
        wrapped = Runnable {
            synchronized(pending) {
                pending[task]?.let { it.remove(wrapped); if (it.isEmpty()) pending.remove(task) }
            }
            if (ownership.accepts(token)) task.run()
        }
        synchronized(pending) { pending.getOrPut(task) { mutableSetOf() }.add(wrapped) }
        return enqueue(wrapped, delayMs)
    }
    fun removeCallbacks(task: Runnable) {
        synchronized(pending) { pending.remove(task)?.forEach(cancel) }
    }
    fun clear() {
        synchronized(pending) {
            pending.values.flatten().forEach(cancel)
            pending.clear()
        }
    }
}

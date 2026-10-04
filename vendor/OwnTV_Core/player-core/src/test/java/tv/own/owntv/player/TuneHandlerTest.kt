package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class TuneHandlerTest {
    private class Queue {
        val tasks = mutableListOf<Runnable>()
        fun handler(owner: TuneOwnership) = TuneHandler(owner, { task, _ -> tasks.add(task); true }, { tasks.remove(it) })
    }

    @Test fun pendingChoiceBlocksOldRecoveryDuringDebounceBeforeNewSourceExists() {
        val owner = TuneOwnership()
        owner.nextTune(); val a = owner.nextSource()
        val queue = Queue(); val handler = queue.handler(owner)
        var prepares = 0
        handler.postDelayed(Runnable { prepares++ }, 500)
        val lateRetry = queue.tasks.single()
        owner.suspendCurrent() // user picked B, its DB lookup/debounce is still pending
        lateRetry.run()
        assertFalse(owner.accepts(a))
        assertFalse(handler.postFor(a, Runnable { prepares++ })) // slow HTTP result
        assertEquals(0, prepares)
        owner.nextTune(); owner.nextSource()
        handler.post(Runnable { prepares++ })
        queue.tasks.last().run()
        assertEquals(1, prepares)
    }

    @Test fun queuedOldWatchdogCannotRetryOrClearNewPendingState() {
        val owner = TuneOwnership()
        owner.nextTune(); owner.nextSource()
        val queue = Queue(); val handler = queue.handler(owner)
        var pending = true
        var prepares = 0
        handler.postDelayed(Runnable { pending = false; prepares++ }, 500)
        val deliveredLate = queue.tasks.single()
        owner.nextTune(); owner.nextSource() // B
        owner.nextTune(); owner.nextSource() // A, same URL
        deliveredLate.run()
        assertTrue(pending)
        assertEquals(0, prepares)
        handler.post(Runnable { pending = false; prepares++ })
        queue.tasks.last().run()
        assertFalse(pending)
        assertEquals(1, prepares)
    }

    @Test fun httpPostedAfterNewChoiceIsDiscardedWithoutScheduling() {
        val owner = TuneOwnership()
        owner.nextTune(); val a = owner.nextSource()
        val queue = Queue(); val handler = queue.handler(owner)
        owner.nextTune(); val b = owner.nextSource()
        assertFalse(handler.postFor(a, Runnable { error("Late HLS segment") }))
        assertTrue(queue.tasks.isEmpty())
        var providerMessage = ""
        handler.postFor(b, Runnable { providerMessage = "current channel only" })
        queue.tasks.single().run()
        assertEquals("current channel only", providerMessage)
    }

    @Test fun removalCancelsWrappedTimersAndSelfReschedulingKeepsOwnership() {
        val owner = TuneOwnership()
        owner.nextTune(); owner.nextSource()
        val queue = Queue(); val handler = queue.handler(owner)
        var ticks = 0
        val watchdog = object : Runnable {
            override fun run() { ticks++; handler.postDelayed(this, 100) }
        }
        handler.post(watchdog)
        queue.tasks.removeAt(0).run()
        assertEquals(1, ticks)
        handler.removeCallbacks(watchdog)
        assertTrue(queue.tasks.isEmpty())
        handler.post(watchdog)
        val late = queue.tasks.single()
        owner.nextTune(); owner.nextSource()
        handler.clear()
        late.run()
        assertEquals(1, ticks)
        assertTrue(queue.tasks.isEmpty())
    }
}

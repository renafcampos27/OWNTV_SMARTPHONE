package tv.own.owntv.player

import okhttp3.Call
import okhttp3.Response
import okhttp3.EventListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/** Owns transport cancellation acknowledgement and response-body closure independently of terminal metrics. */
internal class PlaybackHttpCalls(private val report: (String) -> Unit = {}) {
    private val lock = Any()
    private val retirementLock = Any()
    private var generation = 0L
    private var retirementScheduled = false
    private var retirementRequested = false
    private class Entry(var active: Boolean = true, var response: Response? = null) {
        val cancelAcknowledged = AtomicBoolean(false)
        var bodyCloseCompleted = false
        var lateCloses = 0
        val locallyClosed: Boolean get() = cancelAcknowledged.get() && bodyCloseCompleted && lateCloses == 0
    }
    private val calls = mutableMapOf<Call, Entry>()
    private val retirementChanges = Channel<Unit>(Channel.CONFLATED)

    /** Wake as soon as closure changes; polling remains a fallback for a missed notification. */
    suspend fun awaitRetirementChange(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { retirementChanges.receive() }
    }

    /** An old media source cannot open another connection even when its URL is reopened. */
    fun factory(delegate: Call.Factory): Call.Factory {
        val owner = synchronized(lock) { generation }
        return Call.Factory { request ->
            val call = delegate.newCall(request)
            val entry = Entry()
            // Keep ownership on the call after its map entry is removed: headers can arrive late.
            call.tag(Entry::class.java) { entry }
            // OkHttp emits canceled AFTER exchange/route cancellation. isCanceled is set before
            // that work, so it is insufficient when Media3 or an internal timeout cancels concurrently.
            // This callback must remain lock-free and must never close a body or perform I/O.
            call.addEventListener(object : EventListener() {
                override fun canceled(call: Call) {
                    entry.cancelAcknowledged.set(true)
                    retirementChanges.trySend(Unit)
                }
            })
            val accepted = synchronized(lock) {
                if (owner != generation) false else { calls[call] = entry; true }
            }
            if (!accepted) call.cancel()
            call
        }
    }

    /** Called before handing headers to Media3. A late response belongs to the retired source. */
    fun opened(call: Call, response: Response): Boolean {
        var rejectedEntry: Entry? = null
        val accepted = synchronized(lock) {
            val entry = calls[call]
            if (call.isCanceled() || entry?.active == false) {
                rejectedEntry = entry ?: call.tag(Entry::class.java)
                rejectedEntry?.let {
                    it.active = false
                    it.lateCloses++
                    it.response = response
                    it.bodyCloseCompleted = false
                    calls[call] = it
                }
                false
            }
            else {
                // A zero-length body may have already emitted callEnd; it owns no pending resource.
                entry?.response = response
                true
            }
        }
        if (!accepted) {
            var closed = false
            try {
                synchronized(retirementLock) {
                    // Headers can beat the retirement worker. Rejecting them must also cancel
                    // the transport: a successful body close alone supplies no canceled ACK.
                    try { call.cancel() } finally { response.close() }
                }
                closed = true
            } finally {
                synchronized(lock) {
                    rejectedEntry?.let {
                        it.lateCloses--
                        it.bodyCloseCompleted = closed
                        if (it.locallyClosed) calls.remove(call)
                    }
                }
                retirementChanges.trySend(Unit)
                if (!closed && rejectedEntry != null) scheduleRetirement()
            }
        }
        return accepted
    }

    fun finished(call: Call) {
        synchronized(lock) {
            val entry = calls[call] ?: return
            // A terminal callback may be re-entered by response.close(). Retired ownership must
            // survive until that close returns successfully, even if the callback arrived first.
            if (entry.active || entry.locallyClosed) calls.remove(call)
        }
        retirementChanges.trySend(Unit)
    }

    /** Local resources still need closing. Diagnostic callEnd/callFailed counts are not this gate. */
    fun hasRetiredCalls(): Boolean = synchronized(lock) {
        calls.entries.removeAll { !it.value.active && it.value.locallyClosed }
        calls.values.any { !it.active }
    }

    /** Immediate invalidation only; no socket/body I/O on the caller (normally the UI thread). */
    fun retireAll() {
        synchronized(lock) {
            generation++
            calls.values.forEach { entry ->
                if (entry.active) entry.bodyCloseCompleted = false
                entry.active = false
            }
        }
    }

    fun retirementSummary(): String = synchronized(lock) {
        val retired = calls.values.filter { !it.active }
        "pending=${retired.size} cancelAckMissing=${retired.count { !it.cancelAcknowledged.get() }} " +
            "bodyCloseMissing=${retired.count { !it.bodyCloseCompleted }} lateCloses=${retired.sumOf { it.lateCloses }}"
    }

    /** Coalesce rapid zaps into one worker per owner, including final cleanup after engine disposal. */
    fun retireAsync() {
        retireAll()
        scheduleRetirement()
    }

    private fun scheduleRetirement() {
        val schedule = synchronized(lock) {
            retirementRequested = true
            if (retirementScheduled) false else { retirementScheduled = true; true }
        }
        if (!schedule) return
        retirementExecutor.execute {
            while (true) {
                val started = System.nanoTime()
                synchronized(lock) { retirementRequested = false }
                // One bounded recovery pass. Persistent failures retain ownership; never hide a leak.
                closeRetired()
                if (hasRetiredCalls()) closeRetired()
                runCatching { report("close_pass elapsedMs=${(System.nanoTime() - started) / 1_000_000L} ${retirementSummary()}") }
                val again = synchronized(lock) {
                    if (retirementRequested) true else { retirementScheduled = false; false }
                }
                if (!again) break
            }
        }
    }

    /** Synchronous helper for transport tests; production uses retireAsync(). */
    fun cancelAll() { retireAll(); closeRetired() }

    private fun closeRetired() = synchronized(retirementLock) {
        val retired = synchronized(lock) {
            calls.filterValues { !it.active && it.lateCloses == 0 }
                .map { (call, entry) -> Triple(call, entry, entry.response) }
        }
        // Never hold the state monitor while cancel/close emits a terminal callback. Serialise
        // retirement separately so a second cancel cannot acknowledge an in-progress first close.
        retired.forEach { (call, entry, response) ->
            var closedWithoutFailure = true
            try { call.cancel() } catch (_: Exception) { closedWithoutFailure = false }
            try { response?.close() } catch (_: Exception) { closedWithoutFailure = false }
            synchronized(lock) {
                // A response arriving during cancellation must close before this owner is forgotten.
                if (entry.response === response) entry.bodyCloseCompleted = closedWithoutFailure
                if (calls[call] === entry && entry.locallyClosed) calls.remove(call)
            }
            retirementChanges.trySend(Unit)
            if (!closedWithoutFailure) runCatching { report("close_failed") }
            // canceled acknowledges local transport cancellation even if DNS delays the terminal
            // event. A missing ACK or failed body close still blocks the next source; late headers
            // are rejected by opened(). This does not acknowledge the provider's remote session.
        }
    }

    private companion object {
        // Workers are reused; coalescing limits concurrent work to the number of playback owners.
        val retirementExecutor = Executors.newCachedThreadPool { task ->
            Thread(task, "owntv-http-retire").apply { isDaemon = true }
        }
    }
}

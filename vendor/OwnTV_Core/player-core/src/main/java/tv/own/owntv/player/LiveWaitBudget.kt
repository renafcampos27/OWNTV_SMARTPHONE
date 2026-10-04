package tv.own.owntv.player

/** A provider countdown has a stable identity; polling it must never credit the same wait twice. */
data class LiveProviderWait(val waitId: Long, val remainingMs: Long)

internal class ProviderWaitCredit(private val maximumMs: Long = MAXIMUM_MS) {
    private val seen = mutableSetOf<Long>()
    private var creditedMs = 0L

    fun observe(wait: LiveProviderWait?, requested: Boolean = true): Long {
        if (wait == null || !seen.add(wait.waitId)) return 0L
        val extra = wait.remainingMs.coerceIn(0L, (maximumMs - creditedMs).coerceAtLeast(0L))
        creditedMs += extra
        return extra
    }

    companion object { const val MAXIMUM_MS = 60_000L }
}

/** One bounded allowance per interruption, earned only by a new source making transport/media progress. */
internal class LiveRecoveryGrace(private val maximumMs: Long = MAXIMUM_MS) {
    private var previous: LiveStartupProgress? = null
    private var credited = false

    fun observe(progress: LiveStartupProgress?): Long {
        if (progress == null || !progress.recovering || credited) return 0L
        val old = previous
        previous = progress
        val progressed = if (old?.tuneId == progress.tuneId && old.sourceId == progress.sourceId) {
            progress.bufferedMs > old.bufferedMs || progress.loadedBytes > old.loadedBytes
        } else progress.bufferedMs > 0L || progress.loadedBytes > 0L
        if (!progressed) return 0L
        credited = true
        return maximumMs.coerceAtLeast(0L)
    }

    companion object { const val MAXIMUM_MS = 5_000L }
}

/** All deadlines use monotonic elapsed time; provider waits and preparation have independent finite caps. */
internal class LiveWaitBudget(private val startedMs: Long, private val baseMs: Long, private val recovery: Boolean = false) {
    private val provider = ProviderWaitCredit()
    private val initial = LiveStartupGrace()
    private val reconnect = LiveRecoveryGrace()
    private var allowanceMs = 0L
    private val pauses = LivePauseAccounting(startedMs)

    fun remaining(nowMs: Long, progress: LiveStartupProgress?, wait: LiveProviderWait?, requested: Boolean = true): Long {
        allowanceMs += pauses.observe(nowMs, requested)
        allowanceMs += provider.observe(wait)
        allowanceMs += if (recovery) reconnect.observe(progress) else initial.observe(progress)
        return (startedMs + baseMs + allowanceMs - nowMs).coerceAtLeast(0L)
    }
}

/** Counts only deliberate paused intervals; preparation/provider caps remain separate and finite. */
internal class LivePauseAccounting(startedMs: Long) {
    private var lastMs = startedMs
    private var wasRequested = true
    fun observe(nowMs: Long, requested: Boolean): Long {
        val paused = if (wasRequested) 0L else (nowMs - lastMs).coerceAtLeast(0L)
        lastMs = nowMs
        wasRequested = requested
        return paused
    }
}

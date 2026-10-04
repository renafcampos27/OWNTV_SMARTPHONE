package tv.own.owntv.player

/** Identity belongs to a choice AND a media source, never to a URL (A -> B -> A). */
internal data class TuneToken(val tuneId: Long, val sourceId: Long)

internal class TuneOwnership {
    @Volatile var current = TuneToken(0, 0)
        private set

    @Volatile var suspended = false
        private set
    @Synchronized fun suspendCurrent() { suspended = true }
    @Synchronized fun nextTune(): TuneToken = TuneToken(current.tuneId + 1, 0).also { current = it; suspended = false }
    @Synchronized fun nextSource(): TuneToken = current.copy(sourceId = current.sourceId + 1).also { current = it }
    @Synchronized fun runIfCurrent(token: TuneToken, action: () -> Unit) {
        if (accepts(token)) action()
    }
    fun accepts(token: TuneToken?): Boolean = !suspended && token != null && token.sourceId > 0 && token == current
}

/** Call lifetime includes streaming response bodies, not just receipt of HTTP headers. */
internal class TuneHttpRequests {
    private val active = mutableMapOf<TuneToken, Int>()
    @Synchronized fun started(token: TuneToken) { active[token] = (active[token] ?: 0) + 1 }
    @Synchronized fun finished(token: TuneToken) {
        val left = (active[token] ?: return) - 1
        if (left <= 0) active.remove(token) else active[token] = left
    }
    @Synchronized fun otherTunes(tuneId: Long): Int = active.filterKeys { it.tuneId != tuneId }.values.sum()
    @Synchronized fun otherSources(token: TuneToken): Int = active.filterKeys { it != token }.values.sum()
    @Synchronized fun snapshot(): String = active.entries.joinToString(",") { "${it.key.tuneId}:${it.key.sourceId}=${it.value}" }
}

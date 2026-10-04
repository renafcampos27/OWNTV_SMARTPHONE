package tv.own.owntv.player

/** A local observation of the active opening attempt; no extra network request is made. */
data class LiveStartupProgress(
    val tuneId: Long,
    val requestedPrerollMs: Long,
    val bufferedMs: Long,
    val loadedBytes: Long = 0,
    val sourceId: Long = 0,
    val recovering: Boolean = false,
)

/**
 * One bounded allowance for initial preparation that is demonstrably making progress.
 * A pre-buffer is media duration, not a compulsory wall-clock wait. The allowance only
 * extends an existing finite deadline; silence and an open socket alone never earn it.
 */
class LiveStartupGrace(private val maximumMs: Long = 10_000L) {
    private var previous: LiveStartupProgress? = null
    private var creditedMs = 0L

    fun observe(progress: LiveStartupProgress?): Long {
        if (progress == null) return 0L
        val old = previous
        previous = progress
        val grew = if (old?.tuneId == progress.tuneId) {
            progress.bufferedMs > old.bufferedMs || progress.loadedBytes > old.loadedBytes
        } else {
            progress.bufferedMs > 0L || progress.loadedBytes > 0L
        }
        if (!grew) return 0L
        val allowance = progress.requestedPrerollMs.coerceIn(0L, maximumMs.coerceAtLeast(0L))
        val additional = (allowance - creditedMs).coerceAtLeast(0L)
        creditedMs += additional
        return additional
    }
}

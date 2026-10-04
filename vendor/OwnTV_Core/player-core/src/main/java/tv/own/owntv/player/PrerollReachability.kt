package tv.own.owntv.player

/** Startup evidence is independent of optional throughput diagnostics. Never treats an HLS poll gap as
 * proof of a ceiling: wait through two segment intervals, and an in-flight request gets the deadline. */
internal class PrerollReachability {
    enum class Limitation { QUIET_SOURCE, DEADLINE }
    private var lastBufferMs = 0L
    private var lastBytes = 0L
    private var lastProgressMs = 0L

    fun reset(nowMs: Long, loadedBytes: Long) {
        lastBufferMs = 0L
        lastBytes = loadedBytes
        lastProgressMs = nowMs
    }

    fun limitation(
        nowMs: Long,
        openedMs: Long,
        targetMs: Long,
        bufferedMs: Long,
        loadedBytes: Long,
        activeRequests: Int,
        segmentDurationMs: Long?,
    ): Limitation? {
        if (bufferedMs > lastBufferMs || loadedBytes > lastBytes) lastProgressMs = nowMs
        lastBufferMs = bufferedMs
        lastBytes = loadedBytes
        if (targetMs <= 0 || bufferedMs >= targetMs) return null
        // Unknown HLS cadence is conservatively 10 seconds; raw TS passes zero.
        val cadenceMs = (segmentDurationMs ?: 10_000L).coerceIn(0L, 20_000L)
        val quietMs = maxOf(5_000L, cadenceMs * 2 + 2_000L)
        val deadlineMs = (targetMs + maxOf(10_000L, cadenceMs * 2 + 5_000L)).coerceAtMost(60_000L)
        if (nowMs - openedMs >= deadlineMs) return Limitation.DEADLINE
        if (bufferedMs > 0 && activeRequests == 0 && nowMs - lastProgressMs >= quietMs) {
            return Limitation.QUIET_SOURCE
        }
        return null
    }
}

/** Source-specific, thread-safe transfer counters, including the lifetime of each response body. */
internal class StartupTransfers {
    private var active = 0
    private var bytes = 0L
    @Synchronized fun started() { active++ }
    @Synchronized fun ended() { active = (active - 1).coerceAtLeast(0) }
    @Synchronized fun transferred(count: Int) { if (count > 0) bytes += count }
    @Synchronized fun snapshot(): Pair<Int, Long> = active to bytes
}

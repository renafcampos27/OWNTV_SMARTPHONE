package tv.own.owntv.player

/** A seekable server live window; independent of provider catch-up and allocator buffer size. */
data class LiveSeekWindow(
    val positionMs: Long,
    val durationMs: Long,
    val defaultPositionMs: Long,
    val bufferedMs: Long,
) {
    /** Null means return to the player's safe default live position. */
    fun targetAfter(deltaMs: Long): Long? {
        val delta = deltaMs.coerceIn(-durationMs, durationMs)
        val target = (positionMs + delta).coerceIn(0L, durationMs)
        return if (deltaMs > 0 && target >= defaultPositionMs) null else target
    }

    val behindDefaultMs: Long get() = (defaultPositionMs - positionMs).coerceAtLeast(0)

    companion object {
        fun snapshot(positionMs: Long, durationMs: Long, defaultPositionMs: Long, bufferedMs: Long): LiveSeekWindow? {
            if (durationMs <= 0 || positionMs < 0 || defaultPositionMs !in 0..durationMs) return null
            return LiveSeekWindow(positionMs.coerceAtMost(durationMs), durationMs, defaultPositionMs,
                bufferedMs.coerceIn(0L, durationMs))
        }
    }
}

package tv.own.owntv.player

/** Coalesce presentation only. Cache/reconnect decisions continue using the precise native value. */
internal class BufferUiSample {
    private var second: Long? = null
    private var buffered: Long? = null

    @Synchronized fun reset() { second = null; buffered = null }

    @Synchronized fun sample(positionMs: Long, cacheSeconds: Double): Long? {
        if (!cacheSeconds.isFinite() || cacheSeconds < 0) return null
        val wholeSeconds = cacheSeconds.toLong()
        val end = positionMs + wholeSeconds * 1000L
        if (second == wholeSeconds && buffered == end) return null
        second = wholeSeconds
        buffered = end
        return end
    }
}

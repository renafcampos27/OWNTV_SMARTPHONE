package tv.own.owntv.player

/** Cache growth is progress while native playback waits for cache, not a reconnect success. */
internal class MpvCacheRefill {
    private var previous = Double.NaN
    fun isGrowing(pausedForCache: Boolean, seconds: Double): Boolean {
        val growing = pausedForCache && seconds.isFinite() && seconds >= 0 &&
            previous.isFinite() && seconds > previous
        previous = seconds.takeIf { it.isFinite() && it >= 0 } ?: Double.NaN
        return growing
    }
}

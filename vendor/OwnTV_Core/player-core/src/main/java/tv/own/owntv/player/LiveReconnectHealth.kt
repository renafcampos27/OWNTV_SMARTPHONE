package tv.own.owntv.player

/** A brief recovery does not buy an unreliable stream an unlimited number of reconnects. */
internal class LiveReconnectHealth(private val stableMs: Long = 60_000L) {
    private var healthySince: Long? = null

    fun observe(nowMs: Long, healthy: Boolean): Boolean {
        if (!healthy) {
            healthySince = null
            return false
        }
        val start = healthySince ?: nowMs.also { healthySince = it }
        return nowMs - start >= stableMs
    }
}

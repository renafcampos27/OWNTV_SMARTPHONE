package tv.own.owntv.player

/** A decoded first frame alone does not prove the archive is advancing. */
internal class ArchiveFirstFrameGuard(
    private val timeoutMs: Long = 8_000L,
    private val proofMs: Long = 2_000L,
) {
    private var firstPosition: Long? = null
    private var stillSince: Long? = null
    var confirmed: Boolean = false
        private set

    fun stalled(nowMs: Long, positionMs: Long, requested: Boolean, playing: Boolean, buffering: Boolean, audioOnly: Boolean): Boolean {
        if (audioOnly) { confirmed = true; return false }
        val first = firstPosition ?: positionMs.also { firstPosition = it }
        if (positionMs > first + proofMs) { confirmed = true; return false }
        if (!requested || !playing || buffering) { stillSince = null; return false }
        val since = stillSince ?: nowMs.also { stillSince = it }
        return nowMs - since >= timeoutMs
    }
}

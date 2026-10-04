package tv.own.owntv.player

/** Constant-space playback measurements. The owner supplies its already-correlated events. */
internal class PlaybackQoe(private val clockMs: () -> Long) {
    data class BufferEpisode(val durationMs: Long, val duringPlayback: Boolean)
    data class Snapshot(val rebufferCount: Int, val rebufferMs: Long, val rebuildCount: Int)

    private var confirmedPlayback = false
    private var bufferStartedMs: Long? = null
    private var bufferDuringPlayback = false
    private var rebufferCount = 0
    private var rebufferMs = 0L
    private var rebuildCount = 0

    fun reset() {
        confirmedPlayback = false
        bufferStartedMs = null
        rebufferCount = 0
        rebufferMs = 0L
        rebuildCount = 0
    }

    fun confirmPlayback() { confirmedPlayback = true }
    fun rebuilt() { rebuildCount++ }

    fun buffering(requested: Boolean) {
        if (!requested || bufferStartedMs != null) return
        bufferStartedMs = clockMs()
        bufferDuringPlayback = confirmedPlayback
    }

    fun finishBuffer(): BufferEpisode? {
        val started = bufferStartedMs ?: return null
        bufferStartedMs = null
        val duration = (clockMs() - started).coerceAtLeast(0L)
        if (bufferDuringPlayback) {
            rebufferCount++
            rebufferMs += duration
        }
        return BufferEpisode(duration, bufferDuringPlayback)
    }

    /** A manual pause or cancellation is excluded from completed recovery measurements. */
    fun suspendMeasurement() { bufferStartedMs = null }
    fun snapshot() = Snapshot(rebufferCount, rebufferMs, rebuildCount)
}

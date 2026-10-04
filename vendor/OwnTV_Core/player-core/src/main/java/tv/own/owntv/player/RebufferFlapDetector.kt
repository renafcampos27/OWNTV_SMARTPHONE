package tv.own.owntv.player

/** Detect sustained READY/BUFFERING oscillation without penalising brief immediate-resume bursts. */
internal data class RebufferFlapDetector(
    private val windowMs: Long = 6_000L,
    private val minimumTransitions: Int = 12,
    private val minimumProgressMs: Long = 3_000L,
    private var windowStartMs: Long? = null,
    private var windowStartPositionMs: Long = 0L,
    private var transitions: Int = 0,
) {
    data class Evidence(val transitions: Int, val elapsedMs: Long, val advancedMs: Long)

    fun reset() {
        windowStartMs = null
        windowStartPositionMs = 0L
        transitions = 0
    }

    fun buffering(nowMs: Long, positionMs: Long): Evidence? {
        val start = windowStartMs
        if (start == null) {
            startWindow(nowMs, positionMs)
            return null
        }
        val elapsedMs = nowMs - start
        val advancedMs = positionMs - windowStartPositionMs
        // A seek, a discontinuity or useful playback starts a fresh observation window.
        if (elapsedMs < 0 || elapsedMs > windowMs * 2 || advancedMs < 0 || advancedMs >= minimumProgressMs) {
            startWindow(nowMs, positionMs)
            return null
        }
        transitions++
        if (elapsedMs < windowMs) return null
        val evidence = Evidence(transitions, elapsedMs, advancedMs)
        startWindow(nowMs, positionMs)
        return evidence.takeIf { it.transitions >= minimumTransitions }
    }

    private fun startWindow(nowMs: Long, positionMs: Long) {
        windowStartMs = nowMs
        windowStartPositionMs = positionMs
        transitions = 1
    }
}

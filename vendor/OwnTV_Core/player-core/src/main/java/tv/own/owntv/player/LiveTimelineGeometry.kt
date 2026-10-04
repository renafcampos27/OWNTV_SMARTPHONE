package tv.own.owntv.player

/** Default visible live timeline window; callers can supply the channel archive window. */
const val LIVE_WINDOW_SEC = 2 * 3600

/** One guide entry as the timeline needs it: two timestamps and something to call it. */
data class LiveProgramme(val startMs: Long, val stopMs: Long, val title: String)

/** A programme's span on the bar, 0 = the far (oldest) end, 1 = the live edge. */
data class LiveTick(val title: String, val startFrac: Float, val endFrac: Float)

/** Where a watched point sitting [offsetSec] behind live falls on the bar. */
fun offsetFrac(offsetSec: Int, windowSec: Int = LIVE_WINDOW_SEC): Float =
    (1f - offsetSec.toFloat() / windowSec.coerceAtLeast(1)).coerceIn(0f, 1f)

/**
 * [programmes] placed on the bar, clipped to the window. Anything wholly older than the window, or
 * still in the future, is left off — the bar only covers what can actually be scrubbed to.
 */
fun liveTicks(programmes: List<LiveProgramme>, liveEdgeMs: Long, windowSec: Int = LIVE_WINDOW_SEC): List<LiveTick> {
    val windowStart = liveEdgeMs - windowSec.coerceAtLeast(1) * 1000L
    return programmes.mapNotNull { p ->
        if (p.stopMs <= windowStart || p.startMs >= liveEdgeMs) return@mapNotNull null
        LiveTick(p.title, frac(p.startMs, liveEdgeMs, windowSec), frac(p.stopMs, liveEdgeMs, windowSec))
    }
}

/** The programme covering a point [offsetSec] behind live, or null where the guide has a gap. */
fun programmeAt(programmes: List<LiveProgramme>, liveEdgeMs: Long, offsetSec: Int): LiveProgramme? {
    val at = liveEdgeMs - offsetSec.coerceAtLeast(0) * 1000L
    return programmes.firstOrNull { at >= it.startMs && at < it.stopMs } ?: programmes.firstOrNull { at == it.stopMs }
}

private fun frac(atMs: Long, liveEdgeMs: Long, windowSec: Int): Float =
    (1f - (liveEdgeMs - atMs).toFloat() / (windowSec.coerceAtLeast(1) * 1000L)).coerceIn(0f, 1f)

/** A stretch of the bar, 0 = the far (oldest) end, 1 = the live edge. */
data class LiveSpan(val startFrac: Float, val endFrac: Float)

/**
 * N4 — the holes in a channel's saved copy (wall-clock spans with no picture, where the connection
 * dropped) placed on the bar, clipped to the window; empty spans are left off.
 */
fun liveGapSpans(gaps: List<LongRange>, liveEdgeMs: Long, windowSec: Int = LIVE_WINDOW_SEC): List<LiveSpan> =
    gaps.map { LiveSpan(frac(it.first, liveEdgeMs, windowSec), frac(it.last, liveEdgeMs, windowSec)) }
        .filter { it.endFrac > it.startFrac }

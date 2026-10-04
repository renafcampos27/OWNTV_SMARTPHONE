package tv.own.owntv.core.epg

import tv.own.owntv.core.live.DEFAULT_CATCHUP_DAYS
import tv.own.owntv.core.live.MAX_CATCHUP_DAYS

/** Guide history is browsable even when a channel has no replay service. */
object GuideHistoryPolicy {
    const val LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000
    const val MAX_LOOKBACK_MS = MAX_CATCHUP_DAYS * 24L * 60 * 60 * 1000

    /** Shared lower boundary for stored programmes and the lazily read Guide window. */
    fun windowStart(nowMs: Long, declaredDays: Int = DEFAULT_CATCHUP_DAYS): Long =
        nowMs - declaredDays.coerceIn(DEFAULT_CATCHUP_DAYS, MAX_CATCHUP_DAYS) * 86_400_000L

    /** A deliberate attempt can exceed stale provider metadata; it never grants confirmed availability. */
    fun canAttemptCatchup(catchup: Boolean, programmeStartMs: Long, nowMs: Long): Boolean =
        catchup && programmeStartMs <= nowMs && nowMs - programmeStartMs <= MAX_LOOKBACK_MS

    /** Browsing a past programme never grants an archive capability to a live-only channel. */
    fun canCatchup(catchup: Boolean, declaredDays: Int, programmeStartMs: Long, nowMs: Long): Boolean {
        if (!catchup || programmeStartMs > nowMs) return false
        val days = (declaredDays.takeIf { it > 0 } ?: DEFAULT_CATCHUP_DAYS).coerceAtMost(MAX_CATCHUP_DAYS)
        return nowMs - programmeStartMs <= days * 24L * 60 * 60 * 1000
    }
}

package tv.own.owntv.mobile.ui.screens.guide

import java.util.Calendar
import java.util.TimeZone

/** Calendar arithmetic keeps day windows correct across midnight and daylight-saving changes. */
internal object GuideTimePolicy {
    const val EMPTY_TTL_MS = 30_000L

    fun window(offset: Int, atMs: Long, zone: TimeZone = TimeZone.getDefault()): GuideWindow {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = atMs
            if (offset == 0) {
                set(Calendar.MINUTE, if (get(Calendar.MINUTE) < 30) 0 else 30)
            } else {
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
            }
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        return GuideWindow(start, cal.timeInMillis)
    }

    fun expiry(atMs: Long, programmeEndMs: Long?, nextStartMs: Long?): Long =
        programmeEndMs?.takeIf { it > atMs }
            ?: minOf(atMs + EMPTY_TTL_MS, nextStartMs?.takeIf { it > atMs } ?: Long.MAX_VALUE)

    fun isFresh(loadedAtMs: Long, expiresAtMs: Long, atMs: Long): Boolean =
        atMs >= loadedAtMs && atMs < expiresAtMs
}

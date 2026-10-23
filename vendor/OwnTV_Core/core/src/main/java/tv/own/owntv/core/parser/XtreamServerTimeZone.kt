package tv.own.owntv.core.parser

import java.util.TimeZone

/** TimeZone.getTimeZone silently returns GMT for unknown input; never accept that as panel data. */
internal fun validatedPanelTimeZone(value: String): TimeZone? {
    val id = value.trim()
    if (id in TimeZone.getAvailableIDs()) return TimeZone.getTimeZone(id)
    if (!Regex("GMT[+-](?:0[0-9]|1[0-4]):[0-5][0-9]").matches(id)) return null
    return TimeZone.getTimeZone(id).takeIf { it.rawOffset in -12 * 3_600_000..14 * 3_600_000 }
}

/** Use a real zone (DST-aware); only infer a fixed offset when the panel supplied both clocks. */
internal fun xtreamServerTimeZone(name: String?, timeNow: String?, timestampNowSec: Long?): String? {
    name?.let(::validatedPanelTimeZone)?.let { return it.id }
    if (timeNow.isNullOrBlank() || timestampNowSec == null || timestampNowSec !in 1..4_133_980_800L) return null
    val raw = timeNow.trim()
    val position = java.text.ParsePosition(0)
    val parsed = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
        .apply { timeZone = TimeZone.getTimeZone("UTC"); isLenient = false }.parse(raw, position) ?: return null
    if (position.index != raw.length) return null
    val quarters = Math.round((parsed.time - timestampNowSec * 1000L) / 900_000.0)
    if (quarters !in -48..56) return null
    val minutes = kotlin.math.abs(quarters) * 15
    return "GMT${if (quarters < 0) '-' else '+'}${(minutes / 60).toString().padStart(2, '0')}:${(minutes % 60).toString().padStart(2, '0')}"
}

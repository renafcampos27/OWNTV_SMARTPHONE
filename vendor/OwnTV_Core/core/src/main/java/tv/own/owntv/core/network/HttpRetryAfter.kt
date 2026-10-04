package tv.own.owntv.core.network

import java.time.ZonedDateTime
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** Server delay, without a UI ceiling: ending a wait must never authorize an earlier request. */
object HttpRetryAfter {
    fun delayMs(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (text.all { it in '0'..'9' }) {
            val seconds = text.toLongOrNull() ?: return Long.MAX_VALUE
            return if (seconds > Long.MAX_VALUE / 1000L) Long.MAX_VALUE else seconds * 1000L
        }
        val deadline = runCatching {
            ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
        }.getOrNull() ?: obsoleteDate(text, nowMs) ?: return null
        if (deadline <= nowMs) return 0L
        return if (nowMs < 0L && deadline > Long.MAX_VALUE + nowMs) Long.MAX_VALUE else deadline - nowMs
    }

    /** RFC 9110 recipients also accept RFC 850 and asctime timestamps. */
    private fun obsoleteDate(text: String, nowMs: Long): Long? = runCatching {
        val parsed = if (text.contains(',')) {
            val received = Instant.ofEpochMilli(nowMs).atOffset(ZoneOffset.UTC)
            val formatter = DateTimeFormatterBuilder().appendPattern("dd-MMM-")
                .appendValueReduced(ChronoField.YEAR, 2, 2, received.year - 49)
                .appendPattern(" HH:mm:ss 'GMT'").toFormatter(Locale.US)
            val candidate = LocalDateTime.parse(text.substringAfter(',').trim(), formatter)
            if (candidate.isAfter(received.toLocalDateTime().plusYears(50))) candidate.minusYears(100) else candidate
        } else {
            // asctime pads a single-digit day with an extra space.
            val date = text.substringAfter(' ').trim().replace(Regex(" +"), " ")
            LocalDateTime.parse(date, DateTimeFormatter.ofPattern("MMM d HH:mm:ss uuuu", Locale.US))
        }
        parsed.toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()
}

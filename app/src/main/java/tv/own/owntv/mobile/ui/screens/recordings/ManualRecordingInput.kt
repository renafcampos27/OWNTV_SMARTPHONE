package tv.own.owntv.mobile.ui.screens.recordings

import java.time.LocalDate
import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object ManualRecordingInput {
    fun resolve(date: String, start: String, end: String, zone: ZoneId): LongRange? = try {
        val day = LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT))
        val format = DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT)
        val from = LocalTime.parse(start.trim(), format)
        val until = LocalTime.parse(end.trim(), format)
        val first = day.atTime(from)
        val last = (if (until <= from) day.plusDays(1) else day).atTime(until)
        fun instant(value: LocalDateTime): Long? {
            val offsets = zone.rules.getValidOffsets(value)
            if (offsets.isEmpty()) return null
            return ZonedDateTime.ofStrict(value, offsets.first(), zone).toInstant().toEpochMilli()
        }
        val a = instant(first); val b = instant(last)
        if (a == null || b == null || b <= a || b-a > 86_400_000L) null else a..b
    } catch (_: java.time.DateTimeException) { null }
}

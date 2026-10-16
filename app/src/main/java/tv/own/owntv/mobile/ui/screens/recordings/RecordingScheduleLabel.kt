package tv.own.owntv.mobile.ui.screens.recordings

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import tv.own.owntv.mobile.R

internal enum class RecordingScheduleDay { TODAY, TOMORROW, DATE, DATE_WITH_YEAR }

internal fun recordingScheduleDay(start: LocalDate, today: LocalDate): RecordingScheduleDay = when {
    start == today -> RecordingScheduleDay.TODAY
    start == today.plusDays(1) -> RecordingScheduleDay.TOMORROW
    start.year != today.year -> RecordingScheduleDay.DATE_WITH_YEAR
    else -> RecordingScheduleDay.DATE
}

internal fun scheduledDateTime(context: Context, startMs: Long): String {
    val zone = ZoneId.systemDefault()
    val day = recordingScheduleDay(Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate(), LocalDate.now(zone))
    val date = when (day) {
        RecordingScheduleDay.TODAY -> context.getString(R.string.own_recording_today)
        RecordingScheduleDay.TOMORROW -> context.getString(R.string.own_recording_tomorrow)
        else -> DateUtils.formatDateTime(context, startMs,
            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH or
                (if (day == RecordingScheduleDay.DATE_WITH_YEAR) DateUtils.FORMAT_SHOW_YEAR else DateUtils.FORMAT_NO_YEAR))
    }
    return context.getString(R.string.own_recording_date_time, date, DateFormat.getTimeFormat(context).format(Date(startMs)))
}

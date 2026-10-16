package tv.own.owntv.mobile.ui.screens.recordings

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingScheduleLabelTest {
    @Test fun `tomorrow is relative across year boundaries`() {
        assertEquals(RecordingScheduleDay.TOMORROW,
            recordingScheduleDay(LocalDate.of(2027, 1, 1), LocalDate.of(2026, 12, 31)))
    }

    @Test fun `other years show the year and current year dates do not`() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals(RecordingScheduleDay.DATE_WITH_YEAR, recordingScheduleDay(LocalDate.of(2027, 2, 1), today))
        assertEquals(RecordingScheduleDay.DATE, recordingScheduleDay(LocalDate.of(2026, 10, 9), today))
        assertEquals(RecordingScheduleDay.TODAY, recordingScheduleDay(today, today))
    }

    @Test fun `tomorrow follows calendar days across daylight saving changes`() {
        assertEquals(RecordingScheduleDay.TOMORROW,
            recordingScheduleDay(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 24)))
    }
}

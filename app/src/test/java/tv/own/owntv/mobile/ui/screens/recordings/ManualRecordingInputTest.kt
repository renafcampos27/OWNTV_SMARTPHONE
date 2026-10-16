package tv.own.owntv.mobile.ui.screens.recordings

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ManualRecordingInputTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    @Test fun overnightUsesNextLocalDate() {
        val window = ManualRecordingInput.resolve("09/10/2026", "23:30", "00:30", zone)!!
        assertEquals(Instant.parse("2026-10-09T22:30:00Z").toEpochMilli(), window.first)
        assertEquals(3_600_000L, window.last-window.first)
    }
    @Test fun invalidDatesAndTimesAreRejected() {
        assertNull(ManualRecordingInput.resolve("31/02/2026", "12:00", "13:00", zone))
        assertNull(ManualRecordingInput.resolve("09/10/2026", "25:00", "13:00", zone))
    }
    @Test fun nonexistentSpringHourIsRejected() {
        assertNull(ManualRecordingInput.resolve("29/03/2026", "01:30", "03:00", zone))
    }
    @Test fun repeatedHourUsesFirstOccurrence() {
        val window = ManualRecordingInput.resolve("25/10/2026", "01:30", "02:30", zone)!!
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), window.first)
        assertEquals(7_200_000L, window.last-window.first)
    }
    @Test fun fullDayAtFallTransitionCannotExceed24ElapsedHours() {
        assertNull(ManualRecordingInput.resolve("24/10/2026", "12:00", "12:00", zone))
        assertEquals(86_400_000L, ManualRecordingInput.resolve("09/10/2026", "12:00", "12:00", zone)!!.let { it.last-it.first })
    }
}

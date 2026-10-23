package tv.own.owntv.mobile.ui.screens.guide

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class GuideProgrammeCellsTest {
    private fun programme(id: Long, start: Long, stop: Long) = EpgProgrammeEntity(id, 1, "epg", start, stop, "Title")
    @Test fun enclosingProgrammeResumesAfterShortOverlapKeepingOriginalRequest() {
        val long = programme(1, -20, 100)
        val short = programme(2, 40, 50)
        val cells = guideProgrammeCells(listOf(long, short), GuideWindow(0, 110))
        assertEquals(listOf(0L to 40L, 40L to 50L, 50L to 100L), cells.map { it.startMs to it.endMs })
        assertSame(long, cells.last().programme)
        assertEquals(-20L, cells.last().programme.startMs)
    }
    @Test fun gapsAndInvalidEventsNeverInventProgrammes() {
        val cells = guideProgrammeCells(listOf(programme(1, 10, 20), programme(2, 30, 40), programme(3, 60, 50)), GuideWindow(0, 100))
        assertEquals(listOf(10L to 20L, 30L to 40L), cells.map { it.startMs to it.endMs })
    }
    @Test fun equalStartKeepsPreferredReaderOrderAndContinuation() {
        val preferred = programme(1, 10, 20)
        val other = programme(2, 10, 40)
        assertEquals(listOf(1L, 2L), guideProgrammeCells(listOf(preferred, other), GuideWindow(0, 100)).map { it.programme.id })
    }
    @Test fun viewportUsesExclusiveEndsAndKeepsOriginalEntity() {
        val rows = listOf(programme(1, 0, 10), programme(2, 10, 20), programme(3, 20, 30))
        val cells = guideVisibleCells(guideProgrammeCells(rows, GuideWindow(0, 100)), 10, 20)
        assertEquals(listOf(2L), cells.map { it.programme.id })
        assertSame(rows[1], cells.single().programme)
    }
    @Test fun emptyAndReversedWindowsAreEmpty() {
        assertTrue(guideProgrammeCells(emptyList(), GuideWindow(0, 10)).isEmpty())
        assertTrue(guideProgrammeCells(listOf(programme(1, 0, 10)), GuideWindow(10, 0)).isEmpty())
    }
}

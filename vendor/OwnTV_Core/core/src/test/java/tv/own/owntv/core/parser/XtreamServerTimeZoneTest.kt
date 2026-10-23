package tv.own.owntv.core.parser

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.settings.SourceOverrides

class XtreamServerTimeZoneTest {
    @Test fun namedPanelZonePreservesDstInsteadOfFreezingTodaysOffset() {
        assertEquals("Europe/Lisbon", xtreamServerTimeZone(" Europe/Lisbon ", null, null))
        val source = SourceEntity(name = "panel", type = SourceType.XTREAM, url = "https://example.invalid",
            serverTimeZone = "Europe/Lisbon", catchupTimezone = "AUTOMATIC")
        val zone = SourceOverrides.catchupTimeZoneOf(source)!!
        assertEquals(0, zone.getOffset(Instant.parse("2026-01-01T12:00:00Z").toEpochMilli()))
        assertEquals(3_600_000, zone.getOffset(Instant.parse("2026-07-01T12:00:00Z").toEpochMilli()))
        assertEquals(7_200_000, SourceOverrides.catchupTimeZoneOf(source.copy(catchupTimezone = "MANUAL", catchupOffsetMin = 120))!!.rawOffset)
    }
    @Test fun fixedOffsetRequiresTwoValidClocksAndInvalidIdsNeverBecomeGmt() {
        val unix = Instant.parse("2026-10-18T12:00:00Z").epochSecond
        assertEquals("GMT+05:45", xtreamServerTimeZone("bad-zone", "2026-10-18 17:45:00", unix))
        assertEquals("GMT-03:30", xtreamServerTimeZone(null, "2026-10-18 08:30:00", unix))
        assertNull(xtreamServerTimeZone("bad-zone", null, unix))
        assertNull(xtreamServerTimeZone(null, "2026-10-18 12:00:00 junk", unix))
        assertNull(xtreamServerTimeZone(null, "2026-02-31 12:00:00", unix))
        assertNull(xtreamServerTimeZone(null, "2026-10-18 12:00:00", Long.MAX_VALUE))
        assertNull(validatedPanelTimeZone("Not/Real"))
        assertNull(validatedPanelTimeZone("GMT+15:00"))
    }
    @Test fun invalidOrNonXtreamPanelMetadataIsIgnored() {
        val source = SourceEntity(name = "panel", type = SourceType.XTREAM, url = "https://example.invalid", serverTimeZone = "bad-zone")
        assertNull(SourceOverrides.panelTimeZoneOf(source))
        assertNull(SourceOverrides.panelTimeZoneOf(source.copy(type = SourceType.M3U, serverTimeZone = "UTC")))
    }
    @Test fun accountBodyCapAppliesToBothSingleAndBulkReads() {
        assertEquals(4, BoundedAccountInput(ByteArray(4).inputStream(), 4).readBytes().size)
        assertTrue(runCatching { BoundedAccountInput(ByteArray(5).inputStream(), 4).readBytes() }.isFailure)
        val single = BoundedAccountInput(ByteArray(5).inputStream(), 4)
        repeat(4) { assertEquals(0, single.read()) }
        assertTrue(runCatching { single.read() }.isFailure)
    }
}

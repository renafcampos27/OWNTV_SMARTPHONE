package tv.own.owntv.core.epg

import java.time.Instant
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.computeContentHash

class CatchupTemplates68Test {
    private val start = Instant.parse("2026-10-18T12:00:00Z").toEpochMilli()
    @Test fun formattedInstantsAndTimestampUseTheirOwnUtcClock() {
        val template = "{utc:Y-m-d_H:M:S}|\${end:HMS}|{timestamp:H-M}|{duration:60}|{offset:60}"
        assertEquals("2026-10-18_12:00:00|130000|14-00|60|120",
            CatchupUrl.fromTemplate(template, start, start + 3_600_000, start + 7_200_000))
        assertEquals((start / 1000 + 7200).toString(), CatchupUrl.fromTemplate("\${timestamp}", start, start + 3600000, start + 7200000))
    }
    @Test fun invalidDivisorsAndUnknownTokensArePreserved() {
        val template = "{duration:0}/{offset:-2}/{utc:}/{unknown:x}"
        assertEquals(template, CatchupUrl.fromTemplate(template, start, start + 1000, start + 2000))
    }
    @Test fun providerCorrectionAndExplicitRequestShiftEachApplyOnceWithoutChangingDisplay() {
        val display = EpgProgrammeEntity(sourceId = 1, epgChannelId = "ch", startMs = start,
            stopMs = start + 3_600_000, title = "Display")
        val request = CatchupUrl.requestProgramme(display, 60)
        val url = CatchupUrl.forM3u("https://example.invalid/live", "append",
            "?start={utc:HMS}&end={utcend:HMS}", request.startMs, request.stopMs,
            TimeZone.getTimeZone("America/New_York"), correctionMinutes = -30)
        assertEquals("https://example.invalid/live?start=123000&end=133000", url)
        assertEquals(start, display.startMs)
        assertEquals(start + 3_600_000, display.stopMs)
    }
    @Test fun correctionChangesOnlyChannelHashesThatUseIt() {
        val channel = ChannelEntity(sourceId = 1, name = "normal", streamUrl = "https://example.invalid/live")
        assertEquals(channel.computeContentHash(), channel.copy(catchupCorrectionMin = 0).computeContentHash())
        assertNotEquals(channel.computeContentHash(), channel.copy(catchupCorrectionMin = 60).computeContentHash())
    }
}

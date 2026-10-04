package tv.own.owntv.core.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

class CatchupRequestTimeTest {
    private val programme = EpgProgrammeEntity(
        sourceId = 1, epgChannelId = "channel", title = "Programme",
        startMs = 1_609_459_200_000L, stopMs = 1_609_462_800_000L,
    )

    @Test fun zeroCorrectionKeepsExistingRequestAndInstance() {
        assertSame(programme, CatchupUrl.requestProgramme(programme, 0))
        assertEquals(programme.startMs, CatchupUrl.requestStartMs(programme.startMs, 0))
    }

    @Test fun positiveCorrectionChangesRequestWithoutChangingGuideOrDuration() {
        val request = CatchupUrl.requestProgramme(programme, 60)
        assertEquals(programme.startMs + 3_600_000L, request.startMs)
        assertEquals(programme.stopMs + 3_600_000L, request.stopMs)
        assertEquals(programme.stopMs - programme.startMs, request.stopMs - request.startMs)
        assertEquals(1_609_459_200_000L, programme.startMs)
        assertEquals(programme.title, request.title)
        assertEquals(request.startMs, CatchupUrl.requestStartMs(programme.startMs, 60))
    }

    @Test fun negativeCorrectionWorksAcrossMidnight() {
        val request = CatchupUrl.requestProgramme(programme, -60)
        assertEquals(1_609_455_600_000L, request.startMs)
        assertEquals("http://archive/2020-12-31/23-00/3600",
            CatchupUrl.fromTemplate("http://archive/{Y}-{m}-{d}/{H}-{M}/{duration}", request.startMs, request.stopMs))
    }

    @Test fun archiveTemplateReceivesCorrectedTimeButOriginalDuration() {
        val request = CatchupUrl.requestProgramme(programme, 60)
        assertEquals("http://archive?start=1609462800&duration=3600",
            CatchupUrl.fromTemplate("http://archive?start={start}&duration={duration}", request.startMs, request.stopMs))
    }

    @Test fun guideCorrectionAndRequestCorrectionAreIndependent() {
        val displayed = EpgShift.apply(programme, 30)
        val requested = CatchupUrl.requestProgramme(displayed, 60)
        assertEquals(programme.startMs + 1_800_000L, displayed.startMs)
        assertEquals(programme.startMs + 5_400_000L, requested.startMs)
        assertEquals(displayed, CatchupUrl.requestProgramme(displayed, 0))
    }
}

package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

class RecordingRecoveryPolicyTest {
    private val day = 86_400_000L
    private val now = 20 * day
    private fun row(status: RecordingStatus = RecordingStatus.PARTIAL, start: Long = now - day,
        stop: Long = start + 3_600_000) = RecordingEntity(profileId = 1, sourceId = 2,
        channelId = 3, channelName = "channel", streamUrl = "https://example.invalid/live",
        title = "programme", programmeStartMs = start, programmeStopMs = stop,
        startMs = start, stopMs = stop, status = status)

    @Test fun `finished or cancelled files are never automatically replaced`() {
        for (status in listOf(RecordingStatus.COMPLETED, RecordingStatus.CANCELLED, RecordingStatus.RECORDING, RecordingStatus.SCHEDULED))
            assertEquals(RecordingRecoveryPolicy.Mode.NONE, RecordingRecoveryPolicy.mode(row(status), now, true, 7))
    }

    @Test fun `lost programme can be recovered from declared archive`() {
        for (status in listOf(RecordingStatus.PARTIAL, RecordingStatus.FAILED, RecordingStatus.MISSED))
            assertEquals(RecordingRecoveryPolicy.Mode.ARCHIVE, RecordingRecoveryPolicy.mode(row(status), now, true, 7))
    }

    @Test fun `live programme without archive can still capture its remainder`() {
        assertEquals(RecordingRecoveryPolicy.Mode.LIVE,
            RecordingRecoveryPolicy.mode(row(stop = now + 60_000), now, false, 0))
    }

    @Test fun `complete programme must fit archive from its start not merely its end`() {
        val old = row(start = now - 7 * day - 1, stop = now - 7 * day + 3_600_000)
        assertEquals(RecordingRecoveryPolicy.Mode.NONE, RecordingRecoveryPolicy.mode(old, now, true, 7))
        assertEquals(RecordingRecoveryPolicy.Mode.NONE, RecordingRecoveryPolicy.mode(row(), now, false, 7))
    }

    @Test fun `missing archive duration uses shared week and invalid programme is refused`() {
        assertEquals(RecordingRecoveryPolicy.Mode.ARCHIVE, RecordingRecoveryPolicy.mode(row(), now, true, 0))
        assertEquals(RecordingRecoveryPolicy.Mode.NONE,
            RecordingRecoveryPolicy.mode(row(start = now, stop = now), now, true, 7))
    }
}

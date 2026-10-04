package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

class RecordingActionPolicyTest {
    private val row = RecordingEntity(id = 4, profileId = 1, sourceId = 2, channelId = 3,
        channelName = "channel", streamUrl = "https://example.invalid/live", title = "programme",
        programmeStartMs = 100, programmeStopMs = 200, startMs = 100, stopMs = 200)
    @Test fun identityIsScopedToProfileSourceAndChannel() {
        assertTrue(recordingActionMatches(row, row))
        listOf(row.copy(id = 5), row.copy(profileId = 2), row.copy(sourceId = 3), row.copy(channelId = 4)).forEach {
            assertFalse(recordingActionMatches(row, it))
        }
        assertFalse(recordingActionMatches(row, null))
    }
    @Test fun staleScheduledMenuCannotCancelRunningRecording() {
        assertFalse(recordingActionMatches(row, row.copy(status = RecordingStatus.RECORDING), setOf(RecordingStatus.SCHEDULED)))
        assertTrue(recordingActionMatches(row, row, setOf(RecordingStatus.SCHEDULED)))
    }
    @Test fun stopCannotRewriteCompletedOrPartialJob() {
        listOf(RecordingStatus.COMPLETED, RecordingStatus.PARTIAL, RecordingStatus.FAILED).forEach {
            assertFalse(recordingActionMatches(row, row.copy(status = it), setOf(RecordingStatus.RECORDING)))
        }
    }
    @Test fun finalizedDestinationComesFromCurrentRow() {
        assertTrue(recordingActionMatches(row.copy(filePath = "old.ts"), row.copy(filePath = "new.mp4")))
    }
}

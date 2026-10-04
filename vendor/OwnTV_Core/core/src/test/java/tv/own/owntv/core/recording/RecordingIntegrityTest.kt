package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus

class RecordingIntegrityTest {
    private fun row() = RecordingEntity(id = 1, profileId = 1, sourceId = 1, channelId = 1,
        channelName = "", streamUrl = "", title = "", programmeStartMs = 0, programmeStopMs = 100,
        startMs = 0, stopMs = 100, bytes = 100, filePath = "/capture.ts", status = RecordingStatus.PARTIAL)

    @Test fun finalizationFailureNeverOffersAnUnfinishedFile() {
        assertFalse(RecordingIntegrity.canPlay(row().copy(finalizationFailure = RecordingFailure.NO_SPACE)))
        assertFalse(RecordingIntegrity.canPlay(row().copy(finalizationFailure = RecordingFailure.UNSUPPORTED_FORMAT)))
        assertTrue(RecordingIntegrity.canPlay(row().copy(captureFailure = RecordingFailure.NETWORK, finalizationFailure = RecordingFailure.NONE)))
    }

    @Test fun legacyRowsKeepTheirUnknownStageAndPlayablePartialCapture() {
        val legacy = row().copy(capturedDurationMs = 0, failure = RecordingFailure.NETWORK)
        assertNull(legacy.captureFailure)
        assertNull(legacy.finalizationFailure)
        assertTrue(RecordingIntegrity.canPlay(legacy))
    }

    @Test fun activeMissingAndEmptyFilesAreNotPlayable() {
        assertFalse(RecordingIntegrity.canPlay(row().copy(status = RecordingStatus.RECORDING)))
        assertFalse(RecordingIntegrity.canPlay(row().copy(filePath = null)))
        assertFalse(RecordingIntegrity.canPlay(row().copy(bytes = 0)))
    }

    @Test fun latestRecoveryDoesNotChangeOrConfuseItsOriginal() {
        val original = row()
        val old = original.copy(id = 2, recoveryOfId = 1, recoveryAttempt = 1)
        val newest = original.copy(id = 3, recoveryOfId = 1, recoveryAttempt = 2, status = RecordingStatus.RECORDING)
        val unrelated = original.copy(id = 4, recoveryOfId = 20, recoveryAttempt = 3)
        val result = RecordingIntegrity.latestRecoveries(listOf(original, old, unrelated, newest))
        assertEquals(newest, result[1L])
        assertEquals(unrelated, result[20L])
        assertEquals(RecordingStatus.PARTIAL, original.status)
        assertEquals("/capture.ts", original.filePath)
    }
}

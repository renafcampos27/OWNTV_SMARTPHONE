package tv.own.owntv.core.recording

import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

/** Pure timer adjustment; persistence and alarm replacement happen in RecordingManager. */
object RecordingReconciliation {
    fun canAdjust(recording: RecordingEntity, nowMs: Long): Boolean =
        recording.status == RecordingStatus.SCHEDULED && recording.recoveryAttempt == 0 &&
            recording.bytes == 0L && recording.startedAt == null && recording.startMs > nowMs &&
            recording.programmeStopMs > recording.programmeStartMs &&
            // An archive/recovery fetch has a playback window different from its original airing.
            recording.startMs <= recording.programmeStartMs && recording.stopMs >= recording.programmeStopMs

    fun move(recording: RecordingEntity, programme: EpgProgrammeEntity, nowMs: Long): RecordingEntity? {
        if (!canAdjust(recording, nowMs) || programme.stopMs <= nowMs || programme.stopMs <= programme.startMs) return null
        if (recording.programmeStartMs == programme.startMs && recording.programmeStopMs == programme.stopMs) return null
        val preRollMs = recording.programmeStartMs - recording.startMs
        val postRollMs = recording.stopMs - recording.programmeStopMs
        return recording.copy(
            epgChannelId = programme.epgChannelId,
            programmeStartMs = programme.startMs,
            programmeStopMs = programme.stopMs,
            startMs = programme.startMs - preRollMs,
            stopMs = programme.stopMs + postRollMs,
            updatedAt = nowMs,
        )
    }
}

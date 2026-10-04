package tv.own.owntv.core.recording

import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingStatus

/** Recovery is deliberate, and creates another file rather than replacing captured material. */
object RecordingRecoveryPolicy {
    enum class Mode { NONE, LIVE, ARCHIVE }

    fun mode(row: RecordingEntity, nowMs: Long, hasArchive: Boolean, archiveDays: Int): Mode {
        if (row.status !in setOf(RecordingStatus.PARTIAL, RecordingStatus.FAILED, RecordingStatus.MISSED)) return Mode.NONE
        if (row.programmeStopMs <= row.programmeStartMs) return Mode.NONE
        if (nowMs < row.programmeStopMs) return Mode.LIVE
        return if (hasArchive && RecordingSchedule.isWithinArchive(row.programmeStartMs, archiveDays, nowMs))
            Mode.ARCHIVE else Mode.NONE
    }
}

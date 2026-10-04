package tv.own.owntv.core.recording

import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus

/** Duration is committed media, not elapsed capture time; it does not prove programme completeness. */
object RecordingIntegrity {
    fun canPlay(row: RecordingEntity): Boolean =
        (row.status == RecordingStatus.COMPLETED || row.status == RecordingStatus.PARTIAL) &&
            !row.archivePaused && !row.filePath.isNullOrBlank() && row.bytes > 0 && !finalizationFailed(row)

    fun finalizationFailed(row: RecordingEntity): Boolean =
        row.finalizationFailure != null && row.finalizationFailure != RecordingFailure.NONE

    fun latestRecoveries(rows: List<RecordingEntity>): Map<Long, RecordingEntity> =
        rows.filter { it.recoveryOfId != null }.groupBy { it.recoveryOfId!! }
            .mapValues { (_, copies) -> copies.maxWith(compareBy<RecordingEntity> { it.recoveryAttempt }.thenBy { it.createdAt }.thenBy { it.id }) }
}

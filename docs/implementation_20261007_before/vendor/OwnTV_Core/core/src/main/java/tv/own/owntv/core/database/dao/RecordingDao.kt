package tv.own.owntv.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.database.entity.RecordingRuleEntity
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus

/**
 * Live recordings and the standing rules that create them, per profile.
 *
 * Nothing here takes part in local sync or backup (D6) — see [RecordingEntity]'s KDoc for why.
 */
@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE filePath IS NOT NULL")
    suspend fun storageRows(): List<RecordingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recording: RecordingEntity): Long

    @Update
    suspend fun update(recording: RecordingEntity)

    @Delete
    suspend fun delete(recording: RecordingEntity)

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getById(id: Long): RecordingEntity?

    @Query("UPDATE recordings SET archivePaused = :paused, updatedAt = :timestamp WHERE id = :id")
    suspend fun setArchivePaused(id: Long, paused: Boolean, timestamp: Long)

    /** Newest first — the Recordings screen groups these itself rather than asking four times. */
    @Query("SELECT * FROM recordings WHERE profileId = :profileId ORDER BY startMs DESC")
    fun observeForProfile(profileId: Long): Flow<List<RecordingEntity>>

    /** What the pill and the REC indicator watch (D13). */
    @Query("SELECT * FROM recordings WHERE status = 'RECORDING' ORDER BY startMs ASC")
    fun observeRunning(): Flow<List<RecordingEntity>>

    /**
     * The same rows, read once.
     *
     * After a crash or a battery death this is a **lie** — nothing is being written, the process that
     * was doing it is gone — and it is how [tv.own.owntv.core.recording.RecordingEngine] finds a DASH
     * recording whose two halves were captured but never put together.
     */
    @Query("SELECT * FROM recordings WHERE status = 'RECORDING' ORDER BY startMs ASC")
    suspend fun running(): List<RecordingEntity>

    /**
     * Everything still to come, soonest first — what the scheduler re-arms from, including after a
     * reboot. Deliberately across every profile: an alarm is a device-wide thing, and a recording
     * scheduled by one profile still has to fire when another is signed in.
     */
    @Query("SELECT * FROM recordings WHERE status = 'SCHEDULED' ORDER BY startMs ASC")
    suspend fun scheduled(): List<RecordingEntity>

    /** Rows whose window has begun and not yet ended — what the recorder picks up when it starts. */
    @Query(
        "SELECT * FROM recordings WHERE status IN ('SCHEDULED', 'RECORDING') " +
            "AND startMs <= :now AND stopMs > :now ORDER BY startMs ASC",
    )
    suspend fun dueAt(now: Long): List<RecordingEntity>

    /**
     * How many of one playlist's connections recordings are currently holding — the left-hand side
     * of the D10 budget check.
     */
    @Query("SELECT COUNT(*) FROM recordings WHERE sourceId = :sourceId AND status = 'RECORDING'")
    suspend fun runningCountForSource(sourceId: Long): Int

    /**
     * Anything already claiming this playlist over the same window, for the clash warning shown at
     * scheduling time (D10). Overlap is the usual half-open test: it starts before we end, and ends
     * after we start. [excludeId] keeps a row from clashing with itself when it is being edited.
     */
    @Query(
        "SELECT * FROM recordings WHERE sourceId = :sourceId AND id != :excludeId " +
            "AND status IN ('SCHEDULED', 'RECORDING') " +
            "AND startMs < :stopMs AND stopMs > :startMs ORDER BY startMs ASC",
    )
    suspend fun overlapping(sourceId: Long, startMs: Long, stopMs: Long, excludeId: Long = 0): List<RecordingEntity>

    /** The row for one programme, so pressing Record twice finds the first one instead of adding another. */
    @Query(
        "SELECT * FROM recordings WHERE profileId = :profileId AND channelId = :channelId " +
            "AND programmeStartMs = :programmeStartMs AND recoveryAttempt = 0",
    )
    suspend fun forProgramme(profileId: Long, channelId: Long, programmeStartMs: Long): RecordingEntity?

    /** Insert a separate archive recovery without REPLACE ever removing the captured original. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecovery(recording: RecordingEntity): Long

    /** Repeated recovery requests reuse the active attempt rather than creating another file. */
    @Query("SELECT * FROM recordings WHERE recoveryOfId = :originId " +
        "AND status IN ('SCHEDULED', 'RECORDING') ORDER BY recoveryAttempt DESC LIMIT 1")
    suspend fun activeRecoveryFor(originId: Long): RecordingEntity?

    @Query("SELECT COALESCE(MAX(recoveryAttempt), 0) + 1 FROM recordings " +
        "WHERE profileId = :profileId AND channelId = :channelId AND programmeStartMs = :programmeStartMs")
    suspend fun nextRecoveryAttempt(profileId: Long, channelId: Long, programmeStartMs: Long): Int

    @Query(
        "UPDATE recordings SET status = :status, failure = :failure, bytes = :bytes, " +
            "filePath = :filePath, startedAt = :startedAt, endedAt = :endedAt, updatedAt = :timestamp WHERE id = :id",
    )
    suspend fun updateProgress(
        id: Long,
        status: RecordingStatus,
        failure: RecordingFailure,
        bytes: Long,
        filePath: String?,
        startedAt: Long?,
        endedAt: Long?,
        timestamp: Long,
    )

    /** Persist committed media progress without replacing the row or changing its lifecycle status. */
    @Query(
        "UPDATE recordings SET capturedDurationMs = :capturedDurationMs, missingDurationMs = :missingDurationMs, " +
            "gapCount = :gapCount, hlsCheckpoint = :hlsCheckpoint, updatedAt = :timestamp WHERE id = :id",
    )
    suspend fun updateCapture(id: Long, capturedDurationMs: Long, missingDurationMs: Long, gapCount: Int,
        hlsCheckpoint: String?, timestamp: Long)

    /** Capture and promotion outcomes survive a restart independently of the aggregate failure. */
    @Query("UPDATE recordings SET captureFailure = :captureFailure, finalizationFailure = :finalizationFailure, updatedAt = :timestamp WHERE id = :id")
    suspend fun updateFailures(id: Long, captureFailure: RecordingFailure?, finalizationFailure: RecordingFailure?, timestamp: Long)

    // --- Series rules (A11) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRule(rule: RecordingRuleEntity): Long

    @Delete
    suspend fun deleteRule(rule: RecordingRuleEntity)

    @Query("SELECT * FROM recording_rules WHERE profileId = :profileId ORDER BY title ASC")
    fun observeRules(profileId: Long): Flow<List<RecordingRuleEntity>>

    /** Every enabled rule, across profiles — what the guide refresh walks. */
    @Query("SELECT * FROM recording_rules WHERE enabled = 1")
    suspend fun enabledRules(): List<RecordingRuleEntity>

    @Query("SELECT * FROM recording_rules WHERE profileId = :profileId AND channelId = :channelId AND titleKey = :titleKey")
    suspend fun findRule(profileId: Long, channelId: Long, titleKey: String): RecordingRuleEntity?
}

package tv.own.owntv.core.recording

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import tv.own.owntv.core.database.dao.RecordingDao
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus

/**
 * The clock behind recording: one alarm per scheduled recording, re-armed after a reboot.
 *
 * **`AlarmManager`, not WorkManager.** WorkManager is deliberately inexact and will not start the
 * nine o'clock news at nine o'clock; it batches work to save battery, which is the right behaviour
 * for everything else this app does in the background and the wrong behaviour for exactly one
 * feature.
 *
 * **`SCHEDULE_EXACT_ALARM`, never `USE_EXACT_ALARM`.** The second is granted automatically and cannot
 * be revoked, and is restricted by Google Play policy to apps whose core function *is* precise
 * timing — an alarm clock or a calendar. A TV player is neither, so declaring it would be a policy
 * problem at review time rather than a shortcut. The first is user-revocable, which is why
 * [canBeExact] is consulted on every arm rather than once at startup, and why there is a fallback:
 * an inexact alarm plus a bigger head start, so a late wake-up still catches the opening titles.
 *
 * **One alarm per recording, keyed by row id.** A single alarm for "the next one" would be simpler
 * and would lose every later recording the moment one re-arm went astray.
 */
class RecordingScheduler(
    private val context: Context,
    private val recordingDao: RecordingDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private val _unarmedRecordingIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Scheduling failures remain observable until a successful rearm or cancellation. */
    val unarmedRecordingIds: StateFlow<Set<Long>> = _unarmedRecordingIds.asStateFlow()

    /**
     * Whether this device will honour an exact alarm right now. Re-read each time: the user can
     * revoke the permission from Settings at any moment, and the system can too.
     */
    fun canBeExact(): Boolean = when {
        alarms == null -> false
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> alarms.canScheduleExactAlarms()
        else -> true
    }

    /**
     * Re-arm everything still to come and close out everything that was missed.
     *
     * Called after a boot, after the app is replaced, when the exact-alarm permission changes, and
     * whenever a recording is scheduled or cancelled. It is idempotent by construction: arming an
     * alarm with the same request code replaces the previous one rather than adding to it.
     */
    suspend fun rearmAll() {
        val now = clock()
        recordingDao.scheduled().forEach { recording ->
            if (RecordingSchedule.hasBeenMissed(recording, now)) {
                if (recording.archivePaused) {
                    recordingDao.update(recording.copy(status = RecordingStatus.PARTIAL, endedAt = now, updatedAt = now))
                    cancel(recording.id)
                    return@forEach
                }
                // Nothing to retry — the programme has been and gone. Say so instead of leaving a row
                // that claims for ever that it is about to start.
                recordingDao.updateProgress(
                    id = recording.id,
                    status = RecordingStatus.MISSED,
                    failure = RecordingFailure.UNKNOWN,
                    bytes = 0,
                    filePath = null,
                    startedAt = null,
                    endedAt = now,
                    timestamp = now,
                )
                cancel(recording.id)
            } else {
                RecordingSchedule.wakeAtFor(recording, now)?.let { arm(recording.id, it) }
            }
        }
    }

    /** Wake the recorder at [atMs] for the recording with [id]. */
    fun arm(id: Long, atMs: Long): Boolean {
        val manager = alarms
        val intent = pendingIntent(id)
        if (manager == null || intent == null) {
            _unarmedRecordingIds.update { it + id }
            return false
        }
        // setExactAndAllowWhileIdle is the only variant that fires through Doze, which is precisely
        // the state a television or a phone is in at nine in the evening with the screen off.
        val result = RecordingAlarmPolicy.arm(
            canBeExact = ::canBeExact,
            exact = { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent) },
            inexact = { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, intent) },
        ).onFailure {
            // Both attempts failed, or a non-permission platform failure occurred. Keep it observable.
            android.util.Log.w(TAG, "could not arm recording alarm id=$id: ${it.message}")
        }
        _unarmedRecordingIds.update { if (result.isSuccess) it - id else it + id }
        return result.isSuccess
    }

    fun cancel(id: Long) {
        _unarmedRecordingIds.update { it - id }
        val manager = alarms ?: return
        pendingIntent(id, mutable = false)?.let { manager.cancel(it) }
    }

    private fun pendingIntent(id: Long, mutable: Boolean = false): PendingIntent? {
        val intent = Intent(context, RecordingAlarmReceiver::class.java).apply {
            action = RecordingAlarmReceiver.ACTION_RECORDING_DUE
            // In the data, not an extra: PendingIntent equality ignores extras, so two recordings
            // would otherwise collapse into one alarm.
            data = android.net.Uri.parse("owntv://recording/$id")
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
        return runCatching {
            PendingIntent.getBroadcast(context, id.toInt(), intent, flags)
        }.getOrNull()
    }

    private companion object {
        const val TAG = "RecordingScheduler"
    }
}

package tv.own.owntv.core.recording

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import tv.own.owntv.core.i18n.LocaleStore

/**
 * Runs whatever is due to record, in a **`mediaPlayback`** foreground service so it keeps going once
 * the user leaves OwnTV — and so it is not subject to the six-hours-a-day cap Android 15 puts on
 * `dataSync`, which a DVR would hit (§1.4). See [RecordingNotifications].
 *
 * One of these at a time. Active drains accept kicks; a closing drain admits one durable successor.
 * It lives until nothing is recording and nothing is due;
 * the scheduler wakes it again at the next start time.
 *
 * **Network constraint is `CONNECTED`, never `UNMETERED`.** Downloads may wait for Wi-Fi because a
 * film is still there tomorrow; a live programme is not, so a recording runs on whatever connection
 * exists. There is deliberately no "record over Wi-Fi only" here.
 */
class RecordingWorker(
    appContext: Context,
    params: WorkerParameters,
    private val engine: RecordingEngine,
    private val localeStore: LocaleStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val gate = tv.own.owntv.core.sync.work.DurableDrainWakeup.gate(WORK_NAME)
        val owner = id.toString()
        var revision = gate.begin(owner)
        try {
            setForeground(RecordingNotifications.foregroundInfo(applicationContext, null, localeStore))
            var lastTick = 0L
            do {
                engine.drainQueue { progress ->
                    val now = System.currentTimeMillis()
                    if (now - lastTick > NOTIFICATION_INTERVAL_MS) {
                        lastTick = now
                        runCatching {
                            setForegroundAsync(
                                RecordingNotifications.foregroundInfo(applicationContext, progress, localeStore),
                            )
                        }
                    }
                }
                if (gate.close(owner, revision)) break
                revision = gate.currentRevision()
            } while (!isStopped)
            return Result.success()
        } finally { gate.failed(owner) }
    }

    companion object {
        const val WORK_NAME = "owntv-recordings"
        private const val NOTIFICATION_INTERVAL_MS = 5_000L

        /**
         * Running writers are preserved. A kick in the final completion window persists one successor.
         */
        fun kick(context: Context) {
            val request = OneTimeWorkRequestBuilder<RecordingWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .addTag(WORK_NAME)
                .build()
            tv.own.owntv.core.sync.work.DurableDrainWakeup.kick(context, WORK_NAME, request)
        }
    }
}

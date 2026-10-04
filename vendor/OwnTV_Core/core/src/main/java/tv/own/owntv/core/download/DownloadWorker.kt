package tv.own.owntv.core.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import tv.own.owntv.core.i18n.LocaleStore

/**
 * Runs the download queue as a foreground service so transfers keep going once the user leaves
 * OwnTV — an app-scoped coroutine died with the process the moment Android reclaimed it (DL1).
 *
 * There is exactly one drain at a time, with one durable successor allowed during handoff. It lives until the queue is
 * empty, which is what preserves the one-at-a-time semantics downloads always had.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val engine: DownloadEngine,
    private val localeStore: LocaleStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val gate = tv.own.owntv.core.sync.work.DurableDrainWakeup.gate(WORK_NAME)
        val owner = id.toString()
        var revision = gate.begin(owner)
        try {
            setForeground(DownloadNotifications.foregroundInfo(applicationContext, null, localeStore))
            var lastTick = 0L
            do {
                engine.drainQueue { progress ->
                    // The transfer reports twice a second; the notification does not need that.
                    val now = System.currentTimeMillis()
                    if (now - lastTick > NOTIFICATION_INTERVAL_MS) {
                        lastTick = now
                        runCatching {
                            setForegroundAsync(DownloadNotifications.foregroundInfo(applicationContext, progress, localeStore))
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
        const val WORK_NAME = "owntv-downloads"
        private const val NOTIFICATION_INTERVAL_MS = 2_000L

        /**
         * Running writers are preserved; a closing drain admits at most one durable successor.
         *
         * [wifiOnly] is the user's "download over Wi-Fi only" choice, enforced by WorkManager itself
         * rather than by a check here: an UNMETERED constraint also *stops* a running transfer the
         * moment the phone falls off Wi-Fi, which a check at start could never do.
         */
        fun kick(context: Context, wifiOnly: Boolean = false, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED,
                        )
                        .build(),
                )
                .addTag(WORK_NAME)
                .build()
            tv.own.owntv.core.sync.work.DurableDrainWakeup.kick(context, WORK_NAME, request, replace)
        }
    }
}

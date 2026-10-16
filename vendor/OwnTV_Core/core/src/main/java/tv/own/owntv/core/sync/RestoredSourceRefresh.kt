package tv.own.owntv.core.sync

import androidx.work.ExistingWorkPolicy
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.sync.work.CatalogSyncScheduler

class RestoredSourceRefresh(
    private val sources: SourceDao,
    private val scheduler: CatalogSyncScheduler,
    private val finalizer: ImportFinalizer,
) {
    suspend fun enqueueMissingCatalogs() {
        sources.getAllOnce().forEach { source ->
            val counts = finalizer.contentCounts(source.id)
            if (counts.channels + counts.movies + counts.series == 0) scheduler.enqueueSync(source.id, reason = "restore",
                contentTypes = SyncContentTypes.enabledOf(source), baseItemCount = 0,
                policy = ExistingWorkPolicy.KEEP)
        }
    }
}

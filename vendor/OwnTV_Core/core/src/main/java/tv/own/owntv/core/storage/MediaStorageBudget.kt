package tv.own.owntv.core.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.RecordingDao
import tv.own.owntv.core.settings.SettingsRepository
import java.io.File

/** Rebuilt from all profiles' durable rows after restart; no filesystem scan on the UI thread. */
class MediaStorageBudget(private val context: Context, private val settings: SettingsRepository,
    private val recordings: RecordingDao, private val downloads: DownloadDao) {
    enum class Reason { UNAVAILABLE, QUOTA, NO_SPACE, FILE_LIMIT }
    class Refused(val reason: Reason) : java.io.IOException()
    data class Info(val known: Boolean, val free: Long = 0, val total: Long = 0, val reserve: Long = 0,
        val quota: Long = 0, val used: Long = 0, val removable: Boolean = false)
    private val ledgers = mutableMapOf<String, StorageQuotaLedger>()
    private val mutex = Mutex()
    private var refreshedAt = 0L
    private val physical = mutableMapOf<String, PhysicalStorageLedger>()

    private suspend fun quota(volume: StorageVolumeInfo) = (if (volume.removable) settings.externalMediaQuotaGiB.first()
        else settings.internalMediaQuotaGiB.first()) * StorageQuotaPolicy.GIB
    private suspend fun refresh(force: Boolean = false) {
        val now = System.nanoTime()
        if (!force && refreshedAt != 0L && now - refreshedAt < 5_000_000_000L) return
        refreshedAt = now
        data class Entry(val path: String, val captured: Long, val recordingId: Long? = null, val finalized: Boolean = false)
        val rows = recordings.storageRows().mapNotNull { row -> row.filePath?.let { Entry(it, row.bytes, row.id, tv.own.owntv.core.recording.RecordingIntegrity.canPlay(row)) } } +
            downloads.storageRows().mapNotNull { row -> row.filePath?.let { Entry(it, row.downloadedBytes) } }
        val groups = mutableMapOf<String, MutableMap<String, Long>>()
        val volumes = mutableMapOf<String, StorageVolumeInfo?>()
        rows.forEach { row ->
            val cacheKey = if (MediaTarget.isDocument(row.path)) runCatching {
                val uri = android.net.Uri.parse(row.path)
                uri.authority + ":" + DocumentVolumes.volumeIdOf(android.provider.DocumentsContract.getDocumentId(uri))
            }.getOrDefault(row.path) else File(row.path).parent.orEmpty()
            if (!volumes.containsKey(cacheKey)) volumes[cacheKey] = StorageVolumeInfo.resolve(context, row.path)
            val volume = volumes[cacheKey] ?: return@forEach
            val target = MediaTarget.of(context, row.path) ?: return@forEach
            val actual = runCatching { if (tv.own.owntv.core.recording.RecordingParts.isIndex(target) && target.exists()) row.captured + target.length() else target.length() }.getOrDefault(0)
            val staged = row.recordingId?.let { id -> runCatching {
                // Durable progress avoids scanning thousands of HLS segments every refresh.
                val hls = if (spool(tv.own.owntv.core.recording.RecordingParts.spoolTarget(target), ".owntv-hls-$id").isDirectory) row.captured else 0L
                val parent = spool(target, ".owntv-dash").parentFile
                val dash = (0..1).sumOf { index -> File(parent, "owntv-dash-$id-$index.part").length() }
                maxOf(hls, dash)
            }.getOrDefault(0) } ?: 0
            // Missing manually removed files no longer consume quota. Live leases retain their reservation.
            val parts = row.recordingId?.let { id -> runCatching {
                tv.own.owntv.core.recording.RecordingParts.stagedBytes(context, target, id)
            }.getOrDefault(0) } ?: 0
            val size = (if (row.finalized) actual + staged else maxOf(actual, staged)) + parts
            val group = groups.getOrPut(volume.key) { mutableMapOf() }
            group[row.path] = maxOf(group[row.path] ?: 0, size)
        }
        (ledgers.keys + groups.keys).toSet().forEach { key ->
            ledgers.getOrPut(key) { StorageQuotaLedger() }.reconcile(groups[key].orEmpty())
        }
    }
    suspend fun info(root: String): Info = withContext(Dispatchers.IO) { mutex.withLock {
        val volume = StorageVolumeInfo.resolve(context, root) ?: return@withLock Info(false)
        refresh()
        Info(true, volume.free, volume.total, StorageQuotaPolicy.reserve(volume.total, volume.removable),
            quota(volume), ledgers.getOrPut(volume.key) { StorageQuotaLedger() }.total(), volume.removable)
    } }
    suspend fun acquire(target: MediaTarget): Lease = withContext(Dispatchers.IO) { mutex.withLock {
        val volume = StorageVolumeInfo.resolve(context, target.stored) ?: throw Refused(Reason.UNAVAILABLE)
        if (!target.ensureWritable()) throw Refused(Reason.UNAVAILABLE)
        if (volume.free <= StorageQuotaPolicy.reserve(volume.total, volume.removable)) throw Refused(Reason.NO_SPACE)
        refresh(force = true)
        val ledger = ledgers.getOrPut(volume.key) { StorageQuotaLedger() }
        try { ledger.acquire(target.stored) } catch (_: IllegalStateException) { throw Refused(Reason.UNAVAILABLE) }
        Lease(target, volume.key, volume.removable, ledger, volume.maxFileBytes,
            physical.getOrPut(volume.key) { PhysicalStorageLedger() })
    } }
    inner class Lease internal constructor(private val target: MediaTarget, private val volumeKey: String,
        private val removable: Boolean, private val ledger: StorageQuotaLedger, private val maxFileBytes: Long,
        private val physicalLedger: PhysicalStorageLedger) {
        private var committed = target.length()
        private var closed = false
        private var checkedAt = 0L
        private var snapshot: StorageVolumeInfo? = null
        private var quotaBytes = 0L

        val requiresParts: Boolean get() = maxFileBytes != Long.MAX_VALUE

        /** Quota admission is atomic across writers; mount/permissions are rechecked every 500 ms. */
        suspend fun reserve(desired: Long, scratchBytes: Long = 0, perFileBytes: Long = desired) = mutex.withLock {
            check(!closed)
            require(desired >= 0 && scratchBytes >= 0 && perFileBytes >= 0)
            val now = System.nanoTime()
            if (snapshot == null || now - checkedAt >= 500_000_000L) {
                val fresh = StorageVolumeInfo.resolve(context, target.stored) ?: throw Refused(Reason.UNAVAILABLE)
                if (fresh.key != volumeKey) throw Refused(Reason.UNAVAILABLE)
                snapshot = fresh
                checkedAt = now
                quotaBytes = quota(fresh)
            }
            val volume = requireNotNull(snapshot)
            if (physicalLedger.needsObservation(now)) {
                // All writers use this same snapshot/commit ledger. Refresh the actual volume,
                // never a different lease's stale snapshot.
                val ticket = physicalLedger.observationTicket()
                val fresh = StorageVolumeInfo.resolve(context, target.stored) ?: throw Refused(Reason.UNAVAILABLE)
                if (fresh.key != volumeKey) throw Refused(Reason.UNAVAILABLE)
                physicalLedger.observe(fresh.free, now, ticket)
            }
            if (perFileBytes > volume.maxFileBytes - minOf(128L * 1024 * 1024, volume.maxFileBytes / 100)) throw Refused(Reason.FILE_LIMIT)
            val increase = (desired - committed).coerceAtLeast(0)
            if (!physicalLedger.reserve(target.stored, increase, scratchBytes,
                    StorageQuotaPolicy.reserve(volume.total, removable))) throw Refused(Reason.NO_SPACE)
            if (!ledger.grow(target.stored, desired, quotaBytes)) {
                // Keep the conservative physical reservation until the refused writer closes;
                // older scratch files may still exist while cancellation/finalization unwinds.
                throw Refused(Reason.QUOTA)
            }
        }
        fun commit(bytes: Long) {
            val actual = bytes.coerceAtLeast(0)
            physicalLedger.commit(target.stored, (actual - committed).coerceAtLeast(0))
            committed = actual
            ledger.grow(target.stored, actual, Long.MAX_VALUE)
        }
        suspend fun close(actual: Long = committed) = mutex.withLock {
            if (!closed) {
                closed = true
                physicalLedger.commit(target.stored, (actual - committed).coerceAtLeast(0))
                physicalLedger.release(target.stored)
                ledger.release(target.stored, actual)
            }
        }
    }
    fun destinationAvailable(target: MediaTarget): Boolean =
        StorageVolumeInfo.resolve(context, target.stored) != null && target.ensureWritable()

    fun reserveFloor(target: MediaTarget): Long {
        val volume = StorageVolumeInfo.resolve(context, target.stored) ?: throw Refused(Reason.UNAVAILABLE)
        return StorageQuotaPolicy.reserve(volume.total, volume.removable)
    }
    /** Android file-write failures are terminal; they must not become network reconnect loops. */
    fun classifyWriteFailure(error: Exception, target: MediaTarget): Refused? {
        var cause: Throwable? = error
        repeat(8) {
            val current = cause ?: return@repeat
            if (current is android.system.ErrnoException) {
                when (current.errno) {
                    android.system.OsConstants.EFBIG -> return Refused(Reason.FILE_LIMIT)
                    android.system.OsConstants.ENOSPC -> return Refused(Reason.NO_SPACE)
                    android.system.OsConstants.ENODEV, android.system.OsConstants.EACCES -> return Refused(Reason.UNAVAILABLE)
                }
            }
            cause = current.cause
        }
        return if (StorageVolumeInfo.resolve(context, target.stored) == null) Refused(Reason.UNAVAILABLE) else null
    }
    /** A document capture must stage on the destination volume, never silently on the internal one. */
    fun spool(target: MediaTarget, name: String): File {
        if (target is MediaTarget.Path) return File(target.file.parentFile, name)
        val volume = StorageVolumeInfo.resolve(context, target.stored) ?: throw Refused(Reason.UNAVAILABLE)
        val base = if (!volume.removable) context.filesDir else context.getExternalFilesDirs(null).filterNotNull()
            .firstOrNull { StorageVolumeInfo.resolve(context, it.absolutePath)?.key == volume.key }
            ?: throw Refused(Reason.UNAVAILABLE)
        if ((!base.exists() && !base.mkdirs()) || !base.canWrite()) throw Refused(Reason.UNAVAILABLE)
        return File(base, name)
    }
}

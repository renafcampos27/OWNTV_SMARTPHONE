package tv.own.owntv.core.recording

import android.content.Context
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.own.owntv.core.database.dao.RecordingDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.live.StreamGrant
import tv.own.owntv.core.live.StreamPurpose
import tv.own.owntv.core.live.connectionBudget
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.network.ConnectivityObserver
import tv.own.owntv.core.network.HttpClient
import tv.own.owntv.core.network.StreamHeaders
import tv.own.owntv.core.network.cancelBlockingCallWithCoroutine
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.storage.MediaStorageBudget
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.stalker.StreamUrlResolver
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * The byte pump behind live recording: [tv.own.owntv.core.download.DownloadEngine]'s shape, with the
 * four differences a live stream forces (§1.3).
 *
 * 1. **A time-based stop.** A live `.ts` URL never returns end-of-body, so the recording ends at
 *    `stopMs` and nowhere else.
 * 2. **No `Range` resume.** Resuming a live stream at a byte offset is meaningless. A dropped
 *    connection re-requests from *now* and **appends**, leaving a gap in the file. The gap is honest
 *    and unavoidable, and a `.ts` survives it.
 * 3. **Its own queue.** Recordings must not be serialised behind a 6 GB film — `DownloadEngine`
 *    drains strictly one at a time, deliberately, and a film would eat the nine o'clock news.
 * 4. **Concurrency bounded by the provider, not by us** (D10). Several recordings run at once, up to
 *    what the playlist's `maxConnections` allows, with one stream kept back so the user can still
 *    watch — unless they have said otherwise.
 *
 * [RecordingDao] is the single source of truth, exactly as the download queue is: the engine holds no
 * queue of its own, it drains whatever rows are due.
 */
class RecordingEngine(
    /** Only to resolve a stored `filePath` into something writable — a document needs a resolver. */
    private val context: Context,
    private val recordingDao: RecordingDao,
    /** Only to read the channel's declared DRM — see the refusal at the top of [attemptRecord]. */
    private val channelDao: tv.own.owntv.core.database.dao.ChannelDao,
    private val client: OkHttpClient,
    private val sourceDao: SourceDao,
    private val streamUrlResolver: StreamUrlResolver,
    private val streams: OpenStreamRegistry,
    private val settings: SettingsRepository,
    private val connectivity: ConnectivityObserver,
    private val activityTracker: RecordingActivityTracker,
    private val storageBudget: MediaStorageBudget,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Recordings currently running, so one can be stopped precisely without touching the others. */
    private val active = ConcurrentHashMap<Long, Job>()
    private val admissionLock = Any()
    private val archiveSources = ConcurrentHashMap.newKeySet<Long>()

    /** Ids the user has just stopped or deleted; the drain loop steps over them. */
    private val suppressed = Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    /**
     * The DASH state of each running recording, kept **across reconnects**.
     *
     * A DASH recording fixes on its Representations at the start and must keep them: a second attempt
     * that re-chose would append a different quality to the same file, which is exactly the
     * mid-stream codec change the mux cannot absorb. Removed, and its temp files dealt with, by
     * [finishDashSession] when the recording ends however it ends.
     */
    private val dashSessions = ConcurrentHashMap<Long, DashSession>()
    private val playbackOrphansReconciled = java.util.concurrent.atomic.AtomicBoolean(false)
    private val partReaders = ConcurrentHashMap<Long, java.util.concurrent.atomic.AtomicInteger>()
    private val playbackStores = ConcurrentHashMap<Long, RecordingPlaybackStore>()
    private val hlsSessions = ConcurrentHashMap<Long, HlsCaptureSession>()
    private val pauseRequested = ConcurrentHashMap.newKeySet<Long>()
    private val resumePending = ConcurrentHashMap.newKeySet<Long>()
    private val storageLeases = ConcurrentHashMap<Long, MediaStorageBudget.Lease>()

    @Volatile
    private var queueDirty = false

    fun markQueued() {
        queueDirty = true
    }

    /** True while anything is being written — what the worker checks before it lets go. */
    val isRecording: Boolean get() = active.isNotEmpty()

    /**
     * Start everything that is due, keep it running, and return when nothing is recording and
     * nothing is due.
     *
     * Unlike the download drain this does **not** run one at a time: each due row gets its own child
     * job, and the loop then polls so a recording that becomes due while another is still running is
     * picked up without waiting for it.
     */
    suspend fun drainQueue(onProgress: (RecordingProgress) -> Unit) = coroutineScope {
        val report: (RecordingProgress) -> Unit = { activityTracker.progress(it); onProgress(it) }
        // Before anything starts, so it can never race a live recording for the same temp files.
        cleanupFinalizedPlaybackSpools()
        recoverInterruptedHlsRecordings()
        recoverInterruptedDashRecordings()
        finishExpiredOrphans()
        while (currentCoroutineContext().isActive) {
            queueDirty = false
            val now = clock()
            val reserve = settings.recordingReserveConnection()
            // Checked once per pass, not per row: it is one system call and every row this pass gets
            // the same answer.
            val meteredRefused = connectivity.isMeteredNow() && !settings.recordingOverMobileData()
            for (row in recordingDao.dueAt(now)) {
                if (active.containsKey(row.id) || row.id in suppressed) continue
                // A blank stream URL means "somebody else is writing this file": a
                // "record what I'm watching" row, whose bytes come from the player's already-open
                // stream (D3, mode b). Touching it would open a second connection, which is the one
                // thing that mode exists to avoid.
                if (row.streamUrl.isBlank()) continue
                // Said as a MISSED row with a reason rather than by deferring the work. A download
                // waits for Wi-Fi because the film is there tomorrow; a live programme is not.
                if (meteredRefused) {
                    markMissed(row, RecordingFailure.METERED_CONNECTION)
                    continue
                }
                when (val grant = grantFor(row, reserve)) {
                    is StreamGrant.Refused -> markMissed(row, RecordingRules.missedBecause(grant.reason))
                    StreamGrant.Allowed -> start(row, report)
                }
            }
            // A recording whose window has closed while its read was blocked: the pump checks the
            // clock itself, but a socket that never delivers another byte would keep the job alive
            // past its stop time. This is the backstop, and it is why the stop is reliable.
            active.forEach { (id, job) ->
                val row = recordingDao.getById(id)
                if (row == null || RecordingRules.isOverrunning(clock(), row.stopMs)) job.cancel()
            }
            if (active.isEmpty()) {
                if (queueDirty) continue else return@coroutineScope
            }
            delay(POLL_MS)
        }
    }

    private fun kotlinx.coroutines.CoroutineScope.start(row: RecordingEntity, report: (RecordingProgress) -> Unit) {
        // LAZY so the job is in the map before it can finish and try to remove itself.
        val job = launch(start = CoroutineStart.LAZY) {
            val archive = RecordingSchedule.isCatchUp(row)
            if (archive && !archiveSources.add(row.sourceId)) {
                markMissed(row, RecordingFailure.CLASH)
                activityTracker.finished(row.id)
                return@launch
            }
            try {
            val source = sourceDao.getById(row.sourceId)
            val claim = source?.let { streams.tryClaim(it, StreamPurpose.RECORDING, settings.recordingReserveConnection()) }
            if (claim == null) {
                markMissed(row, if (source == null) RecordingFailure.CHANNEL_GONE else RecordingFailure.NO_CONNECTION)
                activityTracker.finished(row.id)
                return@launch
            }
            try {
                runRecording(row.id, report)
            } finally {
                streams.release(claim)
                activityTracker.finished(row.id)
            }
            } finally { if (archive) archiveSources.remove(row.sourceId) }
        }
        synchronized(admissionLock) {
            if (row.id in suppressed || active.containsKey(row.id)) { job.cancel(); return }
            active[row.id] = job
            job.invokeOnCompletion { active.remove(row.id, job) }
            job.start()
        }
    }

    /** May this recording have one of the playlist's connections right now? (D10/D11.) */
    private suspend fun grantFor(row: RecordingEntity, reserveOneForWatching: Boolean): StreamGrant =
        connectionBudget(
            source = sourceDao.getById(row.sourceId),
            open = streams.openOn(row.sourceId),
            purpose = StreamPurpose.RECORDING,
            reserveOneForWatching = reserveOneForWatching,
        )

    /**
     * Stop the recording of [id] and wait for it to let go of the file, keeping the drain loop off it
     * until [release] is called — the same handshake pause/delete use for a download.
     */
    suspend fun stop(id: Long) {
        val job = synchronized(admissionLock) { suppressed += id; active[id] }
        job?.cancelAndJoin()
    }

    /** Admit pause before cancellation; joining also waits for HTTP bodies and stream claims to close. */
    suspend fun pauseArchive(row: RecordingEntity): Boolean {
        val job = synchronized(admissionLock) {
            if (!ArchiveResumePolicy.canPause(row) || hlsSessions[row.id] == null) return false
            val running = active[row.id] ?: return false
            suppressed += row.id
            pauseRequested += row.id
            running
        }
        withContext(NonCancellable) {
            try { recordingDao.setArchivePaused(row.id, true, clock()) }
            catch (error: Exception) { pauseRequested.remove(row.id); throw error }
            job.cancelAndJoin()
        }
        return recordingDao.getById(row.id)?.archivePaused == true
    }

    /** Reserve a scheduled row only if no writer has been admitted yet. */
    fun suppressIfIdle(id: Long): Boolean = synchronized(admissionLock) {
        if (active.containsKey(id)) false else { suppressed += id; true }
    }

    fun release(id: Long) {
        suppressed -= id
    }

    internal fun hasPlaybackReaders(id: Long): Boolean = playbackStores[id]?.hasViewers() == true || (partReaders[id]?.get() ?: 0) > 0

    internal fun openParts(row: RecordingEntity): RecordingPlayback? {
        val target = MediaTarget.of(context, row.filePath) ?: return null
        if (!RecordingParts.isIndex(target)) return null
        val readers = partReaders.getOrPut(row.id) { java.util.concurrent.atomic.AtomicInteger() }
        return RecordingParts.openPlayback(context, target, readers)
    }

    internal fun openInProgress(id: Long): RecordingPlayback? = playbackStores[id]?.playback()

    internal fun retainedArchiveAvailable(row: RecordingEntity): Boolean {
        val target = MediaTarget.of(context, row.filePath) ?: return false
        return storageBudget.destinationAvailable(target) &&
            File(hlsDirectory(row.id, target), "checkpoint.json").isFile
    }

    /** Called after stop by deletion; retained payloads must not outlive a deleted recording. */
    suspend fun discardRetainedCapture(id: Long): Boolean {
        check(!active.containsKey(id)) { "Stop recording before deleting its capture" }
        if (hasPlaybackReaders(id)) return false
        val row = recordingDao.getById(id) ?: return false
        val target = MediaTarget.of(context, row.filePath) ?: return true
        if (!storageBudget.destinationAvailable(target)) return false
        val directory = runCatching { hlsDirectory(id, target) }.getOrNull() ?: return false
        return withContext(Dispatchers.IO) {
            // Derived from the row id, never from a checkpoint-supplied path.
            val files = directory.listFiles()?.filter { it.isFile }.orEmpty() + orphanedDashTemps(id, target)
            if (files.any { !it.delete() && it.exists() }) return@withContext false
            !directory.exists() || directory.delete()
        }
    }

    /**
     * One recording, from its first byte to its last.
     *
     * The attempt loop runs until the window closes rather than for a fixed number of tries: a
     * two-hour recording may legitimately reconnect a dozen times, and a channel that is briefly down
     * at 20:00 should still record the rest of the programme.
     */
    private suspend fun runRecording(id: Long, onProgress: (RecordingProgress) -> Unit) {
        val row = recordingDao.getById(id) ?: return
        val target = MediaTarget.of(context, row.filePath) ?: return
        val startedAt = row.startedAt ?: clock()
        val lease = try { storageBudget.acquire(target) }
        catch (error: MediaStorageBudget.Refused) {
            if (row.archivePaused) preservePausedArchive(row, target, null, storageFailure(error), startedAt)
            else finish(row, target.length(), storageFailure(error), startedAt)
            return
        }
        storageLeases[id] = lease
        if (row.archivePaused) resumePending += id
        try {
            recordingDao.updateProgress(
                id = id,
                status = RecordingStatus.RECORDING,
                failure = RecordingFailure.NONE,
                bytes = target.length(),
                filePath = target.stored,
                startedAt = startedAt,
                endedAt = null,
                timestamp = clock(),
            )

            recordingDao.updateFailures(id, RecordingFailure.NONE, null, clock())
            var failure = RecordingFailure.NONE
            var attempt = 0
            try {
                while (currentCoroutineContext().isActive && !RecordingRules.shouldStop(clock(), row.stopMs)) {
                    attempt++
                    val reason = try {
                        attemptRecord(row.copy(startedAt = startedAt), target, onProgress)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (error: MediaStorageBudget.Refused) {
                        storageFailure(error)
                    } catch (_: UnsupportedHls) {
                        RecordingFailure.UNSUPPORTED_FORMAT
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "recording attempt $attempt failed id=$id kind=${e.javaClass.simpleName}")
                        storageBudget.classifyWriteFailure(e, target)?.let(::storageFailure) ?: RecordingFailure.NETWORK
                    }
                    // A failed resume must preserve its checkpoint, never finalize or append blindly.
                    if (id in resumePending && reason != RecordingFailure.NONE) {
                        failure = reason
                        break
                    }
                    // Terminal reasons cannot be repaired by opening the same stream again:
                    // Quota, storage and protected-format refusals preserve bytes without reconnecting.
                    if (reason == RecordingFailure.NO_SPACE || reason == RecordingFailure.ENCRYPTED ||
                        reason == RecordingFailure.DRM_PROTECTED || reason == RecordingFailure.UNSUPPORTED_FORMAT ||
                        reason == RecordingFailure.QUOTA_REACHED || reason == RecordingFailure.STORAGE_UNAVAILABLE ||
                        reason == RecordingFailure.FILE_LIMIT
                    ) {
                        failure = reason
                        break
                    }
                    // NONE means the attempt ended of its own accord rather than by failing: the window
                    // closed, the playlist said `#EXT-X-ENDLIST`, or an archive stream served the whole
                    // programme. None of those is worth reconnecting for.
                    if (reason == RecordingFailure.NONE) {
                        // HLS continuity is measured by committed sequences; raw streams cannot prove it.
                        if (hlsSessions[id]?.incomplete == false) failure = RecordingFailure.NONE
                        break
                    }
                    failure = reason
                    if (RecordingRules.shouldStop(clock(), row.stopMs)) break
                    delay(RecordingRules.retryDelayMs(attempt))
                }
            } finally {
                // Also the path a cancellation takes — a recording stopped by hand, or by the drain
                // loop's overrun backstop, still has its bytes written down and its file kept. A DASH
                // recording's two halves are put together here, before the size is read, so the row
                // records the finished file rather than an empty one.
                withContext(NonCancellable + Dispatchers.IO) {
                    val captureFailure = failure
                    val (hls, paused) = synchronized(admissionLock) {
                        hlsSessions.remove(id) to (pauseRequested.remove(id) || id in resumePending)
                    }
                    hls?.playback?.finish()
                    if (paused) {
                        preservePausedArchive(row, target, hls, failure, startedAt)
                        return@withContext
                    }
                    recordingDao.setArchivePaused(id, false, clock())
                    val hlsResult = hls?.let {
                        try { finishHlsSession(row, target, it) }
                        catch (error: MediaStorageBudget.Refused) { MuxResult(false, null, storageFailure(error)) }
                        catch (error: Exception) { MuxResult(false, null,
                            storageBudget.classifyWriteFailure(error, target)?.let(::storageFailure) ?: RecordingFailure.UNKNOWN) }
                    }
                    if (hlsResult != null && !hlsResult.succeeded) failure = hlsResult.failure
                    val dashBytes = dashSessions[id]?.tracks?.sumOf { it.temp?.length() ?: 0L } ?: 0L
                    val dashResult = if (hlsResult == null) finishDashSession(id, target) else null
                    if (dashResult != null && !dashResult.succeeded) failure = dashResult.failure
                    val finished = hlsResult?.movedTo ?: dashResult?.movedTo ?: target
                    val retainedBytes = maxOf(hlsResult?.bytes ?: finished.length(), if (hlsResult?.succeeded == false) hls.capturedBytes + hls.initFile.length() else 0,
                        if (dashResult?.succeeded == false) dashBytes else 0)
                    val incomplete = failure != RecordingFailure.NONE || hls?.incomplete == true ||
                        (clock() < row.stopMs && !RecordingSchedule.isCatchUp(row)) ||
                        (hls != null && HlsCaptureCompleteness.missingWindow(hls.capturedDurationMs,
                            hls.entries.maxOfOrNull { it.durationMs } ?: 6_000L,
                            row.programmeStartMs, row.programmeStopMs, startedAt, RecordingSchedule.isCatchUp(row)))
                    val finalizationFailure = (hlsResult ?: dashResult)?.takeIf { !it.succeeded }?.failure ?: RecordingFailure.NONE
                    finish(row, retainedBytes, failure, startedAt, finished.stored, incomplete,
                        captureFailure = captureFailure, finalizationFailure = finalizationFailure)
                }
            }
        } finally {
            withContext(NonCancellable) {
                val latest = recordingDao.getById(id)
                val savedTarget = latest?.filePath?.let { MediaTarget.of(context, it) } ?: target
                val sourceBytes = runCatching { if (hlsDirectory(id, savedTarget).isDirectory) latest?.bytes ?: 0 else 0L }.getOrDefault(0)
                val packageBytes = runCatching { RecordingParts.stagedBytes(context, savedTarget, id) }.getOrDefault(0)
                lease.close((if (RecordingParts.isIndex(savedTarget)) (latest?.bytes ?: 0) + savedTarget.length() + sourceBytes
                    else maxOf(target.length(), latest?.bytes ?: 0)) + packageBytes)
                storageLeases.remove(id)
                resumePending.remove(id)
            }
        }
    }

    private suspend fun preservePausedArchive(row: RecordingEntity, target: MediaTarget,
        session: HlsCaptureSession?, failure: RecordingFailure, startedAt: Long) {
        session?.persist()
        if (session != null) recordingDao.updateCapture(row.id, session.capturedDurationMs, session.missingDurationMs,
            session.gapCount, session.checkpoint(), clock())
        val latest = recordingDao.getById(row.id) ?: return
        recordingDao.update(latest.copy(status = RecordingStatus.PARTIAL, archivePaused = true,
            bytes = maxOf(latest.bytes, row.bytes, session?.let { it.capturedBytes + it.initFile.length() } ?: 0),
            failure = failure, captureFailure = failure, finalizationFailure = null,
            startedAt = startedAt, endedAt = clock(), updatedAt = clock(), filePath = target.stored))
    }

    private fun storageFailure(error: MediaStorageBudget.Refused): RecordingFailure = when (error.reason) {
        MediaStorageBudget.Reason.UNAVAILABLE -> RecordingFailure.STORAGE_UNAVAILABLE
        MediaStorageBudget.Reason.QUOTA -> RecordingFailure.QUOTA_REACHED
        MediaStorageBudget.Reason.NO_SPACE -> RecordingFailure.NO_SPACE
        MediaStorageBudget.Reason.FILE_LIMIT -> RecordingFailure.FILE_LIMIT
    }

    /**
     * One connection's worth of recording. Returns [RecordingFailure.NONE] when it ended because the
     * window closed, or the reason it ended early — the caller decides whether to reconnect.
     */
    private suspend fun attemptRecord(
        row: RecordingEntity,
        target: MediaTarget,
        onProgress: (RecordingProgress) -> Unit,
    ): RecordingFailure {
        // #115 — a channel whose playlist declares a licence can never be written down: the CDM
        // decrypts only into a secure decoder for immediate display, so there is no point at which
        // these bytes exist in the clear for us to keep. Refused BEFORE the request, so it costs no
        // connection at all — unlike the HLS `#EXT-X-KEY` refusal, which can only be found by asking.
        // Read from the channel rather than the recording row so it follows a re-sync; a channel that
        // has since vanished simply has nothing to declare and falls through to the usual failure.
        if (channelDao.getById(row.channelId)?.drmConfig != null) {
            android.util.Log.w(TAG, "recording refused, DRM-protected channel id=${row.id}")
            return RecordingFailure.DRM_PROTECTED
        }
        val (url, userAgent) = resolveTarget(row)
        val headers = StreamHeaders.decode(row.httpHeaders)
        val agent = StreamHeaders.userAgentOf(headers) ?: userAgent

        val call = client.newCall(request(url, agent, headers))
        return cancelBlockingCallWithCoroutine(call::cancel) {
        call.execute().use { response ->
            if (!response.isSuccessful) {
                return@cancelBlockingCallWithCoroutine if (response.code == RecordingRules.SESSION_LIMIT_CODE) {
                    // The provider itself says the account is already streaming. The budget thought
                    // there was room — a stale or absent maxConnections — so this is the backstop,
                    // and it is a refusal rather than a fault.
                    RecordingFailure.NO_CONNECTION
                } else {
                    RecordingFailure.STREAM_UNAVAILABLE
                }
            }
            // An HLS channel is a list of segments, not a body of video. Peek at enough of it to tell
            // — the content type is unreliable and the `.m3u8` in the URL disappears behind a
            // redirect, but the first line of the body never lies.
            val peek = response.peekBody(PLAYLIST_PEEK_BYTES).string()
            val contentType = response.header("Content-Type")
            if (HlsMediaPlaylist.looksLikePlaylist(contentType, peek)) {
                // The playlist's *final* URL, so relative segment URIs resolve against wherever the
                // redirects actually landed rather than where we asked.
                val finalUrl = response.request.url.toString()
                response.close()
                return@cancelBlockingCallWithCoroutine recordHls(row, target, finalUrl, agent, headers, onProgress)
            }
            if (row.id in resumePending) throw UnsupportedHls()
            // A DASH manifest is a document too, and a few kilobytes of it. Before this existed it
            // fell through to the byte pump below, which wrote the XML into the recording, read
            // end-of-body, reported NETWORK, reconnected and appended the same XML again for the
            // whole window — a file of concatenated manifests and a reconnect storm to produce it.
            if (DashManifest.looksLikeDashManifest(contentType, peek)) {
                val finalUrl = response.request.url.toString()
                response.close()
                return@cancelBlockingCallWithCoroutine recordDash(row, target, finalUrl, agent, headers, onProgress)
            }
            return@cancelBlockingCallWithCoroutine recordRaw(row, target, response, onProgress)
        }
        }
    }

    private suspend fun recordRaw(
        row: RecordingEntity, target: MediaTarget, response: okhttp3.Response,
        onProgress: (RecordingProgress) -> Unit,
    ): RecordingFailure {
            // Always append: a reconnect continues the same file from wherever the stream is now.
            // There is no Range to resume with and nothing to rewind to.
            response.body.byteStream().use { input ->
                target.openOutput(append = true).use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    var lastTick = 0L
                    var written = target.length()
                    while (true) {
                        if (!currentCoroutineContext().isActive) return RecordingFailure.NONE
                        if (RecordingRules.shouldStop(clock(), row.stopMs)) return RecordingFailure.NONE
                        if (!RecordingRules.hasSpace(target.usableSpace())) {
                            android.util.Log.w(TAG, "recording stopped, disk reserve reached id=${row.id}")
                            return RecordingFailure.NO_SPACE
                        }
                        val read = input.read(buffer)
                        if (read < 0) {
                            // End of body means opposite things for the two kinds of recording. An
                            // archive stream ends because it has served the whole programme; a live
                            // stream never ends of its own accord, so when it does the provider
                            // dropped us and the right answer is to reconnect and append.
                            return if (RecordingSchedule.isCatchUp(row)) {
                                RecordingFailure.NONE
                            } else {
                                RecordingFailure.NETWORK
                            }
                        }
                        storageLeases[row.id]?.reserve(written + read)
                        out.write(buffer, 0, read)
                        written += read
                        storageLeases[row.id]?.commit(written)
                        val now = clock()
                        if (now - lastTick > PROGRESS_INTERVAL_MS) {
                            lastTick = now
                            val bytes = target.length()
                            recordingDao.updateProgress(
                                id = row.id,
                                status = RecordingStatus.RECORDING,
                                failure = RecordingFailure.NONE,
                                bytes = bytes,
                                filePath = target.stored,
                                startedAt = row.startedAt ?: now,
                                endedAt = null,
                                timestamp = now,
                            )
                            onProgress(RecordingProgress(row.id, row.title, row.channelName, bytes, row.stopMs))
                        }
                    }
                }
            }
    }

    /**
     * Record an HLS channel: poll the media playlist, append every segment that is new, repeat until
     * the window closes.
     *
     * **The playlist is re-fetched every cycle and no segment URL is ever kept.** Several providers
     * sign each segment individually, and a URL cached for one cycle is a 403 in the next — the same
     * cause behind the Live TV black screen recorded in `owntv-live-403-signed-segments`. Segments are
     * identified by their **media sequence number**, not by their URL, so a signature that changes
     * between polls does not make an old segment look new.
     *
     * **Encrypted playlists are refused, not attempted** (§1.5). Writing `#EXT-X-KEY`-protected
     * segments out unchanged produces a file of exactly the right size that will not play, and a
     * recording the user only discovers is worthless when they sit down to watch it is worse than
     * one that said no at the start.
     */
    private suspend fun recordHls(
        row: RecordingEntity, target: MediaTarget, playlistUrl: String,
        userAgent: String, headers: Map<String, String>,
        onProgress: (RecordingProgress) -> Unit,
    ): RecordingFailure {
        val session = hlsSessions[row.id] ?: try {
            val directory = hlsDirectory(row.id, target)
            val store = RecordingPlaybackStore(directory) { idle -> playbackStores.remove(row.id, idle) }
            HlsCaptureSession(directory, store).also { playbackStores[row.id] = store; hlsSessions[row.id] = it }
        } catch (error: MediaStorageBudget.Refused) { throw error }
        catch (_: Exception) { throw UnsupportedHls() }
        var lastInit: HlsMediaPlaylist.Init? = null
        var verifiedResume = row.id !in resumePending
        while (currentCoroutineContext().isActive) {
            if (RecordingRules.shouldStop(clock(), row.stopMs)) return RecordingFailure.NONE
            if (!hlsHasSpace(row.id, session, target)) return RecordingFailure.NO_SPACE
            var snapshot = loadHlsPlaylist(playlistUrl, userAgent, headers, session)
            val playlist = snapshot.playlist
            session.playback.configureTarget(playlist.targetDurationSecs)
            if (playlist.isEncrypted) return RecordingFailure.ENCRYPTED
            if (playlist.invalid || playlist.isMaster) return RecordingFailure.UNSUPPORTED_FORMAT
            if (!verifiedResume) {
                val segment = ArchiveResumePolicy.validatedTail(session, row, playlist) ?: return RecordingFailure.ARCHIVE_CHANGED
                val tail = session.entries.last()
                val tailUrl = absoluteUrl(snapshot.url, segment.uri) ?: return RecordingFailure.UNSUPPORTED_FORMAT
                val proof = File(session.directory, "resume.download")
                try {
                    if (!downloadHls(tailUrl, userAgent, headers, segment.range, proof)) return RecordingFailure.STREAM_UNAVAILABLE
                    if (!ArchiveResumePolicy.proofMatches(tail, proof)) return RecordingFailure.ARCHIVE_CHANGED
                } finally { proof.delete() }
                verifiedResume = true
                resumePending.remove(row.id)
                recordingDao.setArchivePaused(row.id, false, clock())
            }
            if (RecordingSchedule.isCatchUp(row) && session.archiveIdentity == null) {
                session.archiveTimeline = ArchiveResumePolicy.timeline(playlist)
                if (session.archiveTimeline != null) {
                    session.archiveIdentity = ArchiveResumePolicy.identity(row)
                    session.persist()
                }
            }
            if (session.lastSequence >= 0 && playlist.mediaSequence > session.lastSequence + 1) {
                val count = playlist.mediaSequence - session.lastSequence - 1
                session.skip(playlist.mediaSequence - 1,
                    (count * playlist.targetDurationSecs * 1000).toLong().coerceAtLeast(0),
                    count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                updateHlsCapture(row, target, session, onProgress)
            }
            for (original in playlist.segments) {
                currentCoroutineContext().ensureActive()
                if (original.sequence <= session.lastSequence) continue
                if (RecordingRules.shouldStop(clock(), row.stopMs)) return RecordingFailure.NONE
                if (!hlsHasSpace(row.id, session, target)) return RecordingFailure.NO_SPACE
                if (original.gap) {
                    session.skip(original.sequence, HlsRecordingPlan.durationMs(original))
                    updateHlsCapture(row, target, session, onProgress)
                    continue
                }
                val committed = HlsSegmentRetry.capture(original.sequence, snapshot,
                    segmentOf = { fresh, sequence -> fresh.playlist.segments.firstOrNull { it.sequence == sequence } },
                    refresh = {
                        loadHlsPlaylist(playlistUrl, userAgent, headers, session).also {
                            if (it.playlist.isEncrypted) return RecordingFailure.ENCRYPTED
                        }
                    },
                    capture = captureSegment@{ fresh, current ->
                        snapshot = fresh
                        if (RecordingRules.shouldStop(clock(), row.stopMs)) return RecordingFailure.NONE
                        val container = if (current.init != null) "fmp4" else "ts"
                        if (session.container != null && session.container != container) return RecordingFailure.UNSUPPORTED_FORMAT
                        if (container == "fmp4" && session.discontinuity != null &&
                            session.discontinuity != current.discontinuity) return RecordingFailure.UNSUPPORTED_FORMAT
                        session.container = container
                        session.discontinuity = current.discontinuity
                        val map = current.init
                        if (map != null && map != lastInit) {
                            val initUrl = absoluteUrl(snapshot.url, map.uri) ?: return RecordingFailure.UNSUPPORTED_FORMAT
                            val initScratch = File(session.directory, "init.download")
                            if (!downloadHls(initUrl, userAgent, headers, map.range, initScratch)) return@captureSegment false
                            if (!HlsSegmentTransfer.hasMp4Box(initScratch, "moov")) {
                                initScratch.delete(); return RecordingFailure.UNSUPPORTED_FORMAT
                            }
                            if (session.initHash != null && session.initHash != HlsCaptureSession.sha256(initScratch)) {
                                initScratch.delete(); return RecordingFailure.UNSUPPORTED_FORMAT
                            }
                            session.commitInit(initScratch)
                            updateHlsCapture(row, target, session, onProgress)
                            lastInit = map
                        }
                        if (!hlsHasSpace(row.id, session, target)) return RecordingFailure.NO_SPACE
                        val segmentUrl = absoluteUrl(snapshot.url, current.uri) ?: return RecordingFailure.UNSUPPORTED_FORMAT
                        val scratch = File(session.directory, "segment.download")
                        if (!downloadHls(segmentUrl, userAgent, headers, current.range, scratch)) return@captureSegment false
                        val supported = if (container == "fmp4") HlsSegmentTransfer.hasMp4Box(scratch, "moof")
                            && HlsSegmentTransfer.hasMp4Box(scratch, "mdat") else HlsSegmentTransfer.isTransportStream(scratch)
                        if (!supported) { scratch.delete(); return RecordingFailure.UNSUPPORTED_FORMAT }
                        session.commit(current, scratch)
                        updateHlsCapture(row, target, session, onProgress)
                        true
                    },
                )
                if (!committed) {
                    session.skip(original.sequence, HlsRecordingPlan.durationMs(original))
                    android.util.Log.w(TAG, "HLS gap id=${row.id} seq=${original.sequence}")
                    updateHlsCapture(row, target, session, onProgress)
                }
            }
            if (playlist.endList) return RecordingFailure.NONE
            delay(playlist.pollIntervalMs)
        }
        return RecordingFailure.NONE
    }

    private data class HlsSnapshot(val url: String, val playlist: HlsMediaPlaylist)
    private class UnsupportedHls : java.io.IOException()

    /** At most three levels of master indirection, with all responses closed before another request. */
    private suspend fun loadHlsPlaylist(
        initialUrl: String, agent: String, headers: Map<String, String>, session: HlsCaptureSession,
    ): HlsSnapshot {
        var url = initialUrl
        repeat(3) { depth ->
            val snapshot = recordingHttp(request(url, agent, headers)) { response ->
                if (!response.isSuccessful) throw java.io.IOException("HLS playlist HTTP ${response.code}")
                val text = response.body.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (out.size() + read > HLS_MAX_PLAYLIST_BYTES) throw UnsupportedHls()
                        out.write(buffer, 0, read)
                    }
                    out.toString("UTF-8")
                }
                HlsSnapshot(response.request.url.toString(), HlsMediaPlaylist.parse(text))
            }
            if (snapshot.playlist.invalid) throw UnsupportedHls()
            if (snapshot.playlist.isEncrypted || !snapshot.playlist.isMaster) return snapshot
            val fixed = session.variantIdentity?.split('/')?.getOrNull(depth)
            val selected = HlsRecordingPlan.selectVariant(snapshot.playlist, fixed) ?: throw UnsupportedHls()
            val identities = session.variantIdentity?.split('/')?.toMutableList() ?: mutableListOf()
            if (identities.size <= depth) identities += HlsRecordingPlan.identity(selected)
            session.variantIdentity = identities.joinToString("/")
            session.persist()
            url = absoluteUrl(snapshot.url, selected.uri) ?: throw UnsupportedHls()
        }
        throw UnsupportedHls()
    }

    private suspend fun downloadHls(
        url: String, agent: String, headers: Map<String, String>, range: HlsMediaPlaylist.ByteRange?, file: File,
    ): Boolean {
        val req = request(url, agent, headers).newBuilder().apply { if (range != null) header("Range", range.header) }.build()
        return try {
            recordingHttp(req) { response ->
                if (!response.isSuccessful) return@recordingHttp false
                if (range != null && !HlsRangeValidation.accepts(response.code, response.header("Content-Range"), range)) {
                    return@recordingHttp false // Never append a complete resource in place of a range.
                }
                val context = currentCoroutineContext()
                response.body.byteStream().use { input ->
                    HlsSegmentTransfer.copy(input, file, range?.length ?: response.body.contentLength().takeIf { it >= 0 }) {
                        context.ensureActive()
                    }
                }
                true
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: MediaStorageBudget.Refused) { file.delete(); throw error }
        catch (_: Exception) { file.delete(); false }
    }

    private suspend fun <T> recordingHttp(req: Request, body: suspend (okhttp3.Response) -> T): T {
        val call = client.newCall(req)
        return cancelBlockingCallWithCoroutine(call::cancel) { call.execute().use { body(it) } }
    }

    private fun hlsDirectory(id: Long, target: MediaTarget): File =
        storageBudget.spool(RecordingParts.spoolTarget(target), ".owntv-hls-$id")

    private suspend fun hlsHasSpace(id: Long, session: HlsCaptureSession, target: MediaTarget): Boolean {
        val next = HlsSegmentTransfer.MAX_SEGMENT_BYTES
        val desired = session.capturedBytes + session.initFile.length() + next
        val multipart = storageLeases[id]?.requiresParts == true
        val copies = if (multipart) 1L else (if (session.container == "fmp4") 2L else 1L) + if (target is MediaTarget.Document) 1L else 0L
        storageLeases[id]?.reserve(desired, copies * desired + HLS_FINAL_HEADROOM_BYTES,
            perFileBytes = if (multipart) RecordingPartsPlan.MAX_PART_BYTES else desired)
        return session.directory.usableSpace > storageBudget.reserveFloor(target) + copies * desired + next
    }

    private suspend fun updateHlsCapture(
        row: RecordingEntity, target: MediaTarget, session: HlsCaptureSession,
        onProgress: (RecordingProgress) -> Unit,
    ) {
        recordingDao.updateCapture(row.id, session.capturedDurationMs, session.missingDurationMs,
            session.gapCount, session.checkpoint(), clock())
        val bytes = session.capturedBytes + session.initFile.length()
        storageLeases[row.id]?.commit(bytes)
        recordingDao.updateProgress(row.id, RecordingStatus.RECORDING, RecordingFailure.NONE, bytes,
            target.stored, row.startedAt ?: clock(), null, clock())
        onProgress(RecordingProgress(row.id, row.title, row.channelName, bytes, row.stopMs))
    }

    private suspend fun finishHlsSession(row: RecordingEntity, target: MediaTarget, session: HlsCaptureSession): MuxResult {
        recordingDao.updateCapture(row.id, session.capturedDurationMs, session.missingDurationMs,
            session.gapCount, session.checkpoint(), clock())
        if (session.entries.isEmpty()) { session.discard(); return MuxResult(true, null) }
        if (storageLeases[row.id]?.requiresParts == true) {
            return try {
                val sourceBytes = session.capturedBytes + session.initFile.length()
                val (index, bytes) = RecordingParts.finish(context, target, row.id, session,
                    onCommitted = { written -> storageLeases[row.id]?.commit(sourceBytes + written) }) { written ->
                    storageLeases[row.id]?.reserve(sourceBytes + written,
                        (session.capturedBytes + session.initFile.length() - written).coerceAtLeast(0) + HLS_FINAL_HEADROOM_BYTES,
                        perFileBytes = RecordingPartsPlan.MAX_PART_BYTES)
                }
                recordingDao.updateProgress(row.id, RecordingStatus.RECORDING, RecordingFailure.NONE,
                    bytes, index.stored, row.startedAt ?: clock(), null, clock())
                storageLeases[row.id]?.commit(bytes)
                if (index.stored != target.stored && !RecordingParts.isIndex(target)) target.delete()
                session.discard()
                MuxResult(true, index, bytes = bytes)
            } catch (error: MediaStorageBudget.Refused) { MuxResult(false, null, storageFailure(error)) }
            catch (error: Exception) { MuxResult(false, null,
                storageBudget.classifyWriteFailure(error, target)?.let(::storageFailure) ?: RecordingFailure.UNKNOWN) }
        }
        val required = session.capturedBytes * ((if (session.container == "fmp4") 2L else 1L) + if (target is MediaTarget.Document) 1L else 0L) +
            session.initFile.length() + HLS_FINAL_HEADROOM_BYTES
        storageLeases[row.id]?.reserve(session.capturedBytes + session.initFile.length(), required)
        val reserve = storageBudget.reserveFloor(target)
        if (session.directory.usableSpace <= reserve + required ||
            target.usableSpace() <= reserve + session.capturedBytes) {
            return MuxResult(false, null, RecordingFailure.NO_SPACE)
        }
        val aggregate = File(session.directory, "assembled.bin")
        val destination = if (session.container == "fmp4" && target is MediaTarget.Path)
            MediaTarget.Path(File(target.file.parentFile, RecordingRules.muxedNameOf(target.file.name))) else target
        val finalized = File(session.directory, "finalized.mp4")
        return try {
            java.io.FileOutputStream(aggregate).use { out ->
                if (session.container == "fmp4") session.initFile.inputStream().use { it.copyTo(out, BUFFER_BYTES) }
                session.inputs().forEach { file -> file.inputStream().use { it.copyTo(out, BUFFER_BYTES) } }
                out.fd.sync()
            }
            val ready = if (session.container == "fmp4") {
                if (!HlsRemux.mux(aggregate, finalized) {
                    session.directory.usableSpace > reserve
                }) return MuxResult(false, null)
                finalized
            } else aggregate
            storageLeases[row.id]?.reserve(ready.length(), if (destination is MediaTarget.Document) ready.length() else 0L)
            if (destination is MediaTarget.Path) {
                java.nio.file.Files.move(ready.toPath(), destination.file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            } else {
                ready.inputStream().use { input -> destination.openOutput(false).use { input.copyTo(it, BUFFER_BYTES) } }
            }
            // Persist the promoted path before clearing the resumable spool. A crash before this
            // update retries promotion from the spool; a crash after it still finds the right file.
            recordingDao.updateProgress(row.id, RecordingStatus.RECORDING, RecordingFailure.NONE,
                destination.length(), destination.stored, row.startedAt ?: clock(), null, clock())
            if (destination.stored != target.stored) target.delete()
            storageLeases[row.id]?.commit(destination.length())
            session.discard()
            MuxResult(true, destination)
        } catch (error: MediaStorageBudget.Refused) { MuxResult(false, null, storageFailure(error)) }
        catch (error: Exception) { MuxResult(false, null,
            storageBudget.classifyWriteFailure(error, target)?.let(::storageFailure) ?: RecordingFailure.UNSUPPORTED_FORMAT) }
        finally { aggregate.delete(); finalized.delete() }
    }

    /** A process death releases every local reader; only explicitly finalized captures may lose their spool. */
    private suspend fun cleanupFinalizedPlaybackSpools() {
        if (!playbackOrphansReconciled.compareAndSet(false, true)) return
        try {
            for (row in recordingDao.storageRows()) {
                if (row.finalizationFailure != RecordingFailure.NONE || !RecordingIntegrity.canPlay(row) ||
                    active.containsKey(row.id) || hasPlaybackReaders(row.id)) continue
                val target = MediaTarget.of(context, row.filePath) ?: continue
                if (!storageBudget.destinationAvailable(target) || target.length() <= 0) continue
                val directory = runCatching { hlsDirectory(row.id, target) }.getOrNull() ?: continue
                if (!directory.isDirectory) continue
                withContext(Dispatchers.IO) {
                    directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                    directory.delete()
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            playbackOrphansReconciled.set(false)
            throw cancelled
        } catch (_: Exception) { playbackOrphansReconciled.set(false) }
    }

    private suspend fun recoverInterruptedHlsRecordings() {
        for (row in recordingDao.running()) {
            if (row.archivePaused && !active.containsKey(row.id) && row.id !in suppressed) {
                recordingDao.update(row.copy(status = RecordingStatus.PARTIAL, endedAt = clock(), updatedAt = clock()))
                continue
            }
            if (active.containsKey(row.id) || row.id in suppressed) continue
            val target = MediaTarget.of(context, row.filePath) ?: continue
            if (RecordingParts.isIndex(target)) {
                // Promotion precedes source disposal/status update. A killed process must not tune again into the index.
                withContext(Dispatchers.IO) {
                    val bytes = runCatching { RecordingParts.confirmedBytes(context, target, row.id) }.getOrNull()
                    finish(row, bytes ?: row.bytes, RecordingFailure.UNKNOWN, row.startedAt ?: clock(),
                        target.stored, incomplete = true, captureFailure = RecordingFailure.UNKNOWN,
                        finalizationFailure = if (bytes != null) RecordingFailure.NONE else RecordingFailure.UNSUPPORTED_FORMAT)
                }
                continue
            }
            if (!RecordingRules.shouldStop(clock(), row.stopMs)) continue
            val directory = runCatching { hlsDirectory(row.id, target) }.getOrNull() ?: continue
            if (!File(directory, "checkpoint.json").isFile) continue
            withContext(Dispatchers.IO) {
                var lease: MediaStorageBudget.Lease? = null
                try {
                    lease = storageBudget.acquire(target)
                    storageLeases[row.id] = lease
                    val session = HlsCaptureSession(directory)
                    val result = finishHlsSession(row, target, session)
                    val finished = result.movedTo ?: target
                    val bytes = maxOf(result.bytes ?: finished.length(), if (result.succeeded) 0 else row.bytes)
                    finish(row, bytes, if (result.succeeded) RecordingFailure.NETWORK else
                        result.failure, row.startedAt ?: clock(), finished.stored, incomplete = true,
                        captureFailure = RecordingFailure.UNKNOWN,
                        finalizationFailure = if (result.succeeded) RecordingFailure.NONE else result.failure)
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    val failure = if (error is MediaStorageBudget.Refused) storageFailure(error) else RecordingFailure.UNSUPPORTED_FORMAT
                    finish(row, maxOf(row.bytes, target.length()), failure, row.startedAt ?: clock(), incomplete = true,
                        captureFailure = RecordingFailure.UNKNOWN, finalizationFailure = failure)
                } finally {
                    withContext(NonCancellable) {
                        val latest = recordingDao.getById(row.id)
                        val savedTarget = latest?.filePath?.let { MediaTarget.of(context, it) } ?: target
                        val sourceBytes = runCatching { if (hlsDirectory(row.id, savedTarget).isDirectory) latest?.bytes ?: 0 else 0L }.getOrDefault(0)
                        val packageBytes = runCatching { RecordingParts.stagedBytes(context, savedTarget, row.id) }.getOrDefault(0)
                        lease?.close((if (RecordingParts.isIndex(savedTarget)) (latest?.bytes ?: 0) + savedTarget.length() + sourceBytes
                            else maxOf(target.length(), latest?.bytes ?: 0)) + packageBytes)
                        storageLeases.remove(row.id)
                    }
                }
            }
        }
    }

    /** Expired raw streams also need an honest terminal state after process death. */
    private suspend fun finishExpiredOrphans() {
        for (row in recordingDao.running()) {
            if (row.archivePaused && !active.containsKey(row.id) && row.id !in suppressed) {
                recordingDao.update(row.copy(status = RecordingStatus.PARTIAL, endedAt = clock(), updatedAt = clock()))
                continue
            }
            if (active.containsKey(row.id) || row.id in suppressed || !RecordingRules.shouldStop(clock(), row.stopMs)) continue
            val target = MediaTarget.of(context, row.filePath)
            finish(row, maxOf(row.bytes, target?.length() ?: 0L), RecordingFailure.NETWORK,
                row.startedAt ?: clock(), incomplete = true, captureFailure = RecordingFailure.UNKNOWN)
        }
    }

    /**
     * Record a DASH channel: poll the manifest, fetch every segment that is new, repeat until the
     * window closes.
     *
     * **The difference from [recordHls], and the only one that matters.** A multiplexed HLS TS segment is MPEG-TS
     * with audio and video already multiplexed, so concatenation produces a playable file. DASH
     * normally keeps them in two Representations, so the two halves are written to a temp file each
     * and muxed together at the end — see [finishDashSession]. A Representation that already carries
     * both needs none of that and is written straight into the recording, exactly as HLS is.
     *
     * **The Representations are chosen once and kept**, held in [dashSessions] so a reconnect
     * continues the same two files rather than starting a second pair at a different quality. A
     * resolution or codec change partway through is precisely what the mux cannot absorb.
     *
     * **The manifest is re-read every cycle and no segment URL is ever kept**, for the same reason
     * [recordHls] documents: several providers sign each segment individually, so a URL cached for
     * one cycle is a 403 in the next. Segments are identified by their **number**, never their URL.
     *
     * **A protected manifest is refused, not attempted.** The DASH `#EXT-X-KEY`: it can only be found
     * by asking, and writing the segments out unchanged would produce a file of the right size that
     * will not play.
     */
    private suspend fun recordDash(
        row: RecordingEntity,
        target: MediaTarget,
        manifestUrl: String,
        userAgent: String,
        headers: Map<String, String>,
        onProgress: (RecordingProgress) -> Unit,
    ): RecordingFailure {
        while (currentCoroutineContext().isActive) {
            if (RecordingRules.shouldStop(clock(), row.stopMs)) return RecordingFailure.NONE
            if (!RecordingRules.hasSpace(target.usableSpace())) {
                android.util.Log.w(TAG, "recording stopped, disk reserve reached id=${row.id}")
                return RecordingFailure.NO_SPACE
            }
            val text = client.newCall(request(manifestUrl, userAgent, headers)).execute().use { response ->
                if (!response.isSuccessful) return RecordingFailure.STREAM_UNAVAILABLE
                response.body.string()
            }
            val manifest = DashManifest.parse(text) ?: return RecordingFailure.STREAM_UNAVAILABLE
            if (manifest.contentProtected) {
                android.util.Log.w(TAG, "recording refused, protected manifest id=${row.id}")
                return RecordingFailure.DRM_PROTECTED
            }

            val session = dashSessions[row.id] ?: run {
                val selection = DashRecordingPlan.selectTracks(manifest)
                    ?: return RecordingFailure.STREAM_UNAVAILABLE
                openDashSession(row, target, selection).also { dashSessions[row.id] = it }
            }

            var wrote = false
            // Whichever track was resolved first sets the poll rate: the tracks of one manifest share
            // a segment duration, and `minimumUpdatePeriod` usually decides it anyway.
            var pace: DashRepresentation? = null
            for (track in session.tracks) {
                // Resolved fresh from this cycle's manifest — a SegmentTimeline lists different
                // segments every time — but always by the id chosen at the start.
                val representation = manifest.representations.firstOrNull { it.id == track.id }
                    ?: run {
                        // The quality we fixed on has gone. Said as a failure rather than quietly
                        // continued at another one, which would change codec or resolution mid-file.
                        android.util.Log.w(TAG, "recording lost its representation id=${row.id} rep=${track.id}")
                        return RecordingFailure.STREAM_UNAVAILABLE
                    }
                if (pace == null) pace = representation
                if (!track.initWritten) {
                    // The initialisation segment carries the codec configuration. Without it first,
                    // the file is a stream of fragments nothing can read — so this is fatal, unlike
                    // a media segment, which is only a gap.
                    val init = representation.initializationUrl
                    if (init != null && !writeDashSegment(row, track, target, manifestUrl, init, userAgent, headers)) {
                        return RecordingFailure.STREAM_UNAVAILABLE
                    }
                    track.initWritten = true
                }
                val plan = DashRecordingPlan.nextSegments(manifest, representation, track.lastNumber, clock())
                if (plan.unschedulable) {
                    android.util.Log.w(TAG, "recording cannot schedule manifest id=${row.id} rep=${track.id}")
                    return RecordingFailure.STREAM_UNAVAILABLE
                }
                for (segment in plan.segments) {
                    if (!currentCoroutineContext().isActive) return RecordingFailure.NONE
                    if (RecordingRules.shouldStop(clock(), row.stopMs)) return RecordingFailure.NONE
                    if (!RecordingRules.hasSpace(target.usableSpace())) return RecordingFailure.NO_SPACE
                    val ok = writeDashSegment(
                        row, track, target, manifestUrl, segment.url, userAgent, headers,
                    )
                    // One segment the provider would not serve is a gap, not a failure — the same
                    // judgement recordHls makes, and for the same reason.
                    if (!ok) {
                        android.util.Log.w(TAG, "segment refused id=${row.id} rep=${track.id} n=${segment.number}")
                    }
                    wrote = true
                }
                track.lastNumber = plan.lastNumber
            }
            report(row, target, onProgress)

            // A static manifest is a finite window — catch-up, usually. It is finished when a whole
            // cycle finds nothing new, which is not the same as one pass: a numbered template is
            // deliberately taken a bounded number of segments at a time.
            if (!manifest.dynamic) {
                if (!wrote) return RecordingFailure.NONE
                continue
            }
            pace?.let { delay(DashRecordingPlan.pollIntervalMs(manifest, it)) }
        }
        return RecordingFailure.NONE
    }

    /**
     * Open somewhere for each chosen Representation to be written.
     *
     * A single Representation carrying everything is written **straight into the recording** — no
     * temp file, no mux, nothing to go wrong, exactly as HLS behaves. Two separate tracks each get a
     * temp file, which is what [finishDashSession] later muxes together.
     *
     * Temp files sit **beside the recording** when it is an ordinary file, so they share its volume
     * and its space budget. Document captures use an app-specific folder on the same volume.
     */
    private fun openDashSession(
        row: RecordingEntity,
        target: MediaTarget,
        selection: DashTrackSelection,
    ): DashSession {
        val needsMux = selection.needsMux
        val directory = storageBudget.spool(target, ".owntv-dash").parentFile ?: throw MediaStorageBudget.Refused(MediaStorageBudget.Reason.UNAVAILABLE)
        val tracks = selection.tracks.mapIndexed { index, representation ->
            DashTrack(
                id = representation.id,
                kind = representation.kind,
                temp = if (needsMux) File(directory, "$TEMP_PREFIX${row.id}-$index$TEMP_SUFFIX") else null,
            )
        }
        // A reconnect reuses this session; a *new* recording must never inherit a previous one's
        // half-written fragments, so anything left behind under these names is cleared first.
        tracks.forEach { it.temp?.delete() }
        android.util.Log.i(
            TAG,
            "dash recording id=${row.id} tracks=${tracks.joinToString(",") { "${it.kind}:${it.id}" }} mux=$needsMux",
        )
        return DashSession(tracks = tracks, needsMux = needsMux)
    }

    /** One segment or initialisation segment, appended to wherever its track is being written. */
    private suspend fun writeDashSegment(
        row: RecordingEntity,
        track: DashTrack,
        target: MediaTarget,
        manifestUrl: String,
        url: String,
        userAgent: String,
        headers: Map<String, String>,
    ): Boolean {
        // Resolved against the manifest's *final* URL, so relative segment paths follow the redirects
        // rather than the address we asked for.
        val absolute = absoluteUrl(manifestUrl, url) ?: return false
        return runCatching {
            client.newCall(request(absolute, userAgent, headers)).execute().use { response ->
                if (!response.isSuccessful) return@use false
                // Opened per segment rather than held open, exactly as recordHls does: a recording
                // that is killed mid-programme keeps every byte already flushed.
                val out = track.temp?.let { java.io.FileOutputStream(it, true) } ?: target.openOutput(append = true)
                var written = dashSessions[row.id]?.tracks?.sumOf { it.temp?.length() ?: 0L }
                    ?.takeIf { it > 0 } ?: target.length()
                out.use { sink -> response.body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        val desired = written + read
                        val extra = if (track.temp != null) desired * (if (target is MediaTarget.Document) 2L else 1L) else 0L
                        storageLeases[row.id]?.reserve(desired, extra)
                        sink.write(buffer, 0, read)
                        written = desired
                        storageLeases[row.id]?.commit(written)
                    }
                } }
                true
            }
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException || error is MediaStorageBudget.Refused) throw error
            android.util.Log.w(TAG, "segment failed id=${row.id} rep=${track.id}: ${error.message}")
            false
        }
    }

    /**
     * End a DASH recording: put its two halves together and clear up after it.
     *
     * Called from [runRecording]'s `finally`, so it runs however the recording ended — the window
     * closing, the user stopping it, the overrun backstop cancelling it, or a failure. Does nothing
     * at all for a recording that was not DASH, or one written straight into the file.
     *
     * Returns where the finished recording now is, when muxing moved it. A muxed recording is an
     * **MP4**, so it is given an `.mp4` name rather than the `.ts` a live recording normally gets:
     * [RecordingRules.fileName] chooses `.ts` because a transport stream survives being cut off, but
     * the file this produces is not one, and a file manager should not be told otherwise.
     */
    private suspend fun finishDashSession(id: Long, target: MediaTarget): MuxResult? {
        val session = dashSessions.remove(id) ?: return null
        if (!session.needsMux) return null
        val temps = session.tracks.mapNotNull { it.temp }
        val result = try { withContext(Dispatchers.IO) { muxInto(id, target, temps) } }
            catch (error: MediaStorageBudget.Refused) { MuxResult(false, null, storageFailure(error)) }
            catch (error: Exception) { MuxResult(false, null,
                storageBudget.classifyWriteFailure(error, target)?.let(::storageFailure) ?: RecordingFailure.UNKNOWN) }
        // **Kept when the mux failed.** They are the recording — a few gigabytes of it — and deleting
        // them on the one path where the finished file does not exist would throw away everything the
        // recording captured. `recoverInterruptedDashRecordings` tries them once more on the next
        // pass, and clears them then if it still cannot.
        if (result.succeeded) temps.forEach { it.delete() }
        return result
    }

    /**
     * Mux [temps] into the recording, and say where it ended up.
     *
     * An ordinary file is muxed **directly** into its `.mp4` sibling and the empty `.ts` removed — a
     * recording can be several gigabytes, and copying it afterwards would double both the time and
     * the space. A Storage Access Framework document has no sibling to write to, so it is muxed in
     * the cache and copied in; that path keeps the name it was given.
     */
    private suspend fun muxInto(id: Long, target: MediaTarget, temps: List<File>): MuxResult {
        val reserve = try { storageBudget.reserveFloor(target) }
            catch (error: MediaStorageBudget.Refused) { return MuxResult(false, null, storageFailure(error)) }
        val required = temps.sumOf { it.length() } * if (target is MediaTarget.Document) 2L else 1L
        if (target.usableSpace() < reserve + required + HLS_FINAL_HEADROOM_BYTES)
            return MuxResult(false, null, RecordingFailure.NO_SPACE)
        if (target is MediaTarget.Path) {
            val muxed = File(target.file.parentFile, RecordingRules.muxedNameOf(target.file.name))
            if (!DashRemux.mux(temps, muxed)) {
                android.util.Log.w(TAG, "dash mux produced nothing id=$id")
                muxed.delete()
                return MuxResult(succeeded = false, movedTo = null)
            }
            try { storageLeases[id]?.reserve(muxed.length()) }
            catch (error: MediaStorageBudget.Refused) { muxed.delete(); return MuxResult(false, null, storageFailure(error)) }
            storageLeases[id]?.commit(muxed.length())
            // Only once there is something to replace it with.
            target.delete()
            android.util.Log.i(TAG, "dash recording muxed id=$id bytes=${muxed.length()}")
            return MuxResult(succeeded = true, movedTo = MediaTarget.Path(muxed))
        }
        val scratch = storageBudget.spool(target, "$TEMP_PREFIX$id$MUXED_SUFFIX")
        return try {
            if (!DashRemux.mux(temps, scratch)) {
                android.util.Log.w(TAG, "dash mux produced nothing id=$id")
                return MuxResult(succeeded = false, movedTo = null)
            }
            storageLeases[id]?.reserve(scratch.length(), scratch.length())
            scratch.inputStream().use { input ->
                target.openOutput(append = false).use { out -> input.copyTo(out, BUFFER_BYTES) }
            }
            storageLeases[id]?.commit(target.length())
            android.util.Log.i(TAG, "dash recording muxed id=$id bytes=${scratch.length()}")
            MuxResult(succeeded = true, movedTo = null)
        } catch (error: MediaStorageBudget.Refused) { MuxResult(false, null, storageFailure(error)) }
        catch (e: Exception) {
            android.util.Log.w(TAG, "dash mux could not be stored id=$id: ${e.message}")
            MuxResult(succeeded = false, movedTo = null)
        } finally {
            scratch.delete()
        }
    }

    /**
     * Put back together any DASH recording whose two halves were captured but never muxed, because
     * the process died before it could.
     *
     * **This is what makes the temp files a safety net rather than debris.** The mux runs once, at
     * the end — `MediaMuxer` cannot append to an existing MP4, so remuxing every couple of minutes
     * would mean redoing the whole recording each time, sixty times over a two-hour programme. The
     * temp files are the crash-proof part instead: each is a valid fragmented-MP4 stream, flushed a
     * segment at a time, and this turns them into a playable recording on the next run.
     *
     * Runs once per drain, before anything starts, so it can never collide with a live recording.
     *
     * **A recording whose window is still open is left alone** and simply restarts, losing what it
     * captured before the crash. Resuming into the same temp files would need certainty that this run
     * picks the same Representations as the last one, and nothing on disk records what those were.
     */
    private suspend fun recoverInterruptedDashRecordings() {
        for (row in recordingDao.running()) {
            if (row.archivePaused && !active.containsKey(row.id) && row.id !in suppressed) {
                recordingDao.update(row.copy(status = RecordingStatus.PARTIAL, endedAt = clock(), updatedAt = clock()))
                continue
            }
            if (active.containsKey(row.id) || row.id in suppressed) continue
            if (!RecordingRules.shouldStop(clock(), row.stopMs)) continue
            val target = MediaTarget.of(context, row.filePath) ?: continue
            val temps = orphanedDashTemps(row.id, target)
            if (temps.isEmpty()) continue
            android.util.Log.i(TAG, "recovering interrupted dash recording id=${row.id} parts=${temps.size}")
            val result = withContext(Dispatchers.IO) { muxInto(row.id, target, temps) }
            // Keep recoverable tracks after storage/mux failure; delete only after promotion.
            if (result.succeeded) temps.forEach { it.delete() }
            val finished = result.movedTo ?: target
            finish(row, maxOf(finished.length(), if (result.succeeded) 0 else temps.sumOf { it.length() }), if (result.succeeded) RecordingFailure.NETWORK else
                result.failure, row.startedAt ?: clock(), finished.stored, incomplete = true,
                        captureFailure = RecordingFailure.UNKNOWN,
                        finalizationFailure = if (result.succeeded) RecordingFailure.NONE else result.failure)
        }
    }

    /** Temp files left behind for [id], wherever that recording was writing them. */
    private fun orphanedDashTemps(id: Long, target: MediaTarget): List<File> {
        val directory = runCatching { storageBudget.spool(target, ".owntv-dash").parentFile }.getOrNull()
        val prefix = "$TEMP_PREFIX$id-"
        return directory
            ?.listFiles { file -> file.isFile && file.name.startsWith(prefix) && file.name.endsWith(TEMP_SUFFIX) }
            ?.sortedBy { it.name }
            .orEmpty()
    }

    /** One request, carrying the channel's own headers and the User-Agent that goes with them. */
    private fun request(url: String, userAgent: String, headers: Map<String, String>): Request {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        headers.forEach { (name, value) -> if (!name.equals("User-Agent", true)) builder.header(name, value) }
        return builder.build()
    }

    /** A segment URI resolved against the playlist it came from; null when it is not a URL at all. */
    private fun absoluteUrl(base: String, uri: String): String? =
        runCatching { java.net.URI(base).resolve(uri).toString() }.getOrNull()

    /** Write the byte count down and tell the pill, at most twice a second. */
    private suspend fun report(row: RecordingEntity, target: MediaTarget, onProgress: (RecordingProgress) -> Unit) {
        val now = clock()
        if (now - lastReportAt < PROGRESS_INTERVAL_MS) return
        lastReportAt = now
        val bytes = maxOf(target.length(), dashSessions[row.id]?.tracks?.sumOf { it.temp?.length() ?: 0 } ?: 0)
        recordingDao.updateProgress(
            id = row.id,
            status = RecordingStatus.RECORDING,
            failure = RecordingFailure.NONE,
            bytes = bytes,
            filePath = target.stored,
            startedAt = row.startedAt ?: now,
            endedAt = null,
            timestamp = now,
        )
        onProgress(RecordingProgress(row.id, row.title, row.channelName, bytes, row.stopMs))
    }

    @Volatile
    private var lastReportAt = 0L

    /**
     * Write down how it ended. Uses `updateProgress` and never `upsert`: the table's unique index on
     * `(profileId, channelId, programmeStartMs)` would make a REPLACE delete this row and insert a
     * new one with a different id, orphaning anything still holding the old one.
     */
    private suspend fun finish(
        row: RecordingEntity,
        bytes: Long,
        failure: RecordingFailure,
        startedAt: Long,
        /** Where the file actually is — a muxed DASH recording has moved from `.ts` to `.mp4`. */
        filePath: String? = null,
        incomplete: Boolean = failure != RecordingFailure.NONE,
        captureFailure: RecordingFailure? = failure,
        finalizationFailure: RecordingFailure? = null,
    ) {
        recordingDao.updateFailures(row.id, captureFailure, finalizationFailure, clock())
        val (status, reason) = RecordingRules.outcomeOf(bytes, failure, incomplete)
        recordingDao.updateProgress(
            id = row.id,
            status = status,
            failure = reason,
            bytes = bytes,
            filePath = filePath ?: row.filePath,
            startedAt = row.startedAt ?: startedAt,
            endedAt = clock(),
            timestamp = clock(),
        )
    }

    /** Nothing was written and nothing will be: the programme is gone and the row says why (D10). */
    private suspend fun markMissed(row: RecordingEntity, failure: RecordingFailure) {
        if (row.archivePaused) {
            recordingDao.update(row.copy(status = RecordingStatus.PARTIAL, failure = failure, captureFailure = failure,
                endedAt = clock(), updatedAt = clock()))
            return
        }
        android.util.Log.i(TAG, "recording missed id=${row.id} reason=$failure")
        recordingDao.updateProgress(
            id = row.id,
            status = RecordingStatus.MISSED,
            failure = failure,
            bytes = 0,
            filePath = null,
            startedAt = null,
            endedAt = clock(),
            timestamp = clock(),
        )
    }

    /**
     * The URL and User-Agent this attempt should fetch, resolved **fresh every time**: a Stalker
     * portal mints a single-use link that dies long before a two-hour recording does.
     */
    private suspend fun resolveTarget(row: RecordingEntity): Pair<String, String> {
        val source = sourceDao.getById(row.sourceId)
        if (source == null || !streamUrlResolver.needsResolve(source)) {
            return row.streamUrl to (source?.userAgent?.takeIf { it.isNotBlank() } ?: HttpClient.DEFAULT_USER_AGENT)
        }
        val ua = source.userAgent?.takeIf { it.isNotBlank() } ?: StalkerClient.DEFAULT_MAG_USER_AGENT
        if (RecordingSchedule.isCatchUp(row)) {
            val remoteId = channelDao.getById(row.channelId)?.remoteId
                ?: throw java.io.IOException("Archive channel identity unavailable")
            return streamUrlResolver.resolveCatchup(source, remoteId, row.programmeStartMs, row.programmeStopMs) to ua
        }
        return streamUrlResolver.resolve(source, row.streamUrl, vod = false) to ua
    }

    /**
     * One DASH recording's fixed choice of Representations, and where each is being written.
     *
     * Lives for the whole recording rather than one attempt — see [dashSessions].
     */
    private class DashSession(
        val tracks: List<DashTrack>,
        /** True when the two halves still have to be put back together. */
        val needsMux: Boolean,
    )

    /** Whether the mux produced a playable file, and where it left it. */
    private class MuxResult(
        val succeeded: Boolean, val movedTo: MediaTarget?,
        val failure: RecordingFailure = RecordingFailure.UNSUPPORTED_FORMAT,
        val bytes: Long? = null,
    )

    /** One Representation being recorded, and how far through it we are. */
    private class DashTrack(
        /** The Representation id fixed on at the start, re-resolved against each new manifest. */
        val id: String,
        val kind: DashTrackKind,
        /** Its temp file, or null when this track is written straight into the recording. */
        val temp: File?,
        /** The last segment number written. −1 until the first cycle has run. */
        var lastNumber: Long = -1L,
        /** Whether the initialisation segment has been written, which it must be before any other. */
        var initWritten: Boolean = false,
    )

    private companion object {
        const val TAG = "RecordingEngine"
        const val BUFFER_BYTES = 128 * 1024
        const val PROGRESS_INTERVAL_MS = 500L

        /** Temp files for a DASH recording's separate tracks, named so a reconnect finds them again. */
        const val TEMP_PREFIX = "owntv-dash-"
        const val TEMP_SUFFIX = ".part"

        /** What a muxed DASH recording is, as opposed to the `.ts` a live recording normally gets. */
        const val MUXED_SUFFIX = ".mp4"

        /** Enough of the body to see whether the first line is `#EXTM3U`. */
        const val PLAYLIST_PEEK_BYTES = 1024L
        const val HLS_MAX_PLAYLIST_BYTES = 1024 * 1024
        const val HLS_FINAL_HEADROOM_BYTES = 32L * 1024 * 1024

        /** How often the drain loop looks for newly due rows and overrunning ones. */
        const val POLL_MS = 2_000L
    }
}

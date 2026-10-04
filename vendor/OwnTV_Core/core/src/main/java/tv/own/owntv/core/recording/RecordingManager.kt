package tv.own.owntv.core.recording

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.database.OwnTVDatabase
import tv.own.owntv.core.database.transaction
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.EpgDao
import tv.own.owntv.core.database.dao.RecordingDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.epg.CatchupUrl
import tv.own.owntv.core.epg.EpgSourceStore
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.database.entity.RecordingRuleEntity
import tv.own.owntv.core.live.StreamGrant
import tv.own.owntv.core.live.StreamPurpose
import tv.own.owntv.core.live.connectionBudget
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.live.OpenStreamRegistry
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.MediaFolders
import tv.own.owntv.core.storage.MediaRoot
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.parser.XtreamClient
import java.util.TimeZone

/**
 * The control half of recording — what the apps call. The bytes are [RecordingEngine]'s, running
 * inside [RecordingWorker] so they survive the user leaving.
 *
 * [RecordingDao] is the single source of truth; nothing here holds a queue.
 *
 * Recordings are written to `TV/` inside the same root downloads use (D1) — one folder the user
 * chose, three folders OwnTV keeps inside it.
 */
class RecordingManager(
    private val context: Context,
    private val recordingDao: RecordingDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val streams: OpenStreamRegistry,
    private val engine: RecordingEngine,
    private val scheduler: RecordingScheduler,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val db: OwnTVDatabase,
    private val customize: CustomizationStore,
    private val epgSourceStore: EpgSourceStore,
    private val archiveXtream: XtreamClient,
    private val archiveResolver: tv.own.owntv.core.stalker.StreamUrlResolver,
    private val storageBudget: tv.own.owntv.core.storage.MediaStorageBudget,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryMutex = kotlinx.coroutines.sync.Mutex()
    private val actionMutex = kotlinx.coroutines.sync.Mutex()

    init {
        // A row left RECORDING is one the process died in the middle of. If its window is still open
        // the engine picks it up and appends; if it has closed, rearmAll marks it missed.
        engine.markQueued()
        scope.launch {
            scheduler.rearmAll()
            RecordingWorker.kick(context)
        }
    }

    /**
     * Whether timers will fire at the right minute on this device. False when the user has revoked
     * the exact-alarm permission — recordings still happen, but they may start a few minutes late,
     * which is why the window they run in is given a bigger head start. The apps show this.
     */
    fun timersAreExact(): Boolean = scheduler.canBeExact()

    /**
     * The window a programme should be recorded over, paddings and the inexact-alarm head start
     * included. The apps call this rather than doing the arithmetic themselves, so both agree.
     */
    suspend fun windowFor(programmeStartMs: Long, programmeStopMs: Long): LongRange {
        val (pre, post) = settings.recordingRollMinutes()
        return RecordingSchedule.windowFor(
            programmeStartMs = programmeStartMs,
            programmeStopMs = programmeStopMs,
            preRollMinutes = pre,
            postRollMinutes = post,
            exactAlarms = scheduler.canBeExact(),
        )
    }

    fun observe(profileId: Long): Flow<List<RecordingEntity>> = recordingDao.observeForProfile(profileId)

    /** Everything currently being written, for the pill and the player's REC indicator (D13). */
    fun observeRunning(): Flow<List<RecordingEntity>> = recordingDao.observeRunning()

    /** Free/total space of the volume the recordings land on — the same volume downloads use. */
    suspend fun storageInfo(): RecordingStorageInfo = withContext(Dispatchers.IO) {
        val root = runCatching { recordingsRoot() }.getOrNull()
        val info = root?.let { storageBudget.info(it.stored) }
        RecordingStorageInfo(info?.free ?: 0, info?.total ?: 0, info?.reserve ?: 0,
            info?.quota ?: 0, info?.used ?: 0, info?.known == true, info?.removable == true)
    }

    /**
     * Would one more recording on this playlist be allowed right now? The apps ask before they offer
     * the button, so a refusal is a sentence rather than a failure (D5/D10/D11).
     */
    suspend fun canRecordOn(sourceId: Long): StreamGrant = connectionBudget(
        source = sourceDao.getById(sourceId),
        open = streams.openOn(sourceId),
        purpose = StreamPurpose.RECORDING,
        reserveOneForWatching = settings.recordingReserveConnection(),
    )

    /**
     * Anything already claiming this playlist over the same window — the clash the UI warns about at
     * scheduling time, before the user has committed to anything (D10).
     */
    suspend fun clashesWith(sourceId: Long, startMs: Long, stopMs: Long, excludeId: Long = 0): List<RecordingEntity> =
        recordingDao.overlapping(sourceId, startMs, stopMs, excludeId)

    /**
     * Schedule (or start, if its window is already open) one recording, and return the row.
     *
     * Pressing Record twice on the same programme finds the first row instead of making a second —
     * that is what the table's unique index is for — so this is safe to call from anywhere the
     * programme appears.
     */
    suspend fun schedule(recording: RecordingEntity): RecordingEntity = withContext(Dispatchers.IO) {
        val existing = recordingDao.forProgramme(
            recording.profileId,
            recording.channelId,
            recording.programmeStartMs,
        )
        // Already recording or already recorded: leave it alone. Re-scheduling a finished recording
        // from the guide would otherwise wipe the row pointing at the file on disk.
        if (existing != null && existing.status != RecordingStatus.MISSED &&
            existing.status != RecordingStatus.CANCELLED
        ) {
            return@withContext existing
        }
        val target = runCatching { recordingsRoot()
            .child(MediaFolders.TV, RecordingRules.fileName(recording)) }.getOrNull()
        val row = recording.copy(
            id = existing?.id ?: 0,
            filePath = target?.stored,
            status = if (target == null) RecordingStatus.FAILED else RecordingStatus.SCHEDULED,
            failure = if (target == null) RecordingFailure.STORAGE_UNAVAILABLE else RecordingFailure.NONE,
            bytes = 0,
            startedAt = null,
            endedAt = null,
            updatedAt = System.currentTimeMillis(),
        )
        val id = recordingDao.upsert(row)
        val saved = row.copy(id = if (id > 0) id else row.id)
        // Arm the timer first: a recording that is in the table but has no alarm is one that never
        // happens, and the kick below only helps if its window is already open.
        if (target != null) RecordingSchedule.wakeAtFor(saved, System.currentTimeMillis())?.let { scheduler.arm(saved.id, it) }
        kick()
        saved
    }

    /**
     * Record a programme that has **already aired**, from the provider's archive, starting now.
     *
     * This is the only way to record something that has already happened, and it costs almost
     * nothing: `CatchupUrl` already builds a playable archive URL for every convention the app
     * supports — Xtream's timeshift, and M3U's `append` / `shift` / `flussonic` / `xc` — so a
     * catch-up recording is an ordinary recording pointed at a different URL.
     *
     * It differs from a scheduled one in two ways, both of which the recorder derives rather than
     * being told: there is **no timer**, because the window opens now, and the source is **finite**,
     * so end-of-body means the programme is complete instead of meaning the provider dropped us.
     *
     * Returns null — and schedules nothing — when the channel has no archive, when the programme has
     * not finished airing, or when it is older than the archive goes.
     */
    suspend fun recordFromArchive(
        profileId: Long,
        channel: ChannelEntity,
        programme: EpgProgrammeEntity,
        source: SourceEntity,
        timeZone: TimeZone,
        xtream: XtreamClient,
    ): RecordingEntity? = withContext(Dispatchers.IO) { actionMutex.withLock {
        if (!channel.catchup) return@withContext null
        val now = System.currentTimeMillis()
        if (programme.stopMs > now ||
            !tv.own.owntv.core.epg.GuideHistoryPolicy.canAttemptCatchup(channel.catchup, programme.startMs, now)) return@withContext null
        val already = recordingDao.forProgramme(profileId, channel.id, programme.startMs)
        if (already != null && already.status != RecordingStatus.CANCELLED && already.status != RecordingStatus.MISSED)
            return@withContext already
        val busy = recordingDao.storageRows().any { row -> row.sourceId == source.id && row.stopMs > now &&
            (row.status == RecordingStatus.SCHEDULED || row.status == RecordingStatus.RECORDING) && RecordingSchedule.isCatchUp(row) }
        if (busy) return@withContext null
        val url = archiveUrl(channel, programme, source, timeZone, xtream) ?: return@withContext null

        if (settings.activeProfileId.first() != profileId) return@withContext null
        val window = RecordingSchedule.catchUpWindowFor(programme.startMs, programme.stopMs, now)
        schedule(
            RecordingEntity(
                profileId = profileId,
                sourceId = source.id,
                channelId = channel.id,
                channelName = channel.name,
                channelIconUrl = channel.logoUrl,
                epgChannelId = programme.epgChannelId,
                streamUrl = url,
                httpHeaders = channel.httpHeaders,
                title = programme.title,
                description = programme.description,
                // The programme's own times, untouched — this row still describes last Tuesday's
                // nine o'clock news, whatever time it is being fetched at.
                programmeStartMs = programme.startMs,
                programmeStopMs = programme.stopMs,
                startMs = window.first,
                stopMs = window.last,
            ),
        )
    } }

    // --- Series recording: "record every showing of this title on this channel" (D7) -------------

    /** This profile's standing rules, for the screens that list and cancel them. */
    fun observeRules(profileId: Long): Flow<List<RecordingRuleEntity>> = recordingDao.observeRules(profileId)

    /** The rule covering this programme on this channel, or null when there is none. */
    suspend fun ruleFor(profileId: Long, channelId: Long, title: String): RecordingRuleEntity? =
        recordingDao.findRule(profileId, channelId, RecordingRuleMatcher.fold(title))

    /**
     * Start recording every showing of [title] on [channel], and schedule the ones the guide already
     * knows about.
     *
     * Scoped to one channel on purpose: "every showing anywhere" across a twenty-thousand-channel
     * playlist is a different and much worse feature, and nobody asked for it.
     */
    suspend fun addSeriesRule(
        profileId: Long,
        channel: ChannelEntity,
        title: String,
    ): RecordingRuleEntity = withContext(Dispatchers.IO) {
        val key = RecordingRuleMatcher.fold(title)
        val existing = recordingDao.findRule(profileId, channel.id, key)
        val rule = (existing ?: RecordingRuleEntity(
            profileId = profileId,
            sourceId = channel.sourceId,
            channelId = channel.id,
            channelName = channel.name,
            epgChannelId = channel.epgChannelId,
            title = title,
            titleKey = key,
        )).copy(enabled = true)
        val id = recordingDao.upsertRule(rule)
        val saved = rule.copy(id = if (id > 0) id else rule.id)
        applyRules()
        saved
    }

    /**
     * Stop recording every showing, and cancel the showings this rule had queued up.
     *
     * Anything already recorded — or being recorded right now — is left alone. The user asked to stop
     * recording *future* showings, not to throw away last week's.
     */
    suspend fun removeSeriesRule(rule: RecordingRuleEntity) = withContext(Dispatchers.IO) {
        val pending = RecordingRuleMatcher.pendingFor(rule.id, recordingDao.scheduled())
        pending.forEach { row ->
            scheduler.cancel(row.id)
            recordingDao.updateProgress(
                id = row.id,
                status = RecordingStatus.CANCELLED,
                failure = RecordingFailure.NONE,
                bytes = 0,
                filePath = null,
                startedAt = null,
                endedAt = System.currentTimeMillis(),
                timestamp = System.currentTimeMillis(),
            )
        }
        recordingDao.deleteRule(rule)
    }

    private val guideScheduleMutex = kotlinx.coroutines.sync.Mutex()

    /** Update future live timers in place; never replace a recording row or reset its lifecycle. */
    private suspend fun refreshScheduledTimings(now: Long, guideSourceIds: Set<Long>, globalShift: Int) {
        val customByProfile = HashMap<Long, tv.own.owntv.core.customize.SectionCustomizations>()
        var moved = false
        for (snapshot in recordingDao.scheduled()) {
            if (!RecordingReconciliation.canAdjust(snapshot, now)) continue
            // Local ids may have become stale. Decline an uncertain channel re-match rather than
            // moving a timer to an unrelated channel with the same display name.
            val channel = channelDao.getById(snapshot.channelId)?.takeIf { it.sourceId == snapshot.sourceId } ?: continue
            val cust = customByProfile[snapshot.profileId] ?: customize.observe(snapshot.profileId, MediaType.LIVE)
                .first().also { customByProfile[snapshot.profileId] = it }
            val shift = tv.own.owntv.core.epg.EpgShift.minutesFor(cust, channel, globalShift)
            val epgKey = if (tv.own.owntv.core.epg.GuideInheritance.fallback(channel, cust) != null) {
                tv.own.owntv.core.epg.GuideInheritance.key(channel, cust, epgDao,
                    tv.own.owntv.core.epg.EpgShift.toStored(snapshot.programmeStartMs - RecordingProgrammeMatcher.MAX_SHIFT_MS, shift),
                    tv.own.owntv.core.epg.EpgShift.toStored(snapshot.programmeStopMs + RecordingProgrammeMatcher.MAX_SHIFT_MS + 1, shift))
            } else (cust.epgMatchResolver.epgIdFor(channel) ?: snapshot.epgChannelId ?: channel.epgChannelId)
                ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            if (epgKey == null) continue
            val changed = db.transaction {
                val current = recordingDao.getById(snapshot.id) ?: return@transaction null
                val checkedAt = System.currentTimeMillis()
                if (current != snapshot || !RecordingReconciliation.canAdjust(current, checkedAt)) return@transaction null
                val programmes = tv.own.owntv.core.epg.EpgShift.apply(
                    epgDao.programmesForChannel(
                        epgKey,
                        tv.own.owntv.core.epg.EpgShift.toStored(current.programmeStartMs - RecordingProgrammeMatcher.MAX_SHIFT_MS, shift),
                        tv.own.owntv.core.epg.EpgShift.toStored(current.programmeStartMs + RecordingProgrammeMatcher.MAX_SHIFT_MS + 1, shift),
                    ), shift,
                )
                val programme = RecordingProgrammeMatcher.find(current, programmes, epgKey, guideSourceIds)
                    ?: return@transaction null
                val replacement = RecordingReconciliation.move(current, programme, checkedAt) ?: return@transaction null
                val occupied = recordingDao.forProgramme(current.profileId, current.channelId, replacement.programmeStartMs)
                if (occupied != null && occupied.id != current.id) return@transaction null
                // @Update uses ABORT, not the REPLACE insert which could delete the colliding timer.
                recordingDao.update(replacement)
                replacement
            } ?: continue
            // Change alarms only after the DB committed. A cancelled/running row wins over an old
            // snapshot, and an alarm that already fired re-reads the new window before recording.
            scheduler.cancel(changed.id)
            if (recordingDao.getById(changed.id) == changed) {
                RecordingSchedule.wakeAtFor(changed, System.currentTimeMillis())?.let { scheduler.arm(changed.id, it) }
            }
            moved = true
        }
        if (moved) kick()
    }

    /** Reconcile existing timers after EPG refresh, then add newly announced rule showings. */
    suspend fun applyRules() = withContext(Dispatchers.IO) {
        guideScheduleMutex.withLock {
            val now = System.currentTimeMillis()
            val guideSourceIds = (sourceDao.allSourceIds() + epgSourceStore.getAll().map { it.id }).toSet()
            val globalShift = settings.epgOffsetMinutes.first()
            // Manual timers need reconciliation too, even when there are no enabled series rules.
            refreshScheduledTimings(now, guideSourceIds, globalShift)
            val rules = recordingDao.enabledRules()
            if (rules.isEmpty()) return@withLock
            for (rule in rules) {
                val channel = channelDao.getById(rule.channelId)?.takeIf { it.sourceId == rule.sourceId } ?: continue
                val cust = customize.observe(rule.profileId, MediaType.LIVE).first()
                val shift = tv.own.owntv.core.epg.EpgShift.minutesFor(cust, channel, globalShift)
                val epgKey = if (tv.own.owntv.core.epg.GuideInheritance.fallback(channel, cust) != null) {
                    tv.own.owntv.core.epg.GuideInheritance.key(channel, cust, epgDao,
                        tv.own.owntv.core.epg.EpgShift.toStored(now, shift), tv.own.owntv.core.epg.EpgShift.toStored(now + RULE_HORIZON_MS, shift))
                } else (cust.epgMatchResolver.epgIdFor(channel) ?: rule.epgChannelId ?: channel.epgChannelId)
                    ?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
                if (epgKey == null) continue
                // Registered separate EPG feeds can supply this channel's guide as well as its playlist.
                val programmes = tv.own.owntv.core.epg.EpgShift.apply(epgDao.programmesForChannel(
                    epgKey = epgKey,
                    from = tv.own.owntv.core.epg.EpgShift.toStored(now, shift),
                    to = tv.own.owntv.core.epg.EpgShift.toStored(now + RULE_HORIZON_MS, shift),
                ).filter { it.sourceId in guideSourceIds }, shift)
                val existing = recordingDao.observeForProfile(rule.profileId).first()
                val aliases = RecordingProgrammeMatcher.knownShowingAliases(existing, programmes, rule.channelId, epgKey, guideSourceIds)
                val showings = RecordingRuleMatcher.showingsToSchedule(
                    titleKey = rule.titleKey,
                    channelId = rule.channelId,
                    programmes = programmes,
                    // Cancelled/completed showings stay spoken for even after a confident EPG move.
                    existing = existing + aliases,
                    now = now,
                )
                for (programme in showings) {
                    val window = windowFor(programme.startMs, programme.stopMs)
                    schedule(
                        RecordingEntity(
                            profileId = rule.profileId,
                            sourceId = channel.sourceId,
                            channelId = channel.id,
                            channelName = channel.name,
                            channelIconUrl = channel.logoUrl,
                            epgChannelId = epgKey,
                            streamUrl = channel.streamUrl,
                            httpHeaders = channel.httpHeaders,
                            title = programme.title,
                            description = programme.description,
                            programmeStartMs = programme.startMs,
                            programmeStopMs = programme.stopMs,
                            startMs = window.first,
                            stopMs = window.last,
                            ruleId = rule.id,
                        ),
                    )
                }
            }
        }
    }


    /** Pause only a proven finite HLS archive, after its writer has committed complete segments. */
    suspend fun pauseArchive(recording: RecordingEntity): Boolean = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            val row = recordingDao.getById(recording.id) ?: return@withLock false
            if (!recordingActionMatches(recording, row) || settings.activeProfileId.first() != row.profileId ||
                !ArchiveResumePolicy.canPause(row)) return@withLock false
            try {
                val paused = engine.pauseArchive(row)
                if (paused) scheduler.cancel(row.id)
                paused
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            finally { engine.release(row.id) }
        }
    }

    /** A fresh archive URL describes the whole original programme; committed sequences are validated before reuse. */
    suspend fun resumeArchive(recording: RecordingEntity): Boolean = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            val row = recordingDao.getById(recording.id) ?: return@withLock false
            if (!recordingActionMatches(recording, row) || settings.activeProfileId.first() != row.profileId ||
                !ArchiveResumePolicy.canResume(row)) return@withLock false
            repeat(50) { if (engine.hasPlaybackReaders(row.id)) kotlinx.coroutines.delay(100) }
            if (engine.hasPlaybackReaders(row.id) || !engine.suppressIfIdle(row.id)) return@withLock false
            try {
                val source = sourceDao.getById(row.sourceId) ?: return@withLock false
                val candidates = channelDao.recordingCandidates(row.sourceId, row.channelName)
                    .filter { row.epgChannelId == null || it.epgChannelId == row.epgChannelId }
                val channel = candidates.singleOrNull() ?: return@withLock false
                val now = System.currentTimeMillis()
                if (channel.drmConfig != null || !channel.catchup ||
                    !tv.own.owntv.core.epg.GuideHistoryPolicy.canAttemptCatchup(channel.catchup, row.programmeStartMs, now))
                    return@withLock false
                if (recordingDao.dueAt(now).any { it.id != row.id && it.sourceId == row.sourceId && RecordingSchedule.isCatchUp(it) })
                    return@withLock false
                if (canRecordOn(row.sourceId) is StreamGrant.Refused || !engine.retainedArchiveAvailable(row)) return@withLock false
                val programme = EpgProgrammeEntity(sourceId = source.id, epgChannelId = row.epgChannelId.orEmpty(),
                    title = row.title, startMs = row.programmeStartMs, stopMs = row.programmeStopMs)
                val url = archiveUrl(channel, programme, source, settings.resolveCatchupTimeZone(), archiveXtream)
                    ?: return@withLock false
                val latest = recordingDao.getById(row.id) ?: return@withLock false
                if (latest != row || settings.activeProfileId.first() != row.profileId) return@withLock false
                val window = RecordingSchedule.catchUpWindowFor(row.programmeStartMs, row.programmeStopMs, now)
                recordingDao.update(row.copy(channelId = channel.id, streamUrl = url, httpHeaders = channel.httpHeaders,
                    status = RecordingStatus.SCHEDULED, startMs = window.first, stopMs = window.last,
                    failure = RecordingFailure.NONE, captureFailure = null, finalizationFailure = null,
                    endedAt = null, updatedAt = now))
                scheduler.arm(row.id, now)
                true
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            finally { engine.release(row.id); kick() }
        }
    }

    /** Recover into a separate row and file; the original capture remains untouched. */
    suspend fun retryRecording(recording: RecordingEntity): Boolean = withContext(Dispatchers.IO) {
        recoveryMutex.lock()
        try {
            val original = recordingDao.getById(recording.id) ?: return@withContext false
            val originId = original.recoveryOfId ?: original.id
            if (recordingDao.activeRecoveryFor(originId) != null) return@withContext true
            val source = sourceDao.getById(original.sourceId) ?: return@withContext false
            val channel = channelDao.getById(original.channelId)?.takeIf { it.sourceId == original.sourceId }
                ?: channelDao.recordingCandidates(original.sourceId, original.channelName)
                    .filter { original.epgChannelId == null || it.epgChannelId == original.epgChannelId }
                    .singleOrNull() ?: return@withContext false
            val now = System.currentTimeMillis()
            val mode = RecordingRecoveryPolicy.mode(original, now, channel.catchup, channel.catchupDays)
            if (mode == RecordingRecoveryPolicy.Mode.NONE || channel.drmConfig != null) return@withContext false
            val programme = EpgProgrammeEntity(
                sourceId = source.id, epgChannelId = channel.epgChannelId.orEmpty(), title = original.title,
                startMs = original.programmeStartMs, stopMs = original.programmeStopMs,
            )
            val archive = mode == RecordingRecoveryPolicy.Mode.ARCHIVE
            val url = if (archive) archiveUrl(channel, programme, source, settings.resolveCatchupTimeZone(), archiveXtream)
                ?: return@withContext false else channel.streamUrl
            val runWindow = if (archive) RecordingSchedule.catchUpWindowFor(programme.startMs, programme.stopMs, now)
                else now..original.stopMs.coerceAtLeast(original.programmeStopMs)
            val attempt = recordingDao.nextRecoveryAttempt(original.profileId, channel.id, original.programmeStartMs)
            val name = RecordingRules.fileName(channel.name, original.title, now)
            val target = recordingsRoot().child(MediaFolders.TV,
                name.substringBeforeLast('.') + "-recovery-$originId-$attempt-$now." + name.substringAfterLast('.')) ?: return@withContext false
            if (target.length() > 0L) return@withContext false
            val row = original.copy(
                id = 0, channelId = channel.id, channelName = channel.name, channelIconUrl = channel.logoUrl,
                epgChannelId = channel.epgChannelId, streamUrl = url, httpHeaders = channel.httpHeaders,
                startMs = runWindow.first, stopMs = runWindow.last, status = RecordingStatus.SCHEDULED,
                failure = RecordingFailure.NONE, captureFailure = null, finalizationFailure = null, filePath = target.stored, bytes = 0, startedAt = null, endedAt = null,
                ruleId = null, archivePaused = false, capturedDurationMs = 0, missingDurationMs = 0, gapCount = 0, hlsCheckpoint = null,
                recoveryOfId = originId, recoveryAttempt = attempt,
                createdAt = now, updatedAt = now,
            )
            val id = recordingDao.insertRecovery(row)
            if (id <= 0) return@withContext false
            scheduler.arm(id, now)
            kick()
            true
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (e: Exception) {
            android.util.Log.w("RecordingRecovery", "recovery unavailable id=${recording.id} cause=${e.javaClass.simpleName}")
            false
        } finally {
            recoveryMutex.unlock()
        }
    }

    private suspend fun archiveUrl(
        channel: ChannelEntity, displayProgramme: EpgProgrammeEntity, source: SourceEntity, timeZone: TimeZone, xtream: XtreamClient,
    ): String? {
        val programme = settings.correctCatchupProgramme(displayProgramme)
        return if (archiveResolver.needsResolve(source)) {
            channel.remoteId?.let { archiveResolver.resolveCatchup(source, it, programme.startMs, programme.stopMs) }
        } else CatchupUrl.forSource(channel, programme, source, timeZone, xtream)
    }

    fun isParts(recording: RecordingEntity): Boolean = MediaTarget.of(context, recording.filePath)?.let(RecordingParts::isIndex) == true

    suspend fun openParts(recording: RecordingEntity): RecordingPlayback? = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            val row = current(recording) ?: return@withLock null
            if (settings.activeProfileId.first() != row.profileId || !RecordingIntegrity.canPlay(row)) return@withLock null
            runCatching { engine.openParts(row) }.getOrNull()
        }
    }

    suspend fun openInProgress(recording: RecordingEntity): RecordingPlayback? = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            val row = current(recording) ?: return@withLock null
            if (settings.activeProfileId.first() != row.profileId || row.status != RecordingStatus.RECORDING ||
                row.archivePaused || row.capturedDurationMs <= 0) return@withLock null
            engine.openInProgress(row.id)
        }
    }

    /** Re-read for playback/export; stale UI metadata must not choose an old finalized path. */
    suspend fun current(recording: RecordingEntity): RecordingEntity? = recordingDao.getById(recording.id)
        ?.takeIf { recordingActionMatches(recording, it) }

    /** Completion means the selected writer and its stream claim have actually been released. */
    suspend fun stopAndAwait(recording: RecordingEntity): Boolean = withContext(Dispatchers.IO) {
        actionMutex.withLock {
            val current = recordingDao.getById(recording.id) ?: return@withLock false
            if (!recordingActionMatches(recording, current, setOf(RecordingStatus.RECORDING))) return@withLock false
            engine.stop(current.id)
            try {
                val row = recordingDao.getById(current.id) ?: return@withLock false
                val bytes = maxOf(row.bytes, MediaTarget.of(context, row.filePath)?.length() ?: 0L)
                val (status, failure) = RecordingRules.outcomeOf(bytes, row.failure,
                    incomplete = row.status == RecordingStatus.PARTIAL || row.gapCount > 0 ||
                        System.currentTimeMillis() < row.programmeStopMs)
                recordingDao.updateProgress(row.id, status, failure, bytes, row.filePath,
                    row.startedAt, System.currentTimeMillis(), System.currentTimeMillis())
                true
            } finally { engine.release(current.id) }
        }
    }

    fun stop(recording: RecordingEntity) { scope.launch { stopAndAwait(recording) } }

    /** A stale scheduled menu must never cancel a capture which has already started. */
    fun cancel(recording: RecordingEntity) {
        scope.launch {
            actionMutex.withLock {
                val row = recordingDao.getById(recording.id) ?: return@withLock
                if (!recordingActionMatches(recording, row, setOf(RecordingStatus.SCHEDULED))) return@withLock
                if (!engine.suppressIfIdle(row.id)) return@withLock
                try {
                    // Re-read after admission has been excluded.
                    val latest = recordingDao.getById(row.id) ?: return@withLock
                    if (latest.status != RecordingStatus.SCHEDULED) return@withLock
                    scheduler.cancel(row.id)
                    if (latest.archivePaused) {
                        recordingDao.update(latest.copy(status = RecordingStatus.PARTIAL, endedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
                        return@withLock
                    }
                    recordingDao.updateProgress(row.id, RecordingStatus.CANCELLED, RecordingFailure.NONE,
                        latest.bytes, latest.filePath, latest.startedAt, System.currentTimeMillis(), System.currentTimeMillis())
                } finally { engine.release(row.id) }
            }
        }
    }

    /** Deletion is scoped to the selected identity, after finalization, never a saved menu path. */
    fun delete(recording: RecordingEntity) {
        scope.launch {
            actionMutex.withLock {
                val row = recordingDao.getById(recording.id) ?: return@withLock
                if (!recordingActionMatches(recording, row)) return@withLock
                repeat(50) { if (engine.hasPlaybackReaders(row.id)) kotlinx.coroutines.delay(100) }
                if (engine.hasPlaybackReaders(row.id)) return@withLock
                scheduler.cancel(row.id)
                engine.stop(row.id)
                try {
                    if (!engine.discardRetainedCapture(row.id)) return@withLock
                    val finished = recordingDao.getById(row.id) ?: return@withLock
                    val file = MediaTarget.of(context, finished.filePath)
                    if (file != null) {
                        if (!runCatching { RecordingParts.delete(context, file, finished.id) }.getOrDefault(false)) return@withLock
                        if (!RecordingParts.isIndex(file) && file.exists() && !file.delete()) return@withLock
                    }
                    recordingDao.delete(finished)
                } finally { engine.release(row.id) }
            }
        }
    }

    /**
     * Point a finished recording at a file the user has moved it to, and let go of the old one.
     *
     * This is Export's second half: the bytes are already at [movedTo], and until the row agrees the
     * user has two copies and the app is tracking the wrong one.
     *
     * Uses `updateProgress` and never `upsert`, for the same reason [RecordingEngine] does: the
     * table's unique index on `(profileId, channelId, programmeStartMs)` makes a REPLACE delete this
     * row and insert a new one with a different id, orphaning anything still holding the old one.
     * Everything but the location is written back exactly as it was.
     */
    suspend fun relocate(recording: RecordingEntity, movedTo: String): Boolean =
        withContext(Dispatchers.IO) {
            val previous = MediaTarget.of(context, recording.filePath)
            if (previous != null && RecordingParts.isIndex(previous)) return@withContext false
            recordingDao.updateProgress(
                id = recording.id,
                status = recording.status,
                failure = recording.failure,
                bytes = recording.bytes,
                filePath = movedTo,
                startedAt = recording.startedAt,
                endedAt = recording.endedAt,
                timestamp = System.currentTimeMillis(),
            )
            previous?.delete()
            true
        }

    private suspend fun recordingsRoot(): MediaRoot =
        MediaRoot.of(context, settings.downloadRoot.first()).also { it.ensureFolders() }

    private fun kick() {
        engine.markQueued()
        scope.launch { RecordingWorker.kick(context) }
    }
}

/**
 * How far ahead a series rule looks. Two weeks is more guide than most providers publish, and a rule
 * is re-applied on every refresh — so looking further would only schedule rows for programmes whose
 * times are still going to change.
 */
private const val RULE_HORIZON_MS = 14L * 24 * 60 * 60 * 1000

/** Free/total space on the volume recordings are written to, and the floor they stop at (D8). */
data class RecordingStorageInfo(val freeBytes: Long, val totalBytes: Long, val reserveBytes: Long,
    val quotaBytes: Long = 0, val mediaBytes: Long = 0, val known: Boolean = true, val removable: Boolean = false) {
    /** What is actually available to a recording: everything above the reserve. */
    val writableBytes: Long get() = if (!known) 0 else minOf((freeBytes - reserveBytes).coerceAtLeast(0L),
        if (quotaBytes > 0) (quotaBytes - mediaBytes).coerceAtLeast(0L) else Long.MAX_VALUE)
}

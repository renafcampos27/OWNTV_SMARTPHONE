package tv.own.owntv.mobile.ui.screens.live

import android.content.Context
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import tv.own.owntv.mobile.ui.screens.settings.SimpleModePreferences
import tv.own.owntv.mobile.ui.screens.settings.RecoveryTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.FavoriteDao
import tv.own.owntv.core.database.dao.ChannelDao
import tv.own.owntv.core.database.dao.HistoryDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.FavoriteEntity
import tv.own.owntv.core.database.entity.WatchHistoryEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.CatchupContinue
import tv.own.owntv.core.live.EpgNowNext
import tv.own.owntv.core.live.LiveArchiveUrls
import tv.own.owntv.core.live.LiveEpgReader
import tv.own.owntv.core.live.LiveTimeshift
import tv.own.owntv.core.customize.railCategories
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.player.AudioOnlyStore
import tv.own.owntv.core.player.enginePinKey
import tv.own.owntv.core.repository.ActiveProfileSources
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.settings.SourceOverrides
import tv.own.owntv.core.stalker.StreamUrlResolver
import tv.own.owntv.mobile.cast.CastController
import tv.own.owntv.mobile.cast.CastHandoff
import tv.own.owntv.mobile.cast.CastRequest
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.playback.DataSaverGate
import tv.own.owntv.mobile.playback.PlaybackService
import kotlinx.coroutines.flow.map
import tv.own.owntv.player.LiveProgramme
import tv.own.owntv.player.EnginePair
import tv.own.owntv.player.LiveTuneController
import tv.own.owntv.player.LivePreviewEngine
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.player.MpvPlaybackEngine
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaybackSession

/**
 * What is playing, and everything a screen needs to ask about it.
 *
 * It outlives every screen deliberately. The channel screen, the fullscreen player and the docked
 * mini player are three views of **one** stream, and a user who leaves the channel screen while a
 * match is on has not asked for it to stop — so the tuning state cannot belong to a view model that
 * dies with its route. Only [stop] ends playback.
 *
 * Live rewind is core's [LiveTimeshift], the same class the television uses: this app supplies the
 * archive URL and the play call, exactly as `LiveViewModel` does there.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTuner(
    private val context: Context,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val customCategoryDao: tv.own.owntv.core.database.dao.CustomCategoryDao,
    private val historyDao: HistoryDao,
    private val profileDao: ProfileDao,
    private val favoriteDao: FavoriteDao,
    private val userDataWriter: tv.own.owntv.core.backup.UserDataWriter,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val customize: CustomizationStore,
    private val streamUrlResolver: StreamUrlResolver,
    private val epgReader: LiveEpgReader,
    private val archiveUrls: LiveArchiveUrls,
    private val session: PlaybackSession,
    private val dataSaver: DataSaverGate,
    private val audioOnlyStore: AudioOnlyStore,
    private val cast: CastController,
    private val recordings: tv.own.owntv.core.recording.RecordingManager,
    /**
     * The second live engine (L2). Live played on mpv and nothing else here, which is why the HUD's
     * engine button was hidden for live: there was no engine to swap to. This is the same
     * [LivePreviewEngine] the television runs live on, and the same one Multiview gives each tile.
     */
    private val exo: LivePreviewEngine,
    /** Per-channel "compatibility mode" pins — the same store, and the same meaning, as the television's. */
    private val forceMpvStore: tv.own.owntv.core.player.ForceMpvStore,
    val player: OwnTVPlayer,
    /** Lets core's background catalogue drain know a playlist is in use. */
    private val watchSession: tv.own.owntv.core.live.WatchSession,
) : CastHandoff {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)


    // --- "Record what I'm watching" (Plan D, D3 mode b) -----------------------------------------


    /**
     * Start or stop recording the channel on screen, from its start time of *now*.
     *
     * **This fetches the channel itself rather than copying the open stream, and that is deliberate.**
     * The original design tapped mpv's `stream-record`, which costs no second connection — but that
     * copies bytes as they pass through mpv's stream layer, and an HLS channel never puts them there:
     * FFmpeg's `hls` demuxer opens each segment on its own. mpv accepted the instruction and wrote
     * nothing, every time, on every HLS channel — which on a normal IPTV playlist is all of them.
     *
     * So it goes through the same engine as a scheduled recording, and inherits its behaviour: it
     * costs one of the playlist's connections, it **keeps running** when the channel is changed or
     * the player is left, and Downloads → Live TV can stop it. The connection is why
     * `canRecordOn` is asked first — a refusal is a sentence, not a failed recording.
     */
    fun togglePlayerRecording() {
        scope.launch {
            val open = playerRecording.value
            if (open != null) {
                recordings.stop(open)
                return@launch
            }
            val ch = channel.value ?: return@launch
            val pid = settings.activeProfileId.first().takeIf { it >= 0 } ?: return@launch
            startRecordingNow(ch, pid, _nowNext.value?.now)
        }
    }

    /**
     * Record [channel] from now, whether or not it is the one on screen — the long-press entry in the
     * channel list. A channel the provider publishes no guide for never appears in the guide, so this
     * is the only way to record it at all.
     */
    fun recordNow(ch: tv.own.owntv.core.database.entity.ChannelEntity) {
        scope.launch {
            val pid = settings.activeProfileId.first().takeIf { it >= 0 } ?: return@launch
            startRecordingNow(ch, pid, if (ch.id == channel.value?.id) _nowNext.value?.now else null)
        }
    }

    private suspend fun startRecordingNow(
        ch: tv.own.owntv.core.database.entity.ChannelEntity,
        profileId: Long,
        programme: tv.own.owntv.core.parser.XtEpgEntry?,
    ): tv.own.owntv.core.database.entity.RecordingEntity? {
        if (recordings.canRecordOn(ch.sourceId) !is tv.own.owntv.core.live.StreamGrant.Allowed) return null
        val startMs = System.currentTimeMillis()
        // Start now: the programme is already under way and a live edge cannot be rewound, so the
        // pre-roll has nothing to reach back to. The end is the programme's own, padding included.
        val stopMs = programme
            ?.let { recordings.windowFor(it.startMs, it.stopMs).last }
            ?.takeIf { it > startMs }
            ?: (startMs + tv.own.owntv.core.recording.RecordingSchedule.NO_GUIDE_RUNTIME_MINUTES * 60_000L)
        return recordings.schedule(
            tv.own.owntv.core.database.entity.RecordingEntity(
                profileId = profileId,
                sourceId = ch.sourceId,
                channelId = ch.id,
                channelName = ch.name,
                channelIconUrl = ch.logoUrl,
                epgChannelId = ch.epgChannelId,
                streamUrl = ch.streamUrl,
                httpHeaders = ch.httpHeaders,
                title = programme?.title ?: ch.name,
                description = programme?.description,
                programmeStartMs = programme?.startMs ?: startMs,
                programmeStopMs = programme?.stopMs ?: stopMs,
                startMs = startMs,
                stopMs = stopMs,
            ),
        )
    }

    /**
     * The engine as the rest of the system sees it. Published to [session] whenever a stream starts,
     * withdrawn in [stop] — this class is the only thing that knows whether anything is playing at
     * all, so it is the only thing that can answer a call, a headphone unplug or a lockscreen button
     * correctly.
     */
    private val engine by lazy { MpvPlaybackEngine(player) }

    // --- Which engine is playing live (L2) -------------------------------------------------------

    /**
     * Routing, the fallback ladder, the handovers and the give-up alarm — core's, the same code the
     * television runs, so a fix to either reaches both. What stays here is the phone's own: which
     * channel is on screen, casting, sound-only, history and the media session.
     */
    private var recoveryOpenedId: Long? = null
    private var guideLoadJob: Job? = null

    private val live = LiveTuneController(
        scope = scope,
        engines = EnginePair(exo, player),
        host = LiveTuneController.CoreHost(
            context = context,
            settings = settings,
            sourceDao = sourceDao,
            resolver = streamUrlResolver,
            forceMpvStore = forceMpvStore,
            engineStarted = { publishToSystem() },
            opened = { recoveryOpenedId = it.id },
        ),
    )

    /**
     * Whether live is on ExoPlayer right now — false means mpv, and false is also every VOD case.
     *
     * The HUD reads this to decide which surface to show and which way its engine button flips. It is
     * the *actual* engine rather than the pin, because an automatic handover to mpv leaves a channel
     * running on mpv while still unpinned, and a button keyed off the pin would then do nothing.
     */
    val liveOnExo: StateFlow<Boolean> = live.liveOnExo

    /** The live ExoPlayer engine, for the surface the player screen has to give it. */
    val exoEngine: LivePreviewEngine get() = exo

    /**
     * Whichever engine the HUD should be reading and driving.
     *
     * Both are a [PlaybackEngine], so nothing above this has to know which one it has — the same
     * arrangement the television uses, and the reason the phone's HUD needed no second set of
     * controls.
     */
    val activeEngine: StateFlow<PlaybackEngine> = liveOnExo
        .map { onExo -> if (onExo) exo else engine }
        .stateIn(scope, SharingStarted.Eagerly, engine)

    /**
     * The same answer as [activeEngine], read straight from the flag instead of from the flow
     * derived off it.
     *
     * **Not the same thing as `activeEngine.value`, and the difference matters.** `stateIn` republishes
     * on its own coroutine, so between the engine flag changing and that coroutine running, the flow
     * still holds the engine that was playing a moment ago. Everything in this class acts immediately
     * after starting an engine — the sound-only default is applied on the very next line — and would
     * otherwise be talking to the one just stopped. The flow stays, because a composable has to be
     * able to *observe* the change; a caller that only needs the answer now uses this.
     */
    val currentEngine: PlaybackEngine get() = if (liveOnExo.value) exo else engine

    /**
     * Whether anything is playing at all, asked of whichever engine would be holding it.
     *
     * `player.hasActiveStream` answers for mpv alone, and live opens on ExoPlayer by default — so
     * every lifecycle decision built on it was told "nothing is playing" for the whole of a live
     * channel: the screen was allowed to sleep, Picture-in-Picture never opened, and the
     * background-playback rules never ran. mpv still answers for a film, a download, a recording and
     * a channel pinned to compatibility mode, because those genuinely are its streams.
     */
    val hasStream: Boolean
        get() = if (liveOnExo.value) exo.currentUrl != null else player.hasActiveStream

    /**
     * The shape of the picture, from whichever engine is drawing it, or null before one is known.
     *
     * Only the Picture-in-Picture window needs it out here — a 2.35:1 film given the fixed 16:9 the
     * window used to ask for is a small picture with a black band above and below it, in a window
     * that is already tiny. [tv.own.owntv.mobile.ui.player.VideoStage] resolves the same two engines
     * for the same reason.
     */
    val videoAspect: Float?
        get() = if (liveOnExo.value) exo.videoAspect.value else player.videoAspect.value

    /**
     * Hand the stream to the system: the session takes the lockscreen and the audio focus, the
     * foreground service keeps the process alive once the app leaves the screen. Both are idempotent,
     * so every `play()` call can go through here.
     */
    private fun publishToSystem() {
        // Whichever engine actually holds the stream: a session published for the idle one would
        // answer the lockscreen and the headphone button for something that is not playing.
        session.attach(if (liveOnExo.value) exo else engine)
        PlaybackService.start(context)
    }

    private val ctx: StateFlow<ActiveProfileSources> = activeProfileSources(settings, sourceDao)
        .stateIn(scope, SharingStarted.Eagerly, ActiveProfileSources(-1L, emptyList()))

    private val custom: StateFlow<SectionCustomizations> = ctx
        .flatMapLatest { c ->
            if (c.profileId < 0) flowOf(SectionCustomizations())
            else customize.observe(c.profileId, MediaType.LIVE)
        }
        .stateIn(scope, SharingStarted.Eagerly, SectionCustomizations())

    private val _channel = MutableStateFlow<ChannelEntity?>(null)

    /** The channel on screen, with the user's own name for it. */
    val channel: StateFlow<ChannelEntity?> = _channel

    /** N2 — the channel watched before the one on screen; null hides the player's "previous channel". */
    private val previousSelection = MutableStateFlow<ChannelEntity?>(null)
    val previousChannel: StateFlow<ChannelEntity?> = combine(previousSelection, ctx) { p, c ->
        p?.takeIf { it.sourceId in c.sourceIds }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Go back to [previousChannel] — the player's button, and "previous" from a headset or the media
     * notification. Only from a playlist this profile has active; [tune] re-reads it by id (a sync may
     * have removed it) and [open] applies the adult filter. A deliberate pick, so it opens at once.
     */
    fun tunePrevious() {
        val previous = previousSelection.value ?: return
        if (previous.sourceId !in ctx.value.sourceIds) return
        tune(previous.id)
    }


    init {
        // Tell core which playlist is on screen, so its background catalogue drain steps aside.
        //
        // Driven from the tuner, not from PlayerScreen: on a phone a channel starts playing from the
        // Live list and the full player composable is not mounted yet, so a hook there never fired —
        // measured on a single-connection portal, where the drain held the only stream and the
        // picture never arrived. The tuner owns playback whichever screen is showing.
        scope.launch {
            var held: Long? = null
            channel.collect { ch ->
                val next = ch?.sourceId
                if (next != held) {
                    held?.let { watchSession.close(it) }
                    next?.let { watchSession.open(it) }
                    held = next
                }
            }
        }
    }


    /**
     * The recording running on the channel on screen, or null.
     *
     * **Read from the table rather than held here.** It used to be a field set by the button, which
     * meant the player only knew about a recording *it* had started: one begun from the channel
     * list's long-press left the button saying "Record" while the channel was already recording, and
     * pressing it again would have started a second one. A recording no longer belongs to the
     * playing stream — it outlives it — so the honest question is "is this channel being recorded?",
     * and only the table can answer that.
     */
    val playerRecording: StateFlow<tv.own.owntv.core.database.entity.RecordingEntity?> =
        settings.activeProfileId
            .flatMapLatest { pid ->
                if (pid < 0) flowOf(emptyList()) else recordings.observe(pid)
            }
            .combine(channel) { rows, ch ->
                rows.firstOrNull {
                    it.channelId == ch?.id &&
                        it.status == tv.own.owntv.core.model.RecordingStatus.RECORDING
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    private val _nowNext = MutableStateFlow<EpgNowNext?>(null)
    val nowNext: StateFlow<EpgNowNext?> = _nowNext

    private val _replaying = MutableStateFlow(false)

    /**
     * True only while a *chosen* archive programme is playing — the one thing on this tuner that has
     * an end and therefore a seek bar.
     *
     * The player cannot be asked this. A live stream's duration is whatever the provider's rolling
     * window happens to report, which for plenty of them is a plausible-looking twenty-five hours, so
     * deciding live-ness from the duration classed real channels as recordings and hid the entire
     * live panel. This tuner is the thing that knows: it started the stream, and it knew which kind
     * it was asking for.
     *
     * A rewind into the archive from the live edge is deliberately **not** a replay — that is still
     * the channel, just behind, and it keeps the live bar and the way back to now.
     */
    val replaying: StateFlow<Boolean> = _replaying

    private val _timelineProgrammes = MutableStateFlow<List<LiveProgramme>>(emptyList())

    /**
     * The playing channel's guide window, for the player's live timeline: these become the programme
     * boundary ticks on the bar and the name the scrub bubble reads out. Loaded once per channel —
     * scrubbing must never re-query the guide, the whole window is already here.
     */
    val timelineProgrammes: StateFlow<List<LiveProgramme>> = _timelineProgrammes

    /** Whether the channel playing is a favourite — the floating window's menu, which has no list
     *  row behind it to ask. */
    val isFavorite: StateFlow<Boolean> = _channel
        .flatMapLatest { channel ->
            val pid = ctx.value.profileId
            if (channel == null || pid < 0) flowOf(false)
            else favoriteDao.isFavorite(pid, MediaType.LIVE, channel.id)
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleFavorite() {
        val channel = _channel.value ?: return
        val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return
        scope.launch {
            if (isFavorite.value) userDataWriter.removeFavorite(pid, MediaType.LIVE, channel.id)
            else favoriteDao.add(FavoriteEntity(profileId = pid, mediaType = MediaType.LIVE, itemId = channel.id))
        }
    }

    private val _siblings = MutableStateFlow<List<ChannelEntity>>(emptyList())

    /** The other channels of the same folder — the Channels tab, and the swipe-up overlay. */
    val siblings: StateFlow<List<ChannelEntity>> = _siblings

    private val timeshift = LiveTimeshift(
        scope = scope,
        playback = object : LiveTimeshift.Playback {
            override val positionMs: Long get() = player.position.value
            override val hasError: Boolean get() = player.error.value != null
            override val hasActiveStream: Boolean get() = player.hasActiveStream
        },
        loadArchive = ::loadArchiveStream,
        onLiveEdge = { goToLive() },
    )

    // Local capture/timeshift is deferred; rewind uses the provider's archive.
    fun timeshiftGaps(): List<LongRange> = emptyList()
    val hasLocalCopy: StateFlow<Boolean> = MutableStateFlow(false)

    /** Seconds behind the live edge; null at the edge — what the red bar and the pill read. */
    val offsetSec: StateFlow<Int?> = timeshift.offsetSec

    /** The wall-clock instant actually on screen while an archive plays. */
    val watchingWallMs: StateFlow<Long?> = timeshift.watchingWallMs

    private var loadedId: Long? = null
    // Navigation follows the selected row even when recovery plays another version.
    private var stepSelectionId: Long? = null

    /** The programme a replay is showing, so ending a cast can start the same one again rather than
     *  dropping the user at the live edge. Null whenever a replay is not what is playing. */
    private var lastCatchup: EpgProgrammeEntity? = null

    init {
        scope.launch { player.archiveEnded.collect { continueAfterCatchup() } }
    }

    /**
     * A replayed programme played to its end with "Auto-play next" on (the player only raises
     * `archiveEnded` then). Carry on down the guide instead of leaving a black screen — the
     * television's rule, from core's [CatchupContinue]: the next programme if it has finished airing,
     * the live stream if it is on air now, otherwise stop.
     *
     * Guarded on [replaying]: a rewind into the archive also ends on `archiveEnded`, and that one is
     * the timeshift's to handle.
     */
    private fun continueAfterCatchup() {
        if (!_replaying.value) return
        val ch = _channel.value ?: return
        val ended = lastCatchup ?: return
        scope.launch {
            val next = epgReader.programmeAfter(
                ch, ended.stopMs, custom.value, settings.epgOffsetMinutes.first(), ctx.value.sourceIds,
            )
            when (CatchupContinue.decide(next?.startMs, next?.stopMs, System.currentTimeMillis())) {
                is CatchupContinue.Next.Programme -> next?.let { playCatchup(it, ch) }
                CatchupContinue.Next.Live -> live.launch { open(ch) }
                CatchupContinue.Next.Stop -> Unit
            }
        }
    }

    /** Open [channelId]: read the row, start it, and fill the guide and the channel list around it. */
    fun tune(channelId: Long, explicitVersion: Boolean = false) {
        // `hasStream`, not mpv's own: on live-on-ExoPlayer mpv always answers "nothing here", so
        // re-opening the channel you are already watching tore the stream down and rebuilt it —
        // a reconnection and a second of black for a screen you had just come back to.
        if (loadedId == channelId && hasStream && currentEngine.error.value == null) return
        loadedId = channelId
        stepSelectionId = null
        // One tune in flight: a newer pick, or a catch-up, cancels this one wherever it has got to.
        live.launch {
            val channel = withContext(Dispatchers.IO) { channelDao.getById(channelId) } ?: return@launch
            openWithRecovery(channel, explicitVersion)
            _channel.value?.let { loadSiblings(it) }
        }
    }

    /** Switch to another channel without leaving the screen — the Channels tab and the overlay. */
    /**
     * Make [channel] the current one **without playing it**, for a tap that is about to open
     * Multiview instead of the player.
     *
     * [switchTo] starts a stream, and the grid opens by stopping it again a fraction of a second
     * later — a race the stop lost: the engine was still loading when it was told to stop, carried
     * on, and played underneath the grid. That was the phone's doubled sound, named in a log as an
     * engine the pool had never built. Nothing should be started that is about to be stopped.
     */
    fun selectWithoutPlaying(channel: ChannelEntity) {
        loadedId = channel.id
        _channel.value = custom.value.itemNames[CustomizeKeys.channel(channel)]
            ?.let { channel.copy(name = it) } ?: channel
    }

    fun switchTo(channel: ChannelEntity) {
        if (channel.id == loadedId && hasStream && currentEngine.error.value == null) return
        loadedId = channel.id
        live.launch { channelDao.getById(channel.id)?.let { openWithRecovery(it) } }
    }

    /**
     * The next (+1) or previous (−1) channel of the same folder, wrapping at both ends — Channel +/−
     * from the Picture-in-Picture window, where there is no room for a list.
     *
     * The stream opens once the user stops pressing, [ZAP_TUNE_DELAY_MS] later, and the next press
     * cancels it — the television's rule. Every press used to open a stream, and a one-session Xtream
     * panel answers a burst of opens by locking the account for two minutes (HTTP 458).
     */
    fun step(delta: Int) {
        val list = _siblings.value
        if (list.size < 2) return
        val index = list.indexOfFirst { it.id == loadedId }.takeIf { it >= 0 }
            ?: list.indexOfFirst { it.id == stepSelectionId }
        if (index < 0) return
        val target = list[(index + delta).mod(list.size)]
        if (target.id == loadedId) return
        loadedId = target.id
        stepSelectionId = target.id
        live.launch {
            kotlinx.coroutines.delay(ZAP_TUNE_DELAY_MS)
            channelDao.getById(target.id)?.let { openWithRecovery(it) }
        }
    }

    /** All variants share the controller's cancellable tune; provider backoff pauses the budget. */
    private suspend fun openWithRecovery(original: ChannelEntity, explicitVersion: Boolean = false) {
        val profile = ctx.first { it.profileId >= 0 }
        if (original.sourceId !in profile.sourceIds) return
        val policy = customize.observe(profile.profileId, MediaType.LIVE).first()
        if (!dataSaver.allowsStreaming() || CustomizeKeys.channel(original) in policy.hiddenItems ||
            !AdultCategoryClassifier.allows(profile.profileId, original.categoryId, profileDao, categoryDao) ||
            original.categoryId?.let { categoryDao.getById(it) }?.let { CustomizeKeys.category(it) in policy.hiddenCategories } == true) {
            loadedId = _channel.value?.id
            return
        }
        suspend fun variants(): List<ChannelEntity> {
            val group = ChannelVersionPolicy.groupKey(original, policy)
            val automatic = channelDao.searchList(ChannelAlternatives.searchTerm(group.substringAfter(':')), listOf(original.sourceId), 2_000)
            val manual = policy.channelVersionGroups.filterValues { it == group }.keys.mapNotNull { key ->
                if (!key.startsWith("${original.sourceId}:")) return@mapNotNull null
                val tail = CustomizeKeys.tailOf(key)
                channelDao.findByRemote(original.sourceId, tail) ?: channelDao.findByName(original.sourceId, tail)
            }
            val grouped = (automatic + manual + original).distinctBy { it.id }
                .filter { ChannelVersionPolicy.groupKey(it, policy) == group && CustomizeKeys.channel(it) !in policy.hiddenItems }
            return ChannelVersionPolicy.ordered(grouped, policy).filter {
                AdultCategoryClassifier.allows(profile.profileId, it.categoryId, profileDao, categoryDao) &&
                    it.categoryId?.let { id -> categoryDao.getById(id) }?.let { category -> CustomizeKeys.category(category) !in policy.hiddenCategories } != false
            }
        }
        val channel = if (policy.prioritizeChannelVersions && !explicitVersion) variants().firstOrNull() ?: return else original
        val options = SimpleModePreferences.observe(context).first()
        if (!options.channelRecovery || casting()) {
            open(channel)
            return
        }
        val opened = tryChannelAlternatives(
            initial = channel,
            timeoutMs = RecoveryTimeout.milliseconds(options.recoveryTimeoutSeconds),
            alternatives = { variants().filter { it.id != channel.id }.distinctBy { it.streamUrl } },
            attempt = { candidate ->
                currentCoroutineContext().ensureActive()
                if (ctx.value != profile) throw CancellationException()
                recoveryOpenedId = null
                open(candidate)
                if (_channel.value?.id != candidate.id) return@tryChannelAlternatives false
                while (recoveryOpenedId != candidate.id) {
                    currentCoroutineContext().ensureActive()
                    if (ctx.value != profile) throw CancellationException()
                    if (currentEngine.error.value != null) return@tryChannelAlternatives false
                    kotlinx.coroutines.delay(50)
                }
                true
            },
            failed = { live.cancelOpening(); exo.stop(); player.stop() },
            switching = { loadedId = it.id },
            isBackingOff = { exo.providerBackOff.value != null },
            backoffRemainingMs = { (exo.providerBackOff.value?.secondsLeft ?: 0) * 1000L },
            shouldSkipAlternative = { tv.own.owntv.player.LiveStreamQuirks.isHostBackingOff(it.streamUrl) },
            startupProgress = { if (liveOnExo.value) exo.startupProgress() else player.startupProgress() },
        )
        if (!opened) {
            loadedId = _channel.value?.id
            if (liveOnExo.value) exo.abandon(context.getString(R.string.own_channel_recovery_failed))
            else player.abandonLive(context.getString(R.string.own_channel_recovery_failed))
        }
    }

    /**
     * Open [channel]. Runs inside the controller's one tune job, so a newer pick cancels it.
     *
     * Every check that can refuse runs **before** the screen claims the new channel: the data saver,
     * the profile's adult filter and minting a Stalker link. A refusal used to leave the new channel's
     * name over the old channel's picture; now the screen stays as it was, and the tapped row can be
     * tapped again.
     */
    private suspend fun open(channel: ChannelEntity) {
        guideLoadJob?.cancel()
        loadedId = channel.id
        val showing = _channel.value
        val refuse = { loadedId = showing?.id }
        if (!dataSaver.allowsStreaming()) return refuse()
        val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return refuse()
        if (!AdultCategoryClassifier.allows(pid, channel.categoryId, profileDao, categoryDao)) return refuse()

        val source = withContext(Dispatchers.IO) { sourceDao.getById(channel.sourceId) }
        // Stalker portals mint a play URL per tune; the stored "URL" is a portal command until then.
        val url = if (streamUrlResolver.needsResolve(source)) {
            runCatching { streamUrlResolver.resolve(source!!, channel.streamUrl) }.onFailure { if (it is CancellationException) throw it }.getOrNull() ?: return refuse()
        } else {
            channel.streamUrl
        }

        currentCoroutineContext().ensureActive()
        timeshift.clear() // a new channel is never still rewound into the old one's archive
        _replaying.value = false
        lastCatchup = null
        // A renamed channel keeps its new name on this screen too — the row the user tapped had it.
        val named = custom.value.itemNames[CustomizeKeys.channel(channel)]?.let { channel.copy(name = it) } ?: channel
        if (showing?.id != named.id) previousSelection.value = showing
        _channel.value = named
        _nowNext.value = null
        _timelineProgrammes.value = emptyList()
        // The television first, if one has been picked. It plays the stream itself, so nothing below
        // about engines, sound-only or the local session applies to it.
        val handedOver = cast.offer(
            this,
            CastRequest(
                url = url,
                title = named.name,
                logoUrl = named.displayLogoUrl,
                isLive = true,
                httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
            ),
        )
        if (!handedOver) {
            // Core decides which engine opens it, arms the ladder and starts it — the television's
            // order of authority (DRM, the pin, a learned panel refusal, the playlist, the setting).
            // The link minted above is reused for the first rung: minting ends the previous session.
            live.start(named, source, resolved = url)
            applyAudioOnlyDefault(channel)
        } else {
            if (showing?.id != named.id) previousSelection.value = showing
        }
        recordHistory(pid, channel.id)
        guideLoadJob?.cancel()
        guideLoadJob = scope.launch {
            // EPG never holds the first-frame budget or the next channel choice.
            val nowNext = epgReader.nowNext(channel, custom.value, settings.epgOffsetMinutes.first())
            val timeline = catchupProgrammes().map { LiveProgramme(it.startMs, it.stopMs, it.title) }
            currentCoroutineContext().ensureActive()
            if (_channel.value?.id == channel.id) {
                _nowNext.value = nowNext
                _timelineProgrammes.value = timeline
            }
        }
    }

    /**
     * The HUD's engine button on a live channel: flip this channel between ExoPlayer and mpv, and
     * remember the choice for next time.
     *
     * The television calls the mpv side "compatibility mode" and files it per channel, and this is
     * that same store and that same pin — so a channel pinned on one device opens on mpv on the other
     * once the two have synced.
     */
    fun toggleLiveEngine() {
        // A replay is a recorded programme, not the live stream: re-tuning here would swap what the
        // user is watching for whatever is on that channel now.
        // A copy saved on this device (N4) is the exception: the other engine continues it at the same moment.
        if (_replaying.value || (timeshift.isRewound && !hasLocalCopy.value)) return
        live.toggleEngine()
    }

    // --- Multiview: channels kept from the browse screen ------------------------------------------
    // The plan's second entry point: pick two to four channels from the Live list, then play one and
    // the grid opens already filled. Held here, not in a view model, for the same reason everything
    // else here is: the list that fills it and the player that empties it are different screens.
    private val _multiviewSelection = MutableStateFlow<List<ChannelEntity>>(emptyList())
    val multiviewSelection: StateFlow<List<ChannelEntity>> = _multiviewSelection

    /** Keep [channel] for the grid, up to [limit] tiles. Adding one twice does nothing. */
    fun addToMultiview(channel: ChannelEntity, limit: Int) {
        val current = _multiviewSelection.value
        if (current.any { it.id == channel.id } || current.size >= limit) return
        _multiviewSelection.value = current + channel
    }

    fun clearMultiviewSelection() {
        _multiviewSelection.value = emptyList()
    }

    /** The playlist a channel came from, so a caller can ask what it allows (the tile budget). */
    suspend fun sourceOf(channel: ChannelEntity): tv.own.owntv.core.database.entity.SourceEntity? =
        withContext(Dispatchers.IO) { sourceDao.getById(channel.sourceId) }

    /**
     * Tune [channel] into a Multiview tile's own engine.
     *
     * Routed through here for the same reason the television routes it through its view model: which
     * URL a channel actually plays is the playlist's business — its User-Agent, the channel's headers,
     * and, for a Stalker portal, a command that has to be resolved to a link per play. None of that
     * belongs in a grid, and a second copy of it would drift.
     *
     * The system session is deliberately **not** published: the lockscreen, the audio focus and the
     * foreground service belong to the one stream the user is watching, and a grid of four muted
     * pictures is not four of those.
     */
    fun tuneTile(engine: tv.own.owntv.player.LivePreviewEngine, channel: ChannelEntity, muted: Boolean) {
        scope.launch {
            if (!dataSaver.allowsStreaming()) return@launch
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            if (!AdultCategoryClassifier.allows(pid, channel.categoryId, profileDao, categoryDao)) return@launch
            // Core builds the tile's URL, headers and per-playlist overrides exactly as a full-screen
            // tune does — including "Prefer HLS", which the phone's own copy of this had left out.
            live.playTile(engine, channel, muted)
        }
    }

    /**
     * Start this channel without a picture when the user has already said so — either for this
     * channel in particular, or for mobile data in general.
     *
     * Read once, after the stream opens: dropping the video track is something the engine does to a
     * stream it already has, and asking before there is one would have nothing to act on.
     */
    private suspend fun applyAudioOnlyDefault(channel: ChannelEntity) {
        val remembered = settings.audioPerChannelNow() && audioOnlyStore.isAudioOnly(audioOnlyKey(channel))
        val onData = settings.audioOnMobileDataNow() && dataSaver.isMetered()
        // Both ways, every time. Turning the picture off is a decision about *this* channel, and the
        // engine keeps the flag across a retune — so without the else, one tap on Sound only silently
        // became every channel afterwards, looking for all the world like a setting that remembered.
        // Asked of the engine that actually has the stream. Sent to mpv, none of this happened on a
        // live channel: a channel the user had put into sound-only opened with its picture again,
        // and "Sound only on mobile data" quietly did nothing at all.
        val playing = currentEngine
        if (remembered || onData) playing.enterAudioOnly() else playing.exitAudioOnly()
    }

    /**
     * Turn the picture off or back on, and remember the choice for this channel when the user asked
     * for it to be remembered. The player alone would forget it the moment the channel changed.
     */
    fun setAudioOnly(audioOnly: Boolean) {
        // The engine holding the stream, not mpv: pressing Sound only on a live channel left the
        // screen but went on decoding video nobody could see — the opposite of what the button is
        // for, and the phone's largest single battery and data saving.
        val playing = currentEngine
        if (audioOnly) playing.enterAudioOnly() else playing.exitAudioOnly()
        val channel = _channel.value ?: return
        scope.launch {
            if (settings.audioPerChannelNow()) audioOnlyStore.set(audioOnlyKey(channel), audioOnly)
        }
    }

    /** The stable per-item key, with the stream URL as the fallback the engine stores also use. */
    private fun audioOnlyKey(channel: ChannelEntity): String =
        enginePinKey(channel.sourceId, MediaType.LIVE.name, channel.remoteId) ?: channel.streamUrl

    /**
     * Every Live TV category across every playlist, for the Multiview picker.
     *
     * The picker cannot start at a channel list the way the player's does: the player already has a
     * channel playing and its category is the obvious place to look, while an empty tile has no such
     * context — and a flat list of every channel is tens of thousands of rows on a real playlist.
     * Hidden categories are dropped and renames applied, so it matches what is seen everywhere else.
     */
    suspend fun liveCategoriesForPicker(): List<Pair<LiveKey, String>> {
        val c = ctx.value
        if (c.profileId < 0) return emptyList()
        val cust = custom.value
        val (cats, kids, alpha) = withContext(Dispatchers.IO) {
            Triple(
                categoryDao.observe(c.liveSourceIds.ifEmpty { listOf(-1L) }, MediaType.LIVE).first(),
                profileDao.getById(c.profileId)?.isKids == true,
                settings.sortLive.first() == SettingsRepository.SortMode.ALPHA,
            )
        }
        // The Live screen's own folders: custom categories, hides, renames, kids filter and order.
        return cats.railCategories(cust, kids = kids, alphaRest = alpha).map { e ->
            (e.categoryId?.let { LiveKey.Folder(it) } ?: LiveKey.Custom(e.customId!!)) to e.displayName
        }
    }

    /** One category's channels — a provider folder or a custom category — with the same hide/rename
     *  treatment the Live list applies. */
    suspend fun channelsInCategoryForPicker(key: LiveKey): List<ChannelEntity> {
        val c = ctx.value
        if (c.profileId < 0) return emptyList()
        val cust = custom.value
        val ids = c.liveSourceIds.ifEmpty { listOf(-1L) }
        return withContext(Dispatchers.IO) {
            val cats = categoryDao.observe(ids, MediaType.LIVE).first()
            val hidden = AdultCategoryClassifier.hiddenCategoryIds(cats, cust.hiddenCategories, profileDao.getById(c.profileId)?.isKids == true)
            val raw = when (key) {
                is LiveKey.Folder -> {
                    val category = categoryDao.getById(key.id) ?: return@withContext emptyList()
                    channelDao.snapshotByCategoryManual(
                        categoryId = category.id,
                        profileId = c.profileId,
                        contextKey = CustomizeKeys.category(category),
                        limit = SIBLING_LIMIT,
                    )
                }
                is LiveKey.Custom -> customCategoryDao.snapshotChannels(c.profileId, key.id, ids, SIBLING_LIMIT)
                else -> emptyList()
            }
            raw.filter { tv.own.owntv.core.live.isChannelVisible(it, cust, hidden) }
        }.map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    private suspend fun loadSiblings(channel: ChannelEntity) {
        val c = ctx.value
        if (c.profileId < 0) return
        val providerChannel = withContext(Dispatchers.IO) { channelDao.getById(channel.id) } ?: channel
        val category = providerChannel.categoryId?.let { withContext(Dispatchers.IO) { categoryDao.getById(it) } }
        val list = withContext(Dispatchers.IO) {
            if (category != null) {
                channelDao.snapshotByCategoryManual(
                    categoryId = category.id,
                    profileId = c.profileId,
                    contextKey = CustomizeKeys.category(category),
                    limit = SIBLING_LIMIT,
                )
            } else {
                // No category of its own: fall back to the profile's whole list, in provider order.
                channelDao.snapshotAll(c.liveSourceIds.ifEmpty { listOf(-1L) }, SIBLING_LIMIT)
            }
        }
        val cust = custom.value
        val categories = withContext(Dispatchers.IO) { categoryDao.observe(c.liveSourceIds.ifEmpty { listOf(-1L) }, MediaType.LIVE).first() }
        val hidden = AdultCategoryClassifier.hiddenCategoryIds(categories, cust.hiddenCategories, profileDao.getById(c.profileId)?.isKids == true)
        val visible = list.filter { tv.own.owntv.core.live.isChannelVisible(it, cust, hidden) }
        val grouped = ChannelVersionPolicy.grouped(visible, cust)
        val currentGroup = ChannelVersionPolicy.groupKey(providerChannel, cust)
        _siblings.value = grouped.map { if (cust.groupChannelVersions && ChannelVersionPolicy.groupKey(it, cust) == currentGroup) providerChannel else it }
            .map { ch -> cust.itemNames[CustomizeKeys.channel(ch)]?.let { ch.copy(name = it) } ?: ch }
    }

    /** Already-aired programmes this channel's archive still holds, newest first. */
    suspend fun catchupProgrammes(): List<EpgProgrammeEntity> {
        val channel = _channel.value ?: return emptyList()
        return epgReader.catchupProgrammes(
            channel,
            custom.value,
            settings.epgOffsetMinutes.first(),
            ctx.value.sourceIds,
        )
    }

    /**
     * Replay a past programme from the archive. Seekable, so it plays as VOD rather than as live.
     *
     * [on] is the channel it aired on, for the Guide, where a programme is picked without tuning its
     * channel first — starting the live stream only to abandon it a second later would cost the user
     * a connection and the provider a session. Omitted, it is the channel already playing.
     */
    fun playCatchup(programme: EpgProgrammeEntity, on: ChannelEntity? = null) {
        val channel = on ?: _channel.value ?: return
        if (channel.id != loadedId) {
            loadedId = channel.id
            _channel.value = channel
            _nowNext.value = null
            _timelineProgrammes.value = emptyList()
        }
        // The controller's one tune job: a live tune of this channel still resolving is cancelled here
        // instead of starting over the replay a moment later (the Guide's race), and a later pick
        // cancels this in turn.
        live.launch {
            if (!dataSaver.allowsStreaming()) return@launch
            val pid = ctx.value.profileId.takeIf { it >= 0 } ?: return@launch
            if (!AdultCategoryClassifier.allows(pid, channel.categoryId, profileDao, categoryDao)) return@launch
            val url = archiveUrls.forProgramme(channel, programme) ?: run {
                // Silence here is indistinguishable from a broken button: the tap did nothing, said
                // nothing, and left the user to guess whether the app or the provider was at fault.
                catchupUnavailable()
                return@launch
            }
            val source = withContext(Dispatchers.IO) { sourceDao.getById(channel.sourceId) }
            // isArchive: providers cut archive segments mid-GOP, and the engine needs to tolerate it.
            _replaying.value = true
            lastCatchup = programme
            val handedOver = cast.offer(
                this@LiveTuner,
                CastRequest(
                    url = url,
                    title = channel.name,
                    subtitle = programme.title,
                    logoUrl = channel.displayLogoUrl,
                    isLive = false,
                    httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
                ),
            )
            if (!handedOver) {
                // A replay is an mpv stream, so the live ExoPlayer engine lets go of its channel —
                // its connection and its decoder — before mpv asks for either, and the live ladder
                // stands down: its alarm would otherwise stop a replay that plays perfectly well.
                releaseForArchive()
                player.play(
                    url = url,
                    title = channel.name,
                    subtitle = programme.title,
                    logoUrl = channel.displayLogoUrl,
                    isLive = false,
                    isArchive = true,
                    userAgent = source?.userAgent,
                    httpHeaders = SourceOverrides.headersWithReferer(channel.httpHeaders, source),
                )
                publishToSystem()
            }
            recordHistory(pid, channel.id)
            // The clock over a replay says yesterday 13:00, not now — same as on the television.
            // Not while casting: the timeshift follows the LOCAL player's position, and there is no
            // local player to follow.
            if (!handedOver) timeshift.followArchiveFrom(programme.startMs)
        }
    }

    /**
     * "Go back to…": start the archive [offsetSec] seconds behind live in one jump.
     *
     * Not a replay — this is still the channel, just behind, so the live bar and the way back to now
     * stay. That is why it goes through the timeshift rather than through [playCatchup].
     */
    fun jumpBackTo(offsetSec: Int) {
        if (casting()) return
        val ch = _channel.value?.takeIf { it.catchup } ?: return
        _replaying.value = false
        timeshift.beginAt(ch, offsetSec)
    }

    /**
     * Tell the user the archive could not be opened.
     *
     * Every catch-up path here returned quietly when no URL could be built, so a provider without a
     * recording and a bug in the app looked identical from the sofa. The television says the same
     * sentence through its own in-app toast; this is the phone's half of that.
     */
    private fun catchupUnavailable() {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.content_epg_catchup_unavailable),
            android.widget.Toast.LENGTH_SHORT,
        ).show()
    }

    /** Offsets worth offering in the catch-up sheet, nearest first; empty without an archive. */
    fun jumpOptions(): List<Int> =
        // The provider archive's alone — a channel without catch-up gets no catch-up control for its saved copy.
        if (casting()) emptyList() else _channel.value?.takeIf { it.catchup }?.let { timeshift.jumpOptions(it) } ?: emptyList()

    /**
     * Tune the channel carrying provider number [number] — the numeric entry in the channel sheet.
     *
     * The current playlist's own channels first, then the profile's other Live playlists, because a
     * number is the provider's and two providers routinely disagree about who is channel 101. Two
     * visible channels with the same number and no way to choose between them is [DirectTune.Ambiguous]
     * rather than a silent guess.
     */
    suspend fun tuneByNumber(number: Int): DirectTune {
        val sourceIds = ctx.value.liveSourceIds.ifEmpty { return DirectTune.NotFound }
        val playing = _channel.value
        return runCatching {
            val hidden = custom.value.hiddenItems
            val ordered = if (playing == null) sourceIds else {
                listOf(playing.sourceId) + sourceIds.filter { it != playing.sourceId }
            }
            // Stage by source, so the playing playlist's own 101 wins over another playlist's 101
            // instead of the two of them cancelling each other out as an ambiguity.
            for (sourceId in ordered) {
                val hits = withContext(Dispatchers.IO) { channelDao.findByNumber(listOf(sourceId), number) }
                    .filter { CustomizeKeys.channel(it) !in hidden }
                when (hits.size) {
                    0 -> continue
                    1 -> {
                        switchTo(hits.first())
                        return DirectTune.Found(hits.first().name)
                    }
                    else -> return DirectTune.Ambiguous(hits.size)
                }
            }
            DirectTune.NotFound
        }.getOrElse { DirectTune.Failed }
    }

    /** Settings → Live rewind step, read live so a change applies without a restart. */
    private val rewindStepSec: StateFlow<Int> = settings.liveRewindStepSec
        .stateIn(scope, SharingStarted.Eagerly, tv.own.owntv.core.settings.SeekSteps.DEFAULT_LIVE_REWIND_STEP_SEC)

    /**
     * One live skip: [rewindStepSec] back into the archive, or toward live when [forward].
     *
     * The phone used the films' seek step here, so "Live rewind step" in Settings did nothing at all.
     */
    fun skipLive(forward: Boolean) {
        val step = rewindStepSec.value
        scrubLive(if (forward) -step else step)
    }

    /** Drag back into the archive (+) or toward live (−), in seconds. */
    fun scrubLive(deltaSec: Int) {
        if (casting()) return
        val ch = _channel.value ?: return
        timeshift.scrub(ch, deltaSec)
    }

    /**
     * How deep this channel's archive goes, in seconds — the length of the rewind bar.
     *
     * **Zero while casting**, which is what takes the rewind bar off the screen. Live rewind works by
     * loading one archive URL after another and watching the local player's position to know when to
     * load the next; on a receiver there is no such position, so offering the bar would give the user
     * a control that quietly does nothing.
     */
    fun archiveWindowSec(): Int =
        if (casting()) 0 else _channel.value?.let { timeshift.windowSec(it) } ?: 0

    /** Whether the stream is on a receiver rather than on this phone. */
    private fun casting(): Boolean = cast.engine.value != null

    /** Back to the real-time edge, off the archive stream. */
    fun goToLive() {
        timeshift.clear()
        _channel.value?.let { ch -> live.launch { open(ch) } }
    }

    /**
     * Turn one point in the archive into a playing stream — the "URL out" half of [LiveTimeshift].
     * False when no archive URL can be built, or the user reached live while it was being resolved.
     */
    private suspend fun loadArchiveStream(ch: ChannelEntity, startMs: Long, offsetSec: Int): Boolean {
        val (url, source) = withContext(Dispatchers.IO) {
            val source = sourceDao.getById(ch.sourceId) ?: return@withContext null
            val tz = settings.resolveCatchupTimeZone()
            archiveUrls.forTimeshift(ch, source, startMs, offsetSec, tz)?.let { it to source }
        } ?: run {
            // The timeshift hands back to the live edge from here, which on its own is indistinguishable
            // from "Go back to…" doing nothing at all.
            catchupUnavailable()
            return false
        }
        if (timeshift.offsetSec.value == null) return false // user jumped back to live meanwhile
        // The rewind plays out of the archive on mpv, so the live ExoPlayer engine stops first. This
        // is the one that cost two connections and made two sounds: drag the bar back ten minutes and
        // the live stream went on playing behind the archive. A live tune still in flight is cancelled
        // too — the rewind is the newer request.
        live.cancelTune()
        live.releaseForArchive()
        player.play(
            url = url,
            title = ch.name,
            logoUrl = ch.displayLogoUrl,
            isArchive = true,
            userAgent = source.userAgent,
            httpHeaders = SourceOverrides.headersWithReferer(ch.httpHeaders, source),
            rewindStartMs = startMs,
        )
        publishToSystem()
        return true
    }

    /** Stop playing altogether — the mini player's swipe-down, and nothing else. */
    fun stop() {
        guideLoadJob?.cancel()
        recoveryOpenedId = null
        timeshift.clear()
        loadedId = null
        stepSelectionId = null
        lastCatchup = null
        _channel.value = null
        _nowNext.value = null
        _timelineProgrammes.value = emptyList()
        // Withdraw first: a session left published after the sound stops keeps answering the
        // lockscreen and the headphone button for a stream that no longer exists.
        cast.release(this)
        session.attach(null)
        PlaybackService.stop(context)
        // Both engines and every watcher the ladder owns, or one of them fires on a channel nobody is
        // watching and stops the shared player — which by then may be showing a film.
        live.stop()
    }

    /**
     * The television is taking the channel. Live has no position worth carrying — the receiver joins
     * at the edge, which is where the phone was too — so the number is only there for the interface.
     */
    override fun releaseToCast(): Long {
        timeshift.clear()
        val position = currentEngine.position.value
        session.attach(null)
        // BOTH engines. Tapping Cast while a live channel played stopped mpv only, so ExoPlayer went
        // on playing the same channel on the handset beside the television — two pictures, two
        // sounds, and two of the playlist's connections for one thing being watched.
        //
        // The ladder goes with them: the channel is the television's now, and a give-up alarm or an
        // mpv watcher left armed would act on a stream that is playing perfectly well in another room.
        live.stop()
        return position
    }

    /**
     * The cast ended, so the channel comes back here. Restarted rather than resumed: a live stream
     * has no position to return to, and re-tuning is what puts the phone back at the edge.
     */
    override fun resumeFromCast(positionMs: Long) {
        val channel = _channel.value ?: return
        // A replay is a programme, not a channel, so coming back to the live edge would be the wrong
        // thing entirely — it is started again instead. From its beginning: the receiver's position
        // is not a place the archive URL can be re-entered at.
        val replay = lastCatchup.takeIf { _replaying.value }
        if (replay != null) playCatchup(replay, channel) else live.launch { open(channel) }
    }

    private suspend fun recordHistory(profileId: Long, channelId: Long) {
        runCatching {
            withContext(Dispatchers.IO) {
                historyDao.record(
                    WatchHistoryEntity(profileId = profileId, mediaType = MediaType.LIVE, itemId = channelId),
                )
            }
        }
    }

    private companion object {
        const val SIBLING_LIMIT = 2_000

        /** How long Channel +/− waits for the user to stop pressing before it opens a stream — the
         *  television's figure. */
        const val ZAP_TUNE_DELAY_MS = 500L
    }
}

/**
 * What typing a channel number produced. Every outcome is something the user is told: a number that
 * matches nothing must not look like a tap that was simply ignored.
 */
sealed interface DirectTune {
    data class Found(val name: String) : DirectTune
    data object NotFound : DirectTune
    data class Ambiguous(val count: Int) : DirectTune
    data object Failed : DirectTune
}

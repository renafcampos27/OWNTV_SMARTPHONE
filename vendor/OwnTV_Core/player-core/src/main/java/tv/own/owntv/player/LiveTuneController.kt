package tv.own.owntv.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.playStreamUrl
import tv.own.owntv.core.database.entity.resolveStreamUrl
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.player.ForceMpvStore
import tv.own.owntv.core.player.enginePinKey
import tv.own.owntv.core.settings.LiveBuffer
import tv.own.owntv.core.settings.LiveLatency
import tv.own.owntv.core.settings.LiveReserveResolver
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.stalker.ReconnectUrlProvider
import tv.own.owntv.core.stalker.StreamUrlResolver

/**
 * One live channel from "tune" to "playing" or "gave up", for both apps.
 *
 * This is the orchestration the television's `LiveViewModel` and the phone's `LiveTuner` each carried
 * in their own ~600 lines, and which had drifted apart: engine routing ([LiveRouting]), the fallback
 * [LiveLadder] and its "Give up after" alarm, the ExoPlayer ⇄ mpv handover with its decoder-release
 * wait, the Prefer-HLS `.ts` rung, HLS-redirect learning, the Stalker reconnect link and the
 * per-playlist overrides. Each app now keeps only presentation — which channel is on screen, the HUD,
 * history, casting.
 *
 * **Every tune is one job, and a newer one cancels it** ([launch]). Two quick picks can no longer
 * finish in the wrong order, and a catch-up started while a live tune is still resolving is not then
 * overtaken by it.
 *
 * Everything is main-thread: [scope] must be a Main-dispatched scope, as the engines require.
 */
class LiveTuneController(
    private val scope: CoroutineScope,
    private val engines: LiveEngines,
    private val host: Host,
) {

    /** What the controller needs from outside the engines. [CoreHost] is the one both apps use. */
    interface Host {
        suspend fun sourceOf(sourceId: Long): SourceEntity?
        /** A Stalker playlist stores a portal command, minted into a URL per play. */
        fun needsResolve(source: SourceEntity?): Boolean
        suspend fun resolve(source: SourceEntity, cmd: String): String?
        /** true = pinned to mpv, false = pinned to ExoPlayer, null = follow the setting. */
        suspend fun enginePin(channel: ChannelEntity): Boolean?
        suspend fun pin(channel: ChannelEntity, onMpv: Boolean)
        suspend fun hlsOnly(): Boolean = false
        suspend fun manualTs(channel: ChannelEntity): Boolean = false
        suspend fun channelOptions(channel: ChannelEntity): tv.own.owntv.core.settings.ChannelPlaybackOptions? = null
        suspend fun globalPreference(): EnginePreference
        /** Settings → "Give up after", in seconds; 0 is Never. */
        suspend fun globalBudgetSecs(): Int
        fun meta(channel: ChannelEntity): MediaMeta
        /** Mirror a ladder decision into the user-visible playback error log. */
        fun recordLadderEvent(onExo: Boolean, reason: PlayerFailureReason, detail: String)
        fun nowMs(): Long = android.os.SystemClock.elapsedRealtime()
        /** An engine has just taken the stream (the phone publishes its media session here). */
        fun onEngineStarted() {}
        fun onOpened(channel: ChannelEntity) {}
        /** A handover is re-opening the channel at its live edge, so any rewind is over. */
        fun onBackToLiveEdge() {}
    }

    private val _liveOnExo = MutableStateFlow(false)

    /**
     * Whether live is on ExoPlayer right now — false is mpv. The *actual* engine rather than the pin:
     * an automatic handover leaves a channel on mpv while still unpinned.
     */
    val liveOnExo: StateFlow<Boolean> = _liveOnExo.asStateFlow()

    private val _previewBlocked = MutableStateFlow(false)

    /** True while the preview pane is held back because its panel allows one stream and mpv has it. */
    val previewBlockedSingleSession: StateFlow<Boolean> = _previewBlocked.asStateFlow()

    /** The channel this controller is tuning or playing full-screen; null when it is not driving one. */
    private var current: ChannelEntity? = null

    private val ladder = LiveLadder()
    private var armedBudgetMs: Long = LiveLadder.NO_BUDGET
    private var activeBudgetMs: Long = LiveLadder.NO_BUDGET
    private var engineAttemptStarted = false
    private var openingReported = false

    /**
     * The channel whose ExoPlayer rung is the explicit `.ts` one. Keyed by channel, not a bare flag: the
     * preview tunes through [exoUrlFor] too, so a flag left set by one channel's TS rung would send every
     * other channel's preview to `.ts` until the next tune.
     */
    private var hlsOnly = false
    private var manualTs = false
    private var channelOptions: tv.own.owntv.core.settings.ChannelPlaybackOptions? = null
    private var forceTsFor: String? = null

    /** The Stalker command whose minted URL ExoPlayer holds, so a re-focus or a promote can tell "same
     *  channel" without minting again — the engine's own URL is the minted link, not the command. */
    private var exoStalkerCmd: String? = null

    /** When this controller last stopped ExoPlayer, for [awaitExoRelease]. */
    private var exoStoppedAtMs: Long? = null

    @Volatile private var generation = 0L
    private var choiceId: Long? = null
    private var tuneJob: Job? = null
    private var previewJob: Job? = null
    private var exoResolveJob: Job? = null
    private var exoWatchJob: Job? = null
    private var mpvOutcomeJob: Job? = null
    private var mpvHandoffJob: Job? = null
    private var deadlineJob: Job? = null

    // --- Entry points -----------------------------------------------------------------------------

    /**
     * Run [block] as the one tune in flight, cancelling whichever was running. Every playback-changing
     * entry point of an app goes through here — a tune, a catch-up, a retune at the live edge — so the
     * last thing asked for is the thing that happens.
     */
    fun launch(block: suspend LiveTuneController.() -> Unit): Job {
        cancelTune()
        return scope.launch { block() }.also { tuneJob = it }
    }

    /** Tune [channel] full-screen, as the one tune in flight. */
    fun tune(channel: ChannelEntity): Job = launch { start(channel, host.sourceOf(channel.sourceId)) }

    /**
     * Route [channel], arm its ladder and start the first engine. Called from inside [launch].
     *
     * [resolved] is a URL already minted for a Stalker channel by this tune (the phone mints one before
     * offering the channel to a cast receiver): minting ends the portal's previous session, so the first
     * rung reuses it and only later rungs mint again.
     */
    suspend fun start(channel: ChannelEntity, source: SourceEntity?, resolved: String? = null, choiceId: Long? = null, localHls: Boolean = false) {
        currentCoroutineContext().ensureActive()
        generation++
        this.choiceId = choiceId
        engines.awaitSettings()
        currentCoroutineContext().ensureActive()
        previewJob?.cancel()
        cancelLadderJobs()
        current = channel
        _previewBlocked.value = false
        channelOptions = if (localHls) null else host.channelOptions(channel)
        manualTs = !localHls && (channelOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).directTs(host.manualTs(channel))
        hlsOnly = localHls || (channelOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).hlsOnly(host.hlsOnly(), manualTs)
        val inherited = source?.liveEnginePreference?.let { name -> EnginePreference.entries.firstOrNull { it.name == name } }
            ?: host.globalPreference()
        val setting = (channelOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).effectiveEngine(inherited, hlsOnly, manualTs)
        val route = LiveRouting.decide(
            setting = setting,
            pin = if (localHls || hlsOnly || manualTs || channelOptions?.engine != null) setting.startsOnMpv else host.enginePin(channel),
            drmProtected = channel.drmConfig != null,
            panelRefusesSegments = !manualTs && !hlsOnly && panelRefusesSegments(channel, source),
        )
        currentCoroutineContext().ensureActive()
        engineLog("tune '${channel.name}' -> ${route.why} [${route.preference.name}]")
        arm(channel, source, route.preference)
        currentCoroutineContext().ensureActive()
        if (route.onMpv) startOnMpv(channel, source, route.why, resolved = resolved)
        else startOnExo(channel, source, resolved)
    }

    /** Local HLS keeps upstream identity/headers in the producer, never in the reader. */
    suspend fun startLocalHls(channel: ChannelEntity, url: String, choiceId: Long) {
        start(channel.copy(streamUrl = url, sourceId = 0, remoteId = null, httpHeaders = null,
            directSource = null, drmConfig = null, manifestType = tv.own.owntv.core.player.ManifestType.HLS.key),
            null, choiceId = choiceId, localHls = true)
    }

    /**
     * The HUD's engine button: flip the channel on screen between the two engines and pin the choice.
     *
     * For THIS tune the ladder is armed "only" on the engine the user just named — bouncing them off it
     * seconds later makes the control look broken. The next tune of the channel reads the pin and gets
     * the full ladder back. The app refuses first while a catch-up or a rewind is on screen.
     */
    fun toggleEngine(channel: ChannelEntity? = current) {
        channel ?: return
        if (hlsOnly || manualTs) return
        // A protected channel has only one engine that can obtain its key.
        if (channel.drmConfig != null) return
        val goToMpv = _liveOnExo.value
        engineLog("engine toggle '${channel.name}' -> ${if (goToMpv) "mpv" else "exoplayer"}")
        launch {
            engines.awaitSettings()
            currentCoroutineContext().ensureActive()
            if (host.hlsOnly() || host.manualTs(channel)) return@launch
            host.pin(channel, goToMpv)
            val source = host.sourceOf(channel.sourceId)
            currentCoroutineContext().ensureActive()
            current = channel
            arm(channel, source, EnginePreference.onlyOn(goToMpv))
            if (goToMpv) startOnMpv(channel, source, "user chose compatibility mode")
            else switchToExo(channel, source)
        }
    }

    /**
     * The television's preview pane: play [channel] on ExoPlayer, muted or not, with no ladder.
     *
     * Never while promoted to full-screen — a late preview would re-mute the full-screen stream. On a
     * one-session panel a preview while mpv plays would lock the user's own stream out, so the pane
     * stays silent and [previewBlockedSingleSession] says why.
     */
    fun preview(channel: ChannelEntity, muted: Boolean) {
        if (_liveOnExo.value) return
        previewJob?.cancel()
        previewJob = scope.launch {
            engines.awaitSettings()
            val source = host.sourceOf(channel.sourceId)
            channelOptions = host.channelOptions(channel)
            manualTs = (channelOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).directTs(host.manualTs(channel))
            hlsOnly = (channelOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).hlsOnly(host.hlsOnly(), manualTs)
            currentCoroutineContext().ensureActive()
            if (host.needsResolve(source)) {
                if (exoStalkerCmd == channel.streamUrl && engines.exoCanPromote && engines.exoManualTs == manualTs && engines.exoChannelOptions == channelOptions && !engines.exoFailed) {
                    engines.exoSetMuted(muted)
                    return@launch
                }
                val url = host.resolve(source!!, channel.streamUrl) ?: return@launch
                currentCoroutineContext().ensureActive()
                if (_liveOnExo.value) return@launch // promoted to full-screen while resolving
                if (blockedBySingleSession(url)) return@launch
                exoStalkerCmd = channel.streamUrl
                setReconnect(channel, source)
                engines.exoPlay(url, muted, request(channel, source))
                return@launch
            }
            val url = exoUrlFor(channel, source)
            if (blockedBySingleSession(url)) return@launch
            if (engines.exoUrl == url && engines.exoCanPromote && engines.exoManualTs == manualTs && engines.exoChannelOptions == channelOptions && !engines.exoFailed) {
                engines.exoSetMuted(muted) // already previewing it (a re-focus) — just re-apply the mute
                return@launch
            }
            exoStalkerCmd = null
            setReconnect(null, null)
            engines.exoPlay(url, muted, request(channel, source))
        }
    }

    /**
     * Tune [channel] into a Multiview tile's own [engine] — same URL, headers, overrides and Stalker
     * minting as a full-screen tune, so the grid can never drift from it. No ladder: a tile that fails
     * shows its failure.
     */
    suspend fun playTile(engine: LivePreviewEngine, channel: ChannelEntity, muted: Boolean, stillActive: () -> Boolean = { true }) {
        val source = host.sourceOf(channel.sourceId)
        val tileOptions = host.channelOptions(channel)
        val tileTs = (tileOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).directTs(host.manualTs(channel))
        val tileHlsOnly = (tileOptions ?: tv.own.owntv.core.settings.ChannelPlaybackOptions()).hlsOnly(host.hlsOnly(), tileTs)
        val url = if (host.needsResolve(source)) {
            host.resolve(source!!, channel.streamUrl) ?: return
        } else {
            exoUrlFor(channel, source, tileTs, tileHlsOnly)
        }
        currentCoroutineContext().ensureActive()
        engine.awaitPlaybackSettings()
        currentCoroutineContext().ensureActive()
        if (stillActive()) engine.play(url, muted, request(channel, source, tileTs, tileOptions))
    }

    /**
     * Stop driving the channel but leave the stream playing — leaving full-screen for the Live screen,
     * where the pane re-takes ExoPlayer as a preview. Watchers and the alarm stand down: a preview has
     * no ladder.
     */
    fun detach() {
        cancelTune()
        current = null
        _liveOnExo.value = false
    }

    /** Stop ExoPlayer (the preview, or a detached stream) without touching mpv. */
    fun stopExo() {
        previewJob?.cancel()
        exoResolveJob?.cancel()
        exoWatchJob?.cancel()
        exoStalkerCmd = null
        setReconnect(null, null)
        stopExoEngine()
    }

    /** Cancel the tune in flight, from outside [launch]. */
    fun cancelTune() {
        tuneJob?.cancel()
        cancelOpening()
    }

    /** Invalidate resolver/watchdog results without cancelling the caller trying another variant. */
    fun cancelOpening() {
        generation++
        engines.exoCancelResolver()
        previewJob?.cancel()
        cancelLadderJobs()
        current = null
    }

    /**
     * Give playback to mpv for something that is not the live stream — a catch-up programme, a rewind
     * into the archive, a film. ExoPlayer lets go of its connection and decoder first, or the live
     * stream plays on underneath with a second sound. The whole ladder goes too: an alarm left armed
     * would stop a replay that plays perfectly well.
     *
     * Does not cancel the tune in flight, because a catch-up calls this from inside its own [launch] —
     * which is what cancels a live tune still resolving, so it cannot start over the archive. A caller
     * outside [launch] uses [cancelTune] first.
     */
    fun releaseForArchive() {
        cancelOpening()
        current = null
        _liveOnExo.value = false
        stopExo()
    }

    /** Stop everything: both engines, every watcher. */
    fun stop() {
        cancelTune()
        releaseForArchive()
        engines.mpvStop()
    }

    // --- Engines ----------------------------------------------------------------------------------

    /**
     * Open [channel] on ExoPlayer and watch it. If ExoPlayer already holds this channel — the preview
     * the user just pressed OK on — it is promoted (unmuted) instead of rebuilt.
     */
    private suspend fun startOnExo(channel: ChannelEntity, source: SourceEntity?, resolved: String? = null) {
        currentCoroutineContext().ensureActive()
        val allowPromotion = !_liveOnExo.value && !engines.mpvHasStream
        mpvOutcomeJob?.cancel() // ExoPlayer owns the channel now
        _liveOnExo.value = true
        engines.mpvStop() // mpv lets go of the connection and the decoder before ExoPlayer claims either
        if (host.needsResolve(source)) {
            startOnExoStalker(channel, source!!, resolved, allowPromotion)
            return
        }
        val url = exoUrlFor(channel, source)
        if (allowPromotion && engines.exoCanPromote && engines.exoManualTs == manualTs && engines.exoChannelOptions == channelOptions && !engines.exoFailed && engines.exoUrl == url) {
            engines.exoPromote(choiceId) // promote — instant if already playing, otherwise keeps loading
        } else {
            // Leaving a UHD channel: release its 4K decoder before the rebuild (no-op for SD/HD).
            engines.exoReleaseUhdDecoder()
            exoStalkerCmd = null
            setReconnect(null, null)
            engines.exoPlay(url, muted = false, request(channel, source))
        }
        reportEngineStarted()
        watchExo(channel, source)
    }

    private suspend fun startOnExoStalker(channel: ChannelEntity, source: SourceEntity, resolved: String?, allowPromotion: Boolean) {
        if (allowPromotion && engines.exoCanPromote && engines.exoManualTs == manualTs && engines.exoChannelOptions == channelOptions && !engines.exoFailed && exoStalkerCmd == channel.streamUrl && resolved == null) {
            engines.exoPromote(choiceId) // promote the preview that already holds this command
            setReconnect(channel, source)
            reportEngineStarted()
            watchExo(channel, source)
            return
        }
        exoResolveJob?.cancel()
        engines.exoReleaseUhdDecoder()
        if (resolved != null) {
            // Already minted by this tune: start now, so the caller's next line finds the stream there.
            playExoStalker(channel, source, resolved)
            return
        }
        // Detached: minting is a network call, and a ladder step that got here from a watcher must not
        // die with that watcher when [watchExo] replaces it.
        val owner = generation
        exoResolveJob = scope.launch {
            val url = host.resolve(source, channel.streamUrl) ?: return@launch
            currentCoroutineContext().ensureActive()
            if (owner != generation || !isCurrent(channel)) return@launch // zapped away while minting
            playExoStalker(channel, source, url)
        }
    }

    private fun playExoStalker(channel: ChannelEntity, source: SourceEntity, url: String) {
        exoStalkerCmd = channel.streamUrl
        setReconnect(channel, source)
        engines.exoPlay(url, muted = false, request(channel, source))
        reportEngineStarted()
        watchExo(channel, source)
    }

    /** Put [channel] on ExoPlayer, releasing mpv first when it holds the stream — mpv's stop is
     *  asynchronous, and handing over too early makes the app its own competitor for a session. */
    private suspend fun switchToExo(channel: ChannelEntity, source: SourceEntity?) {
        host.onBackToLiveEdge()
        if (!_liveOnExo.value) {
            engines.mpvStopAndAwaitRelease()
            val stoppedRevision = engines.mpvPlaybackRevision
            delay(OwnTVPlayer.SURFACE_HANDOFF_MS)
            while (engines.mpvPlaybackRevision != stoppedRevision && !engines.mpvPlaybackRequested) {
                if (!isCurrent(channel)) return
                delay(100L)
            }
            if (!isCurrent(channel)) return
        }
        startOnExo(channel, source)
    }

    /**
     * Open [channel] on mpv — a pinned channel, "mpv first", or ExoPlayer having given up.
     *
     * [forceTs] marks the `.ts` rung: "Prefer HLS" must not rewrite the URL. [resolved] is the first
     * rung's already-minted Stalker link; later rungs mint afresh, because a spent link is not reusable.
     */
    private suspend fun startOnMpv(
        channel: ChannelEntity,
        source: SourceEntity?,
        reason: String,
        forceTs: Boolean = false,
        resolved: String? = null,
    ) {
        if (!awaitPlaybackIntent(channel)) return
        val destinationRevision = engines.mpvPlaybackRevision
        engineLog("starting mpv for '${channel.name}' — reason=$reason")
        host.onBackToLiveEdge()
        // What ExoPlayer discovered before it let go: some panels redirect their advertised `.ts` to HLS,
        // and handing mpv that misleading URL traps FFmpeg at the manifest EOF. "Discovered" strictly
        // means ExoPlayer asked for something that was NOT HLS and got HLS anyway — an `.m3u8` we
        // requested teaches nothing about the `.ts` endpoint, and recording it branded a whole panel.
        val exoTuned = engines.exoUrl
        val exoDiscoveredHls = _liveOnExo.value && engines.exoIsHls &&
            exoTuned != null && !LiveStreamQuirks.isExplicitHlsUrl(exoTuned)
        exoWatchJob?.cancel() // mpv owns the channel now
        exoResolveJob?.cancel()
        mpvOutcomeJob?.cancel()
        _liveOnExo.value = false
        exoStalkerCmd = null
        if (engines.exoUrl != null) exoStoppedAtMs = host.nowMs()
        val drained = engines.exoStopAndAwaitDrain()
        if (!isCurrent(channel)) return
        if (!drained) {
            deadlineJob?.cancel()
            deadlineJob = null
            engines.mpvAbandon("Previous ExoPlayer requests did not finish within the handover deadline")
            return
        }
        awaitExoRelease()
        if (!isCurrent(channel)) {
            // The only exit that leaves the screen on mpv's surface with nothing loaded. Normal when the
            // user zapped during the release wait; in a support log it tells "abandoned" from "vanished".
            engineLog("mpv handoff for '${channel.name}' abandoned — the channel changed while ExoPlayer released")
            return
        }
        val stalker = host.needsResolve(source)
        val raw = when {
            !stalker -> channel.streamUrl
            resolved != null -> resolved
            else -> host.resolve(source!!, channel.streamUrl) ?: return
        }
        // Same "Prefer HLS" opt-out as the ExoPlayer URL, but keyed to mpv's OWN verdict: ExoPlayer
        // failing this channel's `.m3u8` says nothing about whether mpv can play it.
        val preferred = if (manualTs) HlsOnlyPolicy.directTsUrl(raw, source?.type == tv.own.owntv.core.model.SourceType.XTREAM)
            else if (hlsOnly) HlsOnlyPolicy.url(raw, source?.type == tv.own.owntv.core.model.SourceType.XTREAM)
            else if (forceTs || LiveStreamQuirks.lacksHlsVariantMpv(channel.streamUrl)) raw
            else resolveStreamUrl(raw, source)
        // Against the PANEL: the redirect is the provider's, so every later channel starts out knowing.
        if (exoDiscoveredHls) LiveStreamQuirks.rememberHlsRedirect(preferred)
        val url = if (!manualTs && LiveStreamQuirks.isKnownHlsHost(preferred)) LiveStreamQuirks.toHlsUrl(preferred) else preferred
        currentCoroutineContext().ensureActive()
        if (!isCurrent(channel)) return // zapped away while minting
        setReconnect(if (stalker) channel else null, source)
        while (engines.mpvPlaybackRevision != destinationRevision && !engines.mpvPlaybackRequested) {
            if (!isCurrent(channel)) return
            delay(100L)
        }
        if (!isCurrent(channel)) return
        engines.mpvPlay(url, request(channel, source))
        reportEngineStarted()
        watchMpv(channel, source)
    }

    /**
     * Let ExoPlayer's decoder go before mpv initialises. Nothing exposes "the MediaCodec is released",
     * so the only alternative to waiting is guessing, and guessing short reproduces the 0x80001000
     * codec-claim failure. Measured at ≈500 ms on a Realtek box.
     *
     * Only the part of the wait that has not already passed since ExoPlayer was last stopped: a tune
     * that starts on mpv with nothing on ExoPlayer for a while has no decoder to wait for, and half a
     * second of black on each of those would be a handover cost charged to a tune that never handed over.
     */
    private suspend fun awaitExoRelease() {
        val stoppedAt = exoStoppedAtMs ?: return
        val left = OwnTVPlayer.SURFACE_HANDOFF_MS - (host.nowMs() - stoppedAt)
        if (left > 0) delay(left)
    }

    private fun stopExoEngine() {
        if (engines.exoUrl != null) exoStoppedAtMs = host.nowMs()
        engines.exoStop()
    }

    // --- Watchers ---------------------------------------------------------------------------------

    private fun watchExo(channel: ChannelEntity, source: SourceEntity?) {
        exoWatchJob?.cancel()
        val owner = generation
        exoWatchJob = scope.launch {
            engines.watchExo(
                channelName = channel.name,
                // A watchdog outlives the tune that armed it; firing after that would stop a stream
                // nobody complained about.
                stillOurs = { owner == generation && isStillExo(channel) },
                // Into the ladder rather than straight to mpv: the ladder decides what "next" means.
                handOver = { reason ->
                    currentCoroutineContext().ensureActive()
                    if (owner == generation && isStillExo(channel)) advance(channel, source, reason)
                },
                onOpened = { if (owner == generation && isStillExo(channel)) { reportOpening(channel) } },
                // A provider back-off is a wait OwnTV agreed to; it is not charged to the budget.
                postponeDeadline = { if (owner == generation) ladder.postponeDeadline(it) },
                log = ::engineLog,
            )
        }
    }

    /** mpv runs its own retry and format ladder first, so its deadline is looser than ExoPlayer's. */
    private fun watchMpv(channel: ChannelEntity, source: SourceEntity?) {
        mpvOutcomeJob?.cancel()
        val owner = generation
        mpvOutcomeJob = scope.launch {
            val outcome = engines.awaitMpvOutcome(MPV_OPEN_TIMEOUT_MS)
            currentCoroutineContext().ensureActive()
            if (owner != generation || !isStillMpv(channel)) return@launch
            val reason = when {
                outcome == null -> "mpv never opened it (${MPV_OPEN_TIMEOUT_MS / 1000}s, no picture and no error)"
                outcome.opened -> {
                    engineLog("'${channel.name}' opened on mpv")
                    reportOpening(channel)
                    return@launch
                }
                else -> "mpv couldn't play it: ${outcome.error}"
            }
            advance(channel, source, reason)
        }
    }

    // --- The ladder -------------------------------------------------------------------------------

    private suspend fun arm(channel: ChannelEntity, source: SourceEntity?, preference: EnginePreference) {
        forceTsFor = null
        val secs = host.globalBudgetSecs()
        currentCoroutineContext().ensureActive()
        armedBudgetMs = if (secs <= 0) LiveLadder.NO_BUDGET else secs * 1000L
        activeBudgetMs = armedBudgetMs
        engineAttemptStarted = false
        ladder.arm(channel.streamUrl, preference, budgetMs = armedBudgetMs, nowMs = host.nowMs(), hlsOnly = hlsOnly) {
            hasHlsAlternative(channel, source)
        }
        startAlarm(channel)
    }

    /**
     * The alarm behind "Give up after", so the budget bounds the black screen rather than only the
     * decision to climb another rung: a rung entered at 24 s with a 35 s timeout of its own would
     * otherwise run to 59 s. The deadline is re-read on each pass, so a postponement moves the alarm.
     * A channel that opens stands the alarm down — later stalls belong to the watchdogs.
     */
    private fun startAlarm(channel: ChannelEntity) {
        deadlineJob?.cancel()
        val owner = generation
        deadlineJob = scope.launch {
            val startupGrace = LiveStartupGrace()
            val recoveryGrace = LiveRecoveryGrace()
            val providerCredit = ProviderWaitCredit()
            val pauses = LivePauseAccounting(host.nowMs())
            while (ladder.owns(channel.streamUrl)) {
                if (owner != generation) return@launch
                val progress = if (engineAttemptStarted) engines.startupProgress() else null
                val startupExtra = startupGrace.observe(progress) + recoveryGrace.observe(progress) + pauses.observe(host.nowMs(), playbackRequested())
                val providerExtra = providerCredit.observe(if (engineAttemptStarted) engines.providerWait() else null)
                if (startupExtra + providerExtra > 0L) {
                    ladder.postponeDeadline(startupExtra + providerExtra)
                    engineLog("deadline credit preparationMs=$startupExtra providerMs=$providerExtra generation=$owner")
                }
                val left = (ladder.deadlineAt() ?: return@launch) - host.nowMs()
                if (left <= 0 && playbackRequested()) break
                delay(if (left <= 0) 100L else minOf(left, 100L))
            }
            // Both gates matter: the ladder can still own a channel the user walked away from, and this
            // alarm stops an engine — on mpv's side the shared player, which may be showing a film.
            if (owner != generation || !ladder.owns(channel.streamUrl)) return@launch
            if (!isStillExo(channel) && !isStillMpv(channel)) return@launch
            if (acceptCurrentPlayback(channel)) return@launch
            val detail = "no playable stream within the ${activeBudgetMs / 1000}s budget and bounded preparation/provider allowance"
            engineLog("'${channel.name}' — giving up: $detail")
            host.recordLadderEvent(_liveOnExo.value, PlayerFailureReason.LIVE_NO_FALLBACK, "'${channel.name}': $detail")
            exoWatchJob?.cancel()
            exoResolveJob?.cancel()
            mpvOutcomeJob?.cancel()
            mpvHandoffJob?.cancel()
            abandon(channel, detail)
        }
    }

    private fun reportEngineStarted() {
        engineAttemptStarted = true
        openingReported = false
        host.onEngineStarted()
    }

    private fun acceptCurrentPlayback(channel: ChannelEntity): Boolean {
        if (!engineAttemptStarted || !isCurrent(channel)) return false
        val confirmed = if (_liveOnExo.value) engines.exoPlaybackConfirmed else engines.mpvPlaybackConfirmed
        if (!confirmed) return false
        reportOpening(channel)
        return true
    }

    private fun reportOpening(channel: ChannelEntity) {
        standDownAlarm()
        if (openingReported) return
        openingReported = true
        host.onOpened(channel)
    }

    private fun standDownAlarm() {
        ladder.markOpened()
        deadlineJob?.cancel()
        deadlineJob = null
    }

    /**
     * Move to the next untried rung after a failure, or give up when there is nothing left. The only
     * place the ladder is climbed, from either engine's watcher — which is what makes "each rung at most
     * once" hold, and that finiteness is what stops a channel bouncing between the engines for ever.
     */
    private suspend fun advance(channel: ChannelEntity, source: SourceEntity?, reason: String) {
        if (!ladder.owns(channel.streamUrl) || !isCurrent(channel) || !awaitPlaybackIntent(channel)) return
        // A failure observed before yielding/resolving cannot overtake current-source recovery.
        if (acceptCurrentPlayback(channel)) return
        val nowMs = host.nowMs()
        if (ladder.beginRecovery(nowMs)) {
            activeBudgetMs = LiveLadder.RECOVERY_BUDGET_MS
            engineLog("recovery episode generation=$generation budgetMs=$activeBudgetMs")
            startAlarm(channel)
        }
        val outOfTime = ladder.expired(nowMs)
        // A panel refusing the *request* (a busy 458, a 403, a rate limit) says nothing about the format,
        // so nothing may be learned from it.
        val next = ladder.advance(failureWasAboutFormat = !isRequestRefusal(reason), nowMs = nowMs) ?: run {
            val detail = if (outOfTime) "$reason — recovery/opening budget exhausted (${activeBudgetMs / 1000}s plus bounded allowance)" else reason
            engineLog("'${channel.name}' — no fallback left ($detail)")
            host.recordLadderEvent(_liveOnExo.value, PlayerFailureReason.LIVE_NO_FALLBACK, "'${channel.name}': $detail")
            abandon(channel, detail)
            return
        }
        val label = ladder.label(next)
        engineLog("'${channel.name}' falling back to $label ($reason)")
        // Before the switch, so the record still names the engine that failed.
        host.recordLadderEvent(_liveOnExo.value, PlayerFailureReason.LIVE_FALLBACK, "'${channel.name}': $label — $reason")
        if (next.onMpv) {
            // Detached on purpose. Every automatic rung is dispatched from inside a watcher job, and the
            // handoff cancels those watchers the moment it takes over — run inline it would cancel itself
            // at its first suspension point: mpv's surface up, mpv never asked to load, a black screen.
            mpvHandoffJob?.cancel()
            mpvHandoffJob = scope.launch { startOnMpv(channel, source, reason, forceTs = !next.isHls) }
        } else {
            forceTsFor = if (next.isHls) null else channel.streamUrl
            mpvHandoffJob?.cancel()
            mpvHandoffJob = scope.launch { switchToExo(channel, source) }
        }
    }

    /** Put the failure on screen: a stream that delivers no segment produces no frame AND no error, so
     *  the honest answer is written by whoever decided to stop trying — on the engine the HUD reads. */
    private fun abandon(channel: ChannelEntity, detail: String) {
        val reason = "'${channel.name}': $detail"
        if (_liveOnExo.value) engines.exoAbandon(reason) else engines.mpvAbandon(reason)
    }

    private fun cancelLadderJobs() {
        exoResolveJob?.cancel()
        exoWatchJob?.cancel()
        mpvOutcomeJob?.cancel()
        mpvHandoffJob?.cancel()
        deadlineJob?.cancel()
    }

    // --- Small helpers ----------------------------------------------------------------------------

    private fun playbackRequested(): Boolean = !engineAttemptStarted ||
        if (_liveOnExo.value) engines.exoPlaybackRequested else engines.mpvPlaybackRequested

    private suspend fun awaitPlaybackIntent(channel: ChannelEntity): Boolean {
        val owner = generation
        while (owner == generation && isCurrent(channel) && !playbackRequested()) delay(100L)
        return owner == generation && isCurrent(channel)
    }

    private fun isCurrent(channel: ChannelEntity): Boolean = current?.id == channel.id

    private fun isStillExo(channel: ChannelEntity): Boolean = _liveOnExo.value && isCurrent(channel)

    private fun isStillMpv(channel: ChannelEntity): Boolean = !_liveOnExo.value && isCurrent(channel)

    private fun blockedBySingleSession(url: String): Boolean {
        val blocked = engines.mpvHasStream && LiveStreamQuirks.isSingleSession(url)
        _previewBlocked.value = blocked
        return blocked
    }

    /**
     * Which of the channel's two addresses ExoPlayer should ask for: the "Prefer HLS" `.m3u8`, except on
     * the ladder's `.ts` rung or a channel already caught this session having no working `.m3u8`.
     */
    private fun exoUrlFor(channel: ChannelEntity, source: SourceEntity?, manualTs: Boolean = this.manualTs, hlsOnly: Boolean = this.hlsOnly): String =
        if (manualTs) HlsOnlyPolicy.directTsUrl(channel.streamUrl, source?.type == tv.own.owntv.core.model.SourceType.XTREAM)
        else if (hlsOnly) HlsOnlyPolicy.url(channel.streamUrl, source?.type == tv.own.owntv.core.model.SourceType.XTREAM)
        else if (forceTsFor == channel.streamUrl || LiveStreamQuirks.lacksHlsVariant(channel.streamUrl)) channel.streamUrl
        else channel.playStreamUrl(source)

    /** Whether "Prefer HLS" rewrites this channel's URL at all; if not, HLS and TS rungs are one
     *  attempt and the ladder drops the HLS ones. A Stalker command is not a URL. */
    private fun hasHlsAlternative(channel: ChannelEntity, source: SourceEntity?): Boolean =
        !manualTs && !host.needsResolve(source) && channel.playStreamUrl(source) != channel.streamUrl

    /** Whether this channel's panel was caught refusing its own signed segment URLs. Stalker is excluded:
     *  its command has no host to key on until minted — a network call routing must not make. */
    private fun panelRefusesSegments(channel: ChannelEntity, source: SourceEntity?): Boolean =
        !host.needsResolve(source) && LiveStreamQuirks.refusesSegments(channel.playStreamUrl(source))

    private fun isRequestRefusal(reason: String): Boolean =
        PlayerErrors.httpStatusIn(reason)?.let { LiveStreamQuirks.isRequestRefusal(it) } == true

    /**
     * Install the Stalker reconnect link on both engines while a Stalker channel plays, so a mid-session
     * stream death mints a fresh `create_link` instead of looping on the expired one; cleared otherwise.
     */
    private fun setReconnect(channel: ChannelEntity?, source: SourceEntity?) {
        if (channel == null || source == null || !host.needsResolve(source)) {
            engines.setReconnectProvider(null)
            return
        }
        val owner = generation
        engines.setReconnectProvider(
            ReconnectUrlProvider {
                if (owner != generation) return@ReconnectUrlProvider null
                val url = runCatching { host.resolve(source, channel.streamUrl) }
                    .onFailure { if (it is CancellationException) throw it; android.util.Log.w(ENGINE_TAG, "stalker reconnect re-resolve failed '${channel.name}'", it) }
                    .getOrNull()
                currentCoroutineContext().ensureActive()
                if (owner == generation) url else null
            },
        )
    }

    private fun request(channel: ChannelEntity, source: SourceEntity?, manualTs: Boolean = this.manualTs, options: tv.own.owntv.core.settings.ChannelPlaybackOptions? = channelOptions): LiveRequest {
        val reserve = LiveReserveResolver.sourceOverride(source)
        return LiveRequest(
            meta = host.meta(channel),
            userAgent = source?.userAgent,
            prerollSecs = options?.prerollSecs ?: source?.livePrerollSecs?.takeIf { it >= 0 },
            liveBuffer = options?.latencySecs?.let { LiveBuffer.Override(it) } ?: source?.liveLatencyMode?.let { mode ->
                LiveBuffer.Override(LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode), source.liveLatencyCustomSecs))
            },
            httpHeaders = channel.httpHeaders,
            drmConfig = channel.drmConfig,
            manifestType = channel.manifestType,
            directSource = channel.directSource,
            choiceId = choiceId,
            reserveBuffer = options?.reserveSecs?.let { LiveBuffer.Override(it) } ?: reserve?.let { LiveBuffer.Override(it.seconds) },
            reserveExtraSecs = options?.extraSecs ?: reserve?.extraSecs,
            manualTs = manualTs,
            strictHls = hlsOnly,
            channelOptions = options,
        )
    }

    /** Routing decisions go to logcat unconditionally (`adb logcat -s LiveEngine`) and to the
     *  diagnostics ring. Channel names only — never a stream URL. */
    private fun engineLog(message: String) {
        android.util.Log.i(ENGINE_TAG, message)
        LiveDiagnosticsLog.event("engine: $message")
    }

    /**
     * The [Host] both apps use: pins from [ForceMpvStore], the settings store, the playlist row, and
     * Stalker minting through [StreamUrlResolver]. [label] is the line under the channel name (the
     * television's "#123"); [sourceOf] lets the television read its warm playlist map.
     */
    class CoreHost(
        private val context: Context,
        private val settings: SettingsRepository,
        private val sourceDao: SourceDao,
        private val resolver: StreamUrlResolver,
        private val forceMpvStore: ForceMpvStore,
        private val label: (ChannelEntity) -> String? = { null },
        private val sourceLookup: (suspend (Long) -> SourceEntity?)? = null,
        private val engineStarted: () -> Unit = {},
        private val backToLiveEdge: () -> Unit = {},
        private val opened: (ChannelEntity) -> Unit = {},
    ) : Host {
        override suspend fun sourceOf(sourceId: Long): SourceEntity? =
            sourceLookup?.invoke(sourceId) ?: sourceDao.getById(sourceId)

        override fun needsResolve(source: SourceEntity?): Boolean = resolver.needsResolve(source)

        override suspend fun resolve(source: SourceEntity, cmd: String): String? =
            runCatching { resolver.resolve(source, cmd) }
                .onFailure { if (it is CancellationException) throw it; android.util.Log.w(ENGINE_TAG, "stalker resolve failed", it) }
                .getOrNull()

        override suspend fun enginePin(channel: ChannelEntity): Boolean? {
            val key = pinKey(channel)
            val mpv = forceMpvStore.urls.first()
            val exo = forceMpvStore.exoUrls.first()
            return when {
                key != null && key in mpv -> true
                key != null && key in exo -> false
                channel.streamUrl in mpv -> true
                channel.streamUrl in exo -> false
                else -> null
            }
        }

        override suspend fun pin(channel: ChannelEntity, onMpv: Boolean) {
            // The stable key, with any legacy URL-keyed entry cleared so an older pin cannot contradict it.
            val key = pinKey(channel)
            forceMpvStore.pin(key ?: channel.streamUrl, onMpv)
            if (key != null) forceMpvStore.forget(channel.streamUrl)
        }

        // Both wait for the settings snapshot (S15), so a tune started at a cold start — Last-channel
        // autoplay — is routed and budgeted by the stored settings, never by defaults.
        override suspend fun hlsOnly(): Boolean = PlaybackSettings.await(settings).liveHlsOnly
        override suspend fun channelOptions(channel: ChannelEntity) = settings.channelPlaybackConfigs.first().firstOrNull { it.channel.matches(channel) }?.options
        override suspend fun manualTs(channel: ChannelEntity): Boolean = settings.manualTsChannels.first().any { it.matches(channel) }

        override suspend fun globalPreference(): EnginePreference =
            PlaybackSettings.await(settings).liveEnginePreference

        override suspend fun globalBudgetSecs(): Int = PlaybackSettings.await(settings).liveTuneTimeoutSecs

        override fun meta(channel: ChannelEntity): MediaMeta = MediaMeta(
            title = channel.name,
            subtitle = label(channel),
            logoUrl = channel.displayLogoUrl,
            contentKey = pinKey(channel),
        )

        override fun recordLadderEvent(onExo: Boolean, reason: PlayerFailureReason, detail: String) =
            PlaybackErrorLog.event(
                context = context,
                engine = if (onExo) "ExoPlayer" else "mpv",
                live = true,
                reason = reason,
                detail = detail,
            )

        override fun onOpened(channel: ChannelEntity) = opened(channel)
        override fun onEngineStarted() = engineStarted()
        override fun onBackToLiveEdge() = backToLiveEdge()

        private fun pinKey(channel: ChannelEntity): String? =
            enginePinKey(channel.sourceId, tv.own.owntv.core.model.MediaType.LIVE.name, channel.remoteId)
    }

    companion object {
        /** Tag for the engine decisions, so a support log can be filtered to just them. */
        const val ENGINE_TAG = "LiveEngine"

        /**
         * How long mpv gets to produce a picture before the ladder moves on. Looser than ExoPlayer's:
         * mpv walks its own internal retry and format ladder first.
         */
        const val MPV_OPEN_TIMEOUT_MS = 35_000L
    }
}

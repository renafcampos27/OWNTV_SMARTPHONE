package tv.own.owntv.player

import tv.own.owntv.core.settings.LiveBuffer
import tv.own.owntv.core.stalker.ReconnectUrlProvider

/** Everything a live tune hands an engine besides the URL. */
data class LiveRequest(
    val meta: MediaMeta,
    val userAgent: String?,
    val prerollSecs: Int?,
    val liveBuffer: LiveBuffer.Override?,
    val httpHeaders: String?,
    val drmConfig: String?,
    val manifestType: String?,
    val directSource: String?,
    val choiceId: Long? = null,
    val reserveBuffer: LiveBuffer.Override? = null,
    val reserveExtraSecs: Int? = null,
    val manualTs: Boolean = false,
    val strictHls: Boolean = false,
    val channelOptions: tv.own.owntv.core.settings.ChannelPlaybackOptions? = null,
)

/** What mpv did with a channel it was just given: confirmed native playback, or an error. */
data class MpvOutcome(val opened: Boolean, val error: String?)

/**
 * The two live engines, as [LiveTuneController] drives them.
 *
 * Exactly the operations the controller uses and nothing more. It exists so the controller's
 * sequencing — superseding tunes, the ladder, the give-up alarm — can be unit-tested against a fake;
 * production is [EnginePair], a thin pass-through to the real engines.
 */
interface LiveEngines {
    /** What ExoPlayer holds right now, or null. */
    val exoUrl: String?
    val exoIsHls: Boolean
    val exoManualTs: Boolean get() = false
    val exoChannelOptions: tv.own.owntv.core.settings.ChannelPlaybackOptions? get() = null
    val exoCanPromote: Boolean get() = !exoFailed
    suspend fun awaitSettings() {}
    fun exoPromote(choiceId: Long?) = exoSetMuted(false)
    /** Current source has really rendered/advanced and is still playing without an engine error. */
    val exoPlaybackConfirmed: Boolean get() = false
    val mpvPlaybackConfirmed: Boolean get() = false
    val exoPlaybackRequested: Boolean get() = true
    val mpvPlaybackRequested: Boolean get() = true
    val mpvPlaybackRevision: Long get() = 0L
    val exoFailed: Boolean
    /** Whether mpv holds a stream (full-screen live, a film, a catch-up). */
    val mpvHasStream: Boolean
    fun startupProgress(): LiveStartupProgress? = null
    fun providerWait(): LiveProviderWait? = null

    fun exoPlay(url: String, muted: Boolean, request: LiveRequest)
    fun exoSetMuted(muted: Boolean)
    suspend fun exoStopAndAwaitDrain(): Boolean { exoStop(); return true }
    fun exoStop()
    fun exoCancelResolver() {}
    fun exoReleaseUhdDecoder()
    fun exoAbandon(reason: String)

    /** Run [LiveExoWatchdog] on ExoPlayer until the outcome is settled. */
    suspend fun watchExo(
        channelName: String,
        stillOurs: () -> Boolean,
        handOver: suspend (String) -> Unit,
        onOpened: () -> Unit,
        postponeDeadline: (Long) -> Unit,
        log: (String) -> Unit,
    )

    fun mpvPlay(url: String, request: LiveRequest)
    fun mpvStop()
    suspend fun mpvStopAndAwaitRelease()
    fun mpvAbandon(reason: String)

    /**
     * Wait up to [timeoutMs] for mpv to open or fail; null when it did neither.
     *
     * Native playback must belong to this load. A file opening, dimensions or `isPlaying` alone
     * are insufficient; this also covers audio-only channels without requiring a video frame.
     */
    suspend fun awaitMpvOutcome(timeoutMs: Long): MpvOutcome?

    /** The Stalker re-resolve hook, installed on both engines, or cleared with null. */
    fun setReconnectProvider(provider: ReconnectUrlProvider?)
}

/** Start [url] on a live ExoPlayer engine with everything [request] carries — a tune, or a Multiview tile. */
internal fun LivePreviewEngine.play(url: String, muted: Boolean, request: LiveRequest) = play(
    url,
    muted = muted,
    meta = request.meta,
    userAgent = request.userAgent,
    prerollSecsOverride = request.prerollSecs,
    liveBufferOverride = request.liveBuffer,
    httpHeaders = request.httpHeaders,
    drmConfig = request.drmConfig,
    manifestType = request.manifestType,
    directSource = request.directSource,
    choiceId = request.choiceId,
    reserveBufferOverride = request.reserveBuffer,
    reserveExtraSecsOverride = request.reserveExtraSecs,
    manualTs = request.manualTs,
    channelOptions = request.channelOptions,
)

/** The real engines: the live ExoPlayer engine and the process-wide mpv player. */
class EnginePair(private val exo: LivePreviewEngine, private val mpv: OwnTVPlayer) : LiveEngines {
    override fun startupProgress(): LiveStartupProgress? =
        if (exo.currentUrl != null) exo.startupProgress() else mpv.startupProgress()
    override fun providerWait(): LiveProviderWait? = exo.providerWait()
    override suspend fun awaitSettings() { exo.awaitPlaybackSettings(); mpv.awaitPlaybackSettings() }
    override val exoChannelOptions get() = exo.channelPlaybackOptions
    override val exoManualTs: Boolean get() = exo.manualTsOverride
    override val exoCanPromote: Boolean get() = exo.canPromoteCurrentSource
    override fun exoPromote(choiceId: Long?) { if (choiceId != null) exo.promoteToFullscreen(choiceId) else exo.setMuted(false) }
    override val exoUrl: String? get() = exo.currentUrl
    override val exoIsHls: Boolean get() = exo.isHlsStream
    override val exoPlaybackConfirmed: Boolean get() = exo.hasConfirmedPlayback
    override val mpvPlaybackConfirmed: Boolean get() = mpv.livePlaybackReady.value && mpv.isPlaying.value && !mpv.buffering.value && mpv.error.value == null
    override val exoPlaybackRequested get() = exo.playbackRequested.value
    override val mpvPlaybackRequested get() = mpv.playbackRequested.value
    override val mpvPlaybackRevision get() = mpv.playbackRequestRevision.value
    override suspend fun exoStopAndAwaitDrain() = exo.stopAndAwaitDrain()
    override val exoFailed: Boolean get() = exo.state.value == LivePreviewEngine.State.ERROR
    override val mpvHasStream: Boolean get() = mpv.hasActiveStream

    override fun exoPlay(url: String, muted: Boolean, request: LiveRequest) = exo.play(url, muted, request)

    override fun exoSetMuted(muted: Boolean) = exo.setMuted(muted)
    override fun exoStop() = exo.stop()
    override fun exoCancelResolver() = exo.cancelCurrentResolution()
    override fun exoReleaseUhdDecoder() = exo.releaseDecoderForUhd()
    override fun exoAbandon(reason: String) { exo.abandon(reason) }

    override suspend fun watchExo(
        channelName: String,
        stillOurs: () -> Boolean,
        handOver: suspend (String) -> Unit,
        onOpened: () -> Unit,
        postponeDeadline: (Long) -> Unit,
        log: (String) -> Unit,
    ) {
        LiveExoWatchdog(exo, stillOurs, handOver, onOpened, postponeDeadline, log).watch(channelName)
    }

    override fun mpvPlay(url: String, request: LiveRequest) = mpv.play(
        url = url,
        title = request.meta.title,
        subtitle = request.meta.subtitle,
        logoUrl = request.meta.logoUrl,
        isLive = true,
        muted = false,
        userAgent = request.userAgent,
        httpHeaders = request.httpHeaders,
        // The same stable key ExoPlayer files this channel under, so a zoom or volume set on one
        // engine is not forgotten when the channel falls back to the other.
        contentKey = request.meta.contentKey,
        livePrerollSecsOverride = request.prerollSecs,
        liveBufferOverride = request.liveBuffer,
        reserveBufferOverride = request.reserveBuffer,
        reserveExtraSecsOverride = request.reserveExtraSecs,
        audioDelayMsOverride = request.channelOptions?.audioDelayMs,
        strictHlsOverride = request.strictHls,
    )

    override fun mpvStop() = mpv.stop()
    override suspend fun mpvStopAndAwaitRelease() { mpv.stopAndAwaitRelease() }
    override fun mpvAbandon(reason: String) = mpv.abandonLive(reason)

    override suspend fun awaitMpvOutcome(timeoutMs: Long): MpvOutcome? {
        val budget = LiveWaitBudget(android.os.SystemClock.elapsedRealtime(), timeoutMs)
        while (true) {
            if (mpv.playbackRequested.value) {
                mpv.error.value?.let { return MpvOutcome(false, it.toString()) }
                if (mpv.livePlaybackReady.value) return MpvOutcome(true, null)
            }
            val left = budget.remaining(android.os.SystemClock.elapsedRealtime(), null, null, mpv.playbackRequested.value)
            if (left <= 0L && mpv.playbackRequested.value) return null
            kotlinx.coroutines.delay(100L)
        }
    }

    override fun setReconnectProvider(provider: ReconnectUrlProvider?) {
        exo.reconnectUrlProvider = provider
        mpv.reconnectUrlProvider = provider
    }
}

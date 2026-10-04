package tv.own.owntv.mobile.cast

import androidx.core.net.toUri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.player.MediaMeta
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.player.PlaybackFailure
import tv.own.owntv.player.TrackOption
import tv.own.owntv.player.ZoomMode

/**
 * The receiver, wearing the same interface as mpv.
 *
 * This is what makes casting cost the rest of the app almost nothing: [tv.own.owntv.player.PlaybackSession]
 * takes a [PlaybackEngine], so attaching this one hands the lock screen, the audio-focus rules and the
 * media buttons to the television without a second copy of any of it.
 *
 * **Most of the interface is a no-op here, and that is the honest answer rather than a gap.** The
 * receiver decodes the stream itself, so there is no track list of ours to offer, no zoom to apply,
 * no subtitle to shift and no engine to swap — every one of those is a property of a decoder running
 * on this phone, and while casting there isn't one.
 */
class CastPlaybackEngine(private val session: CastSession) : PlaybackEngine {

    private val _isPlaying = MutableStateFlow(false)
    private val _buffering = MutableStateFlow(false)
    private val _error = MutableStateFlow<PlaybackFailure?>(null)
    private val _position = MutableStateFlow(0L)
    private val _duration = MutableStateFlow(0L)
    private val _volume = MutableStateFlow(0)
    private val _meta = MutableStateFlow(MediaMeta())
    private var live = false

    /** Kept so [retry] has something to send again, and so a reconnect reloads the same stream. */
    private var request: CastRequest? = null

    override val isPlaying: StateFlow<Boolean> = _isPlaying
    override val buffering: StateFlow<Boolean> = _buffering
    override val error: StateFlow<PlaybackFailure?> = _error
    override val position: StateFlow<Long> = _position
    override val duration: StateFlow<Long> = _duration
    override val volume: StateFlow<Int> = _volume
    override val currentMeta: StateFlow<MediaMeta> = _meta
    override val isLiveContent: Boolean get() = live

    /** The whole point of this engine: the sound is in the other room, not in this phone. */
    override val playsLocally: Boolean = false

    override val videoRes: StateFlow<String?> = MutableStateFlow(null)
    override val zoomMode: StateFlow<ZoomMode> = MutableStateFlow(ZoomMode.FIT)
    override val audioCount: StateFlow<Int> = MutableStateFlow(0)
    override val subCount: StateFlow<Int> = MutableStateFlow(0)
    override val engineChip: StateFlow<String?> = MutableStateFlow(ENGINE_CHIP)

    private val client: RemoteMediaClient? get() = runCatching { session.remoteMediaClient }.getOrNull()

    private val callback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = sync()
        override fun onMetadataUpdated() = sync()
    }

    private val progress = RemoteMediaClient.ProgressListener { positionMs, durationMs ->
        _position.value = positionMs.coerceAtLeast(0L)
        _duration.value = durationMs.coerceAtLeast(0L)
    }

    /** Send [request] to the receiver, or refuse it outright when nothing could ever play it. */
    fun load(request: CastRequest) {
        this.request = request
        live = request.isLive
        _meta.value = MediaMeta(
            title = request.title,
            subtitle = request.subtitle,
            logoUrl = request.logoUrl,
        )
        _position.value = request.startPositionMs
        _duration.value = 0L
        if (!request.castable()) {
            refuse()
            return
        }
        val client = client ?: run { refuse(); return }
        _error.value = null
        _buffering.value = true
        client.registerCallback(callback)
        client.addProgressListener(progress, PROGRESS_MS)
        val metadata = MediaMetadata(
            if (request.isLive) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE,
        ).apply {
            putString(MediaMetadata.KEY_TITLE, request.title)
            request.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            request.logoUrl?.takeIf { it.isNotBlank() }?.let { addImage(WebImage(it.toUri())) }
        }
        val info = MediaInfo.Builder(request.url)
            .setStreamType(if (request.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(request.contentType())
            .setMetadata(metadata)
            .build()
        val load = MediaLoadRequestData.Builder()
            .setMediaInfo(info)
            .setAutoplay(true)
            .setCurrentTime(if (request.isLive) 0L else request.startPositionMs)
            .build()
        runCatching {
            client.load(load).setResultCallback { result ->
                if (!result.status.isSuccess) refuse()
            }
        }.onFailure { refuse() }
    }

    /**
     * The receiver said no. There is nothing to fall back to — no ladder, no second engine — so this
     * is the end of the attempt, and the user is told so in their own language through core's
     * existing player wording.
     */
    private fun refuse() {
        _buffering.value = false
        _isPlaying.value = false
        _error.value = PlaybackFailure.CastUnsupported
    }

    private fun sync() {
        val client = client ?: return
        _isPlaying.value = runCatching { client.isPlaying }.getOrDefault(false)
        _buffering.value = runCatching { client.isBuffering }.getOrDefault(false)
        _position.value = runCatching { client.approximateStreamPosition }.getOrDefault(0L).coerceAtLeast(0L)
        _duration.value = runCatching { client.streamDuration }.getOrDefault(0L).coerceAtLeast(0L)
        _volume.value = runCatching { (session.volume * 100).toInt() }.getOrDefault(_volume.value)
        // idleReason only means anything while the receiver is idle; at any other moment it is
        // whatever the last idle was, and reading it would resurrect an error the retry just cleared.
        val idle = runCatching { client.mediaStatus?.playerState }.getOrNull() == MediaStatus.PLAYER_STATE_IDLE
        if (idle && runCatching { client.idleReason }.getOrNull() == MediaStatus.IDLE_REASON_ERROR) refuse()
    }

    /** Stop listening. The session itself is the controller's to end. */
    fun detach() {
        runCatching { client?.unregisterCallback(callback) }
        runCatching { client?.removeProgressListener(progress) }
    }

    /** Take the stream off the television — the user stopped playing, not stopped casting. */
    fun stopRemote() {
        runCatching { client?.stop() }
    }

    override fun togglePlayPause() {
        val client = client ?: return
        runCatching { if (client.isPlaying) client.pause() else client.play() }
    }

    override fun seekBy(deltaMs: Long) {
        if (live) return
        val client = client ?: return
        val target = (_position.value + deltaMs).coerceIn(0L, _duration.value.coerceAtLeast(0L))
        runCatching { client.seek(MediaSeekOptions.Builder().setPosition(target).build()) }
    }

    /**
     * The television's own volume, not this phone's. Casting moves the sound to the other room, so
     * the slider that used to move mpv's level has to move the receiver's or it does nothing at all.
     */
    override fun adjustVolume(delta: Int) {
        val target = ((_volume.value + delta).coerceIn(0, 100)) / 100.0
        runCatching { session.volume = target }
        _volume.value = (target * 100).toInt()
    }

    override fun toggleMute() {
        runCatching { session.isMute = !session.isMute }
    }

    override fun retry() {
        request?.let { load(it) }
    }

    // Nothing below exists on a receiver: no decoder of ours is running, so there is no track list to
    // choose from and no picture on this device to scale.
    override fun setZoomMode(mode: ZoomMode) {}
    override fun selectAudio(id: Int) {}
    override fun selectSubtitle(id: Int) {}
    override fun disableSubtitles() {}
    override fun audioTracks(): List<TrackOption> = emptyList()
    override fun textTracks(): List<TrackOption> = emptyList()

    private companion object {
        /** Matches the local engines' chips ("MPV", "EXO"), so the top bar reads the same way. */
        const val ENGINE_CHIP = "CAST"
        const val PROGRESS_MS = 1_000L
    }
}

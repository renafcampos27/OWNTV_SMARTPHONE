package tv.own.owntv.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** One owner for audio focus and platform transport commands across all local engines.
 * Internal gain is fixed at unity. Interruptions pause; an owned temporary pause can resume
 * only if no later user command or engine change superseded it.
 */
class PlaybackSession(
    private val context: Context,
    private val focusPolicy: FocusPolicy = FocusPolicy.DUCK,
    private val pauseWhenOutputDisconnects: Boolean = false,
) {

    /** Kept for host compatibility. Both policies pause now that gain is owned by the system. */
    enum class FocusPolicy {
        /** Legacy TV value; interruptions use a temporary owned pause. */
        DUCK,

        /** Pause, and resume on the next gain unless the user paused by hand — a phone. */
        PAUSE,
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    private val _active = kotlinx.coroutines.flow.MutableStateFlow(false)
    val active: kotlinx.coroutines.flow.StateFlow<Boolean> = _active
    private var engine: PlaybackEngine? = null
    private var collectJob: Job? = null
    private var session: MediaSession? = null

    private enum class FocusState { NONE, GRANTED, DELAYED, TRANSIENT, FAILED }
    private var focusEpoch = 0L
    private var focusRequest: AudioFocusRequest? = null
    private var focusState = FocusState.NONE
    private var lastFocusAttempt: Pair<PlaybackEngine, Long>? = null
    private data class ResumeTicket(val owner: PlaybackEngine, val revision: Long)
    private var resumeTicket: ResumeTicket? = null

    /**
     * Make [engine] the one this session represents, or `null` when nothing is playing any more (the
     * player closed). Safe to call repeatedly with the same engine.
     */
    fun attach(engine: PlaybackEngine?) {
        if (this.engine === engine) return
        collectJob?.cancel()
        // A different engine (or none) starts a fresh session as far as controllers are concerned, so the
        // next publish must send the metadata even if the title happens to match.
        lastMetaKey = null
        resumeTicket = null
        abandonFocus()
        unregisterNoisy()
        unregisterOutputChanges()
        this.engine = engine
        _active.value = engine != null
        if (engine == null) {
            abandonFocus()
            unregisterNoisy()
            unregisterOutputChanges()
            resumeTicket = null
            runCatching { session?.apply { isActive = false; release() } }
            session = null
            return
        }
        // A dead or refusing MediaSession must never take the player down with it: the session is a
        // convenience for other controllers on the TV, the video is the point. Every other system call
        // in this class is already guarded; these were the last three that were not.
        val s = runCatching { session ?: createSession().also { session = it } }.getOrNull()
        runCatching { s?.isActive = true }
        registerNoisy()
        registerOutputChanges()
        val playbackState = combine(
            engine.isPlaying,
            engine.currentMeta,
            engine.position,
            engine.duration,
        ) { playing, meta, position, duration -> State(playing, meta, position, duration, engine.isLiveContent) }
        val request = combine(engine.playbackRequested, engine.playbackRequestRevision) { requested, revision -> requested to revision }
        collectJob = combine(playbackState, engine.buffering, engine.error, request) { state, buffering, error, request ->
            state.copy(buffering = buffering, failed = error != null, requested = request.first, requestRevision = request.second)
        }.onEach(::publish)
            .launchIn(scope)
    }

    /**
     * The platform session's token, or null while nothing is attached.
     *
     * A host that shows a media notification needs it: hanging `MediaStyle` on this token is what puts
     * the transport controls on the lockscreen and lets the system draw the seek bar from the state
     * this class already publishes. The television has no such notification and never reads it.
     */
    val token: MediaSession.Token?
        get() = runCatching { session?.sessionToken }.getOrNull()

    /** The parts of [State] that actually reach `MediaMetadata`; see [publish]. */
    private data class MetaKey(val title: String, val subtitle: String, val durationMs: Long)

    private var lastMetaKey: MetaKey? = null

    private data class State(
        val playing: Boolean,
        val meta: MediaMeta,
        val positionMs: Long,
        val durationMs: Long,
        val live: Boolean,
        val buffering: Boolean = false,
        val failed: Boolean = false,
        val requested: Boolean = false,
        val requestRevision: Long = 0L,
    )

    private fun publish(state: State) {
        // Buffering/suppression is not a user pause. Retain the request that owns playback.
        resumeTicket?.let { if (it.owner !== engine || it.revision != engine?.playbackRequestRevision?.value || engine?.error?.value != null) resumeTicket = null }
        if (shouldHoldPlaybackFocus(state.playing, state.buffering, engine?.playbackRequested?.value == true, engine?.error?.value != null, resumeTicket != null)) requestFocus()
        else abandonFocus()
        val s = session ?: return
        runCatching {
            // Metadata only when it actually changed (A-F10/F-F9). This is driven by a combine() that also
            // carries the position, so it fires on every position tick — but the title, subtitle and
            // duration only change when the ITEM does. Rebuilding and re-publishing MediaMetadata dozens
            // of times a second was pure waste, and every controller on the TV had to process each one.
            // The playback state below still updates every tick: a controller needs the moving position.
            val metaKey = MetaKey(
                title = state.meta.title.orEmpty(),
                subtitle = state.meta.subtitle.orEmpty(),
                // Live has no meaningful duration; -1 tells a controller "not seekable/unknown".
                durationMs = if (state.live) -1L else state.durationMs,
            )
            if (metaKey != lastMetaKey) {
                lastMetaKey = metaKey
                s.setMetadata(
                    MediaMetadata.Builder()
                        .putString(MediaMetadata.METADATA_KEY_TITLE, metaKey.title)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, metaKey.subtitle)
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, metaKey.durationMs)
                        .build(),
                )
            }
            var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
            if (!state.live) {
                actions = actions or PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
            }
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(actions)
                    .setState(
                        if (state.playing) PlaybackState.STATE_PLAYING else if (state.buffering && state.requested && !state.failed) PlaybackState.STATE_BUFFERING else PlaybackState.STATE_PAUSED,
                        state.positionMs,
                        if (state.playing) (engine?.speed?.value ?: 1.0).toFloat() else 0f,
                    )
                    .build(),
            )
        }
    }

    private fun createSession(): MediaSession = MediaSession(context, SESSION_TAG).apply {
        setCallback(object : MediaSession.Callback() {
            // Either transport button is the user speaking for themselves, so any resume this class
            // still owed is cancelled: their choice is newer than the interruption's.
            override fun onPlay() = withEngine { resumeTicket = null; it.play() }
            override fun onPause() = withEngine { resumeTicket = null; it.pause() }
            // Nothing here may tear the player down — this session doesn't own the UI. "Stop" from a
            // system control therefore means "silence it", which is a pause the user can undo.
            override fun onStop() = withEngine { resumeTicket = null; it.pause() }
            override fun onSeekTo(pos: Long) = withEngine {
                if (!it.isLiveContent) it.seekBy(pos - it.position.value)
            }
            // Settings → Seek step, the same value the on-screen buttons use: a Bluetooth remote or the
            // system media notification must not move by a different amount than the HUD does.
            override fun onFastForward() = withEngine {
                if (!it.isLiveContent) it.seekBy(it.seekStepMs.value)
            }
            override fun onRewind() = withEngine {
                if (!it.isLiveContent) it.seekBy(-it.seekStepMs.value)
            }
            override fun onSkipToNext() = withEngine { it.next() }
            override fun onSkipToPrevious() = withEngine { it.previous() }
        })
    }

    /** Callbacks arrive on a binder thread; every engine here is main-thread-only. */
    private fun withEngine(block: (PlaybackEngine) -> Unit) {
        val e = engine ?: return
        scope.launch { if (engine === e) runCatching { block(e) } }
    }

    // --- Audio focus ------------------------------------------------------------------------------

    private fun onFocusChange(owner: PlaybackEngine, epoch: Long, change: Int) {
        if (engine !== owner || epoch != focusEpoch) return
        LiveDiagnosticsLog.event("audio_focus change=$change policy=pause")
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusState = FocusState.GRANTED
                resumeAfterInterruption()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusState = FocusState.TRANSIENT
                pauseForInterruption()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeTicket = null
                withEngine { it.pause(); abandonFocus() }
            }
        }
    }

    private fun pauseForInterruption() {
        withEngine {
            if (!it.playbackRequested.value) return@withEngine
            it.pause()
            resumeTicket = ResumeTicket(it, it.playbackRequestRevision.value)
        }
    }

    private fun resumeAfterInterruption() {
        val ticket = resumeTicket ?: return
        resumeTicket = null
        withEngine {
            if (mayResumeOwnedPause(it === ticket.owner, ticket.revision, it.playbackRequestRevision.value, it.error.value != null)) it.play()
            else if (!it.playbackRequested.value) abandonFocus()
        }
    }

    // --- Headphones and Bluetooth -----------------------------------------------------------------

    /**
     * Unplugging headphones or walking out of Bluetooth range: pause, and do **not** arm a resume.
     * Plugging back in must not blast a film out of a pocket, so this deliberately does not go through
     * [pauseForInterruption].
     */
    private val outputChanges = object : android.media.AudioDeviceCallback() {
        override fun onAudioDevicesAdded(devices: Array<out android.media.AudioDeviceInfo>) = logOutputs("added")
        override fun onAudioDevicesRemoved(devices: Array<out android.media.AudioDeviceInfo>) = logOutputs("removed")
    }
    private var outputsRegistered = false
    private fun logOutputs(event: String) {
        if (engine == null || engine?.playsLocally == false) return
        val types = runCatching { audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }.distinct() }.getOrDefault(emptyList())
        // Available outputs are not proof of the AudioTrack's active hardware route.
        LiveDiagnosticsLog.event("audio_outputs event=$event availableTypes=$types")
    }
    private fun registerOutputChanges() {
        if (outputsRegistered || engine?.playsLocally == false) return
        outputsRegistered = runCatching {
            audioManager.registerAudioDeviceCallback(outputChanges, android.os.Handler(android.os.Looper.getMainLooper()))
        }.isSuccess
    }
    private fun unregisterOutputChanges() {
        if (!outputsRegistered) return
        outputsRegistered = false
        runCatching { audioManager.unregisterAudioDeviceCallback(outputChanges) }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            LiveDiagnosticsLog.event("audio_output_disconnected policy=pause")
            resumeTicket = null
            withEngine { it.pause() }
        }
    }

    private var noisyRegistered = false

    private fun registerNoisy() {
        // Same reason as in requestFocus: this device's headphone jack has nothing to do with a
        // stream playing on a television.
        if (!pauseWhenOutputDisconnects || noisyRegistered || engine?.playsLocally == false) return
        noisyRegistered = runCatching {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        noisyRegistered = false
        runCatching { context.unregisterReceiver(noisyReceiver) }
    }

    private fun newFocusRequest(owner: PlaybackEngine): AudioFocusRequest {
        val epoch = ++focusEpoch
        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(
                { change -> onFocusChange(owner, epoch, change) },
                android.os.Handler(android.os.Looper.getMainLooper()),
            )
            .build()
    }

    private fun requestFocus() {
        val e = engine ?: return
        if (!e.playsLocally || focusState == FocusState.GRANTED) return
        if (focusState == FocusState.DELAYED || focusState == FocusState.TRANSIENT) {
            // A later Play command still cannot render before the platform grants focus.
            if (e.playbackRequested.value) pauseForInterruption()
            return
        }
        val attempt = e to e.playbackRequestRevision.value
        if (lastFocusAttempt == attempt) return // a refusal is retried only by a later explicit command
        lastFocusAttempt = attempt
        val request = newFocusRequest(e).also { focusRequest = it }
        val result = runCatching { audioManager.requestAudioFocus(request) }
            .getOrDefault(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        focusState = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> FocusState.GRANTED
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> FocusState.DELAYED
            else -> FocusState.FAILED
        }
        LiveDiagnosticsLog.event("audio_focus_request result=$focusState revision=${attempt.second}")
        when (focusState) {
            FocusState.DELAYED -> pauseForInterruption()
            FocusState.FAILED -> withEngine { resumeTicket = null; it.pause() }
            else -> Unit
        }
    }

    private fun abandonFocus() {
        val held = focusState == FocusState.GRANTED || focusState == FocusState.DELAYED || focusState == FocusState.TRANSIENT
        ++focusEpoch
        val request = focusRequest
        focusRequest = null
        focusState = FocusState.NONE
        lastFocusAttempt = null
        if (held && request != null) runCatching { audioManager.abandonAudioFocusRequest(request) }
        if (held) LiveDiagnosticsLog.event("audio_focus_abandoned")
    }

    private companion object {
        const val SESSION_TAG = "OwnTV"
    }
}

/** Pure focus decision shared by the session and interruption regression tests. */
internal fun shouldHoldPlaybackFocus(playing: Boolean, buffering: Boolean, requested: Boolean, failed: Boolean, pausedBySession: Boolean): Boolean =
    pausedBySession || (!failed && requested)

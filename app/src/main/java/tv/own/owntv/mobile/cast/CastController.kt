package tv.own.owntv.mobile.cast

import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.own.owntv.mobile.playback.PlaybackService
import tv.own.owntv.player.PlaybackSession

/**
 * Whoever is playing something, from the cast path's point of view.
 *
 * Both tuners implement it. The controller cannot ask "what is playing" of the app in general —
 * there are two tuners and only one of them ever has the stream — so the one that started it says so
 * by offering, and stays reachable for the two moments that matter: the receiver taking over, and
 * the receiver giving it back.
 */
interface CastHandoff {
    /** Stop local playback because the television is taking it, and say where it had got to. */
    fun releaseToCast(): Long

    /** Play the same thing on this phone again, from [positionMs]. */
    fun resumeFromCast(positionMs: Long)
}

/**
 * Casting, start to finish.
 *
 * It owns the Cast session, the remote engine while one exists, and the two handovers in between. The
 * rest of the app talks to it through [offer] — "here is what is about to play; did you take it?" —
 * which is the one question a tuner has to ask before starting its own engine.
 *
 * **Google Play services may not be there at all** (an emulator image without it, a de-Googled
 * phone). [CastContext.getSharedInstance] throws in that case, so it is caught: casting simply never
 * becomes available and every other path behaves as though this class did not exist.
 */
class CastController(
    private val context: Context,
    private val session: PlaybackSession,
) {

    private val castContext: CastContext? =
        runCatching { CastContext.getSharedInstance(context) }.getOrNull()

    private val _engine = MutableStateFlow<CastPlaybackEngine?>(null)

    /** The receiver as an engine while one is playing, null the rest of the time. */
    val engine: StateFlow<CastPlaybackEngine?> = _engine

    private val _deviceName = MutableStateFlow<String?>(null)

    /** What the receiver calls itself — "Living Room TV" — for the message over the picture. */
    val deviceName: StateFlow<String?> = _deviceName

    private var connected: CastSession? = null
    private var owner: CastHandoff? = null
    private var current: CastRequest? = null

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = takeOver(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = takeOver(s)
        override fun onSessionEnded(s: CastSession, error: Int) = handBack()

        // The route went away — the receiver was unplugged, or the phone left the Wi-Fi. Treated
        // exactly like ending the session on purpose: what must not happen is the stream vanishing
        // with the network and the phone showing a still frame of nothing.
        override fun onSessionSuspended(s: CastSession, reason: Int) = handBack()
        override fun onSessionStartFailed(s: CastSession, error: Int) = handBack()
        override fun onSessionResumeFailed(s: CastSession, error: Int) = handBack()

        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionResuming(s: CastSession, sessionId: String) {}
    }

    init {
        runCatching {
            castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
            // A session can already be up when this class is built — the app was reopened while the
            // television was still playing. Recorded, so the next offer() goes straight to it.
            connected = castContext?.sessionManager?.currentCastSession?.takeIf { it.isConnected }
            _deviceName.value = connected?.castDevice?.friendlyName
        }
    }

    /**
     * The stream the caller is about to start. **True means the television took it and the local
     * engine must not be started**; false means play it here as usual.
     *
     * A stream the receiver cannot play still returns true: it was handed over, and the refusal is
     * shown as a player error rather than silently played on the phone instead. Casting a channel and
     * getting it on the handset would be the confusing outcome, not the helpful one.
     */
    fun offer(owner: CastHandoff, request: CastRequest): Boolean {
        this.owner = owner
        current = request
        val s = connected ?: return false
        val existing = _engine.value
        if (existing != null) {
            existing.load(request)
        } else {
            startRemote(s, request)
        }
        return true
    }

    /** The caller stopped playing altogether. The device stays connected, ready for the next thing. */
    fun release(owner: CastHandoff) {
        if (this.owner !== owner) return
        this.owner = null
        current = null
        _engine.value?.let { it.stopRemote(); it.detach() }
        _engine.value = null
    }

    private fun takeOver(s: CastSession) {
        connected = s
        _deviceName.value = s.castDevice?.friendlyName
        val request = current ?: return
        val handoff = owner ?: return
        // Where the phone had got to. A live channel resumes at the edge, so its position is not a
        // place to start from and is deliberately thrown away.
        val position = handoff.releaseToCast()
        startRemote(s, request.copy(startPositionMs = if (request.isLive) 0L else position))
    }

    private fun startRemote(s: CastSession, request: CastRequest) {
        val remote = CastPlaybackEngine(s)
        _engine.value = remote
        // The lock screen, the media buttons and the notification follow the engine, so they follow
        // the television without a second implementation of any of the three.
        session.attach(remote)
        PlaybackService.start(context)
        remote.load(request)
    }

    private fun handBack() {
        connected = null
        _deviceName.value = null
        val remote = _engine.value ?: return
        val position = remote.position.value
        remote.detach()
        _engine.value = null
        // Withdrawn before the local engine starts: the tuner attaches its own on the way back in,
        // and two attaches racing would leave the session pointing at a stopped engine.
        session.attach(null)
        owner?.resumeFromCast(position)
    }
}

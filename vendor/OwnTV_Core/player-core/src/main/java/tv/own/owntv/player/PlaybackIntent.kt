package tv.own.owntv.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread intent survives buffering, decoder handoffs and internal retries. */
class PlaybackIntent(initial: Boolean = false) {
    private val desired = MutableStateFlow(initial)
    private val version = MutableStateFlow(0L)
    private var temporaryPauseRevision: Long? = null
    val requested: StateFlow<Boolean> = desired.asStateFlow()
    val revision: StateFlow<Long> = version.asStateFlow()
    /** Home during an owned display hold remembers the original Play, never a newer manual Pause. */
    val restoreRequested: Boolean
        get() = desired.value || temporaryPauseRevision == version.value

    /** Every deliberate command invalidates an older automatic resume, even when unchanged. */
    fun request(play: Boolean) {
        temporaryPauseRevision = null
        version.value++
        desired.value = play
    }

    fun markTemporaryPause() {
        if (!desired.value) temporaryPauseRevision = version.value
    }
}

/** A temporary pause may resume only its original owner and unmodified command revision. */
internal fun mayResumeOwnedPause(ownerMatches: Boolean, pausedAt: Long, currentRevision: Long, failed: Boolean): Boolean =
    ownerMatches && pausedAt == currentRevision && !failed

/** Guard only constrained devices; unknown decoder/size is not proof of an unsafe fallback. */
fun softwareDecoderAllowed(lowSpec: Boolean, hardware: Boolean?, width: Int, height: Int): Boolean =
    !lowSpec || hardware != false || width <= 0 || height <= 0 || maxOf(width, height) <= 1920 && minOf(width, height) <= 1080

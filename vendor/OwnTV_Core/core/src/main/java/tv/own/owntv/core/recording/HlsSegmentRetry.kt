package tv.own.owntv.core.recording

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Bounded sequential retry of the same sequence; each retry first obtains fresh signed URIs. */
internal object HlsSegmentRetry {
    const val ATTEMPTS = 3
    suspend inline fun <S> capture(
        sequence: Long, initial: S,
        segmentOf: (S, Long) -> HlsMediaPlaylist.Segment?,
        refresh: () -> S,
        capture: (S, HlsMediaPlaylist.Segment) -> Boolean,
        noinline wait: suspend () -> Unit = { delay(500L) },
    ): Boolean {
        var snapshot = initial
        repeat(ATTEMPTS) { attempt ->
            currentCoroutineContext().ensureActive()
            val segment = segmentOf(snapshot, sequence) ?: return false
            if (segment.gap) return false
            if (capture(snapshot, segment)) return true
            if (attempt + 1 < ATTEMPTS) { wait(); snapshot = refresh() }
        }
        return false
    }
}

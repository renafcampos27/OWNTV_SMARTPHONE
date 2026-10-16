package tv.own.owntv.core.recording

import java.io.IOException
import kotlinx.coroutines.delay
import okhttp3.Response
import tv.own.owntv.core.network.HttpRetryAfter

/** RAW archives have no verified byte-resume contract; a second whole body would duplicate them. */
internal object RecordingAttemptPolicy {
    fun mayRetry(archive: Boolean, writtenBytes: Long, segmented: Boolean): Boolean =
        !archive || writtenBytes <= 0L || segmented

    /** RAW requests never ask for or validate a byte range. Do not accept an unsolicited prefix. */
    fun acceptsRawStatus(status: Int): Boolean = status in 200..299 && status != 206

    fun httpFailure(response: Response, nowMs: Long): RecordingHttpFailure =
        RecordingHttpFailure(response.code, HttpRetryAfter.delayMs(response.header("Retry-After"), nowMs))

    /** A clock adjustment cannot end Retry-After early; cancellation and the recording stop win. */
    suspend fun awaitRetry(waitMs: Long, stopMs: Long, wallClock: () -> Long,
        monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
        pause: suspend (Long) -> Unit = { delay(it) }): Boolean {
        val started = monotonicMs()
        while (wallClock() < stopMs) {
            val elapsed = (monotonicMs() - started).coerceAtLeast(0L)
            val remaining = waitMs.coerceAtLeast(0L) - elapsed
            if (remaining <= 0L) return true
            pause(minOf(remaining, 1_000L, (stopMs - wallClock()).coerceAtLeast(1L)))
        }
        return false
    }
}

internal class RecordingHttpFailure(val status: Int, val retryAfterMs: Long?) :
    IOException("Recording HTTP $status")

/** SAF metadata can lag or stay zero. A failed write may also have accepted an unknown prefix. */
internal class RecordingRawProgress(initialBytes: Long = 0L) {
    private var knownOrAttemptedBytes = initialBytes.coerceAtLeast(0L)
    var confirmedBytes: Long = initialBytes.coerceAtLeast(0L)
        private set

    fun beforeWrite(count: Int) {
        require(count >= 0)
        knownOrAttemptedBytes = if (knownOrAttemptedBytes > Long.MAX_VALUE - count)
            Long.MAX_VALUE else knownOrAttemptedBytes + count
    }

    /** Called only after write returned successfully; an uncertain partial write is not confirmed. */
    fun afterWrite(count: Int) {
        require(count >= 0)
        confirmedBytes = if (confirmedBytes > Long.MAX_VALUE - count)
            Long.MAX_VALUE else confirmedBytes + count
    }

    fun mayRetry(archive: Boolean, observedBytes: Long, segmented: Boolean): Boolean =
        RecordingAttemptPolicy.mayRetry(archive, maxOf(knownOrAttemptedBytes, observedBytes), segmented)
}

/** stop() is a required commit step, not best-effort cleanup after returning success. */
internal object RecordingMuxCompletion {
    fun complete(copy: () -> Unit, stop: () -> Unit, valid: () -> Boolean): Boolean {
        copy()
        stop()
        return valid()
    }
}

package tv.own.owntv.core.recording

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.model.RecordingFailure
import tv.own.owntv.core.model.RecordingStatus

class RecordingAttemptPolicyTest {
    @Test fun interruptedRawArchiveNeverAppendsAnotherWholeBody() {
        assertFalse(RecordingAttemptPolicy.mayRetry(archive = true, writtenBytes = 1L, segmented = false))
        assertTrue(RecordingAttemptPolicy.mayRetry(archive = true, writtenBytes = 0L, segmented = false))
        assertTrue(RecordingAttemptPolicy.mayRetry(archive = false, writtenBytes = 100L, segmented = false))
        assertTrue(RecordingAttemptPolicy.mayRetry(archive = true, writtenBytes = 100L, segmented = true))
    }

    @Test fun unsolicitedPartialRawResponseIsNeverACompleteArchive() {
        assertTrue(RecordingAttemptPolicy.acceptsRawStatus(200))
        assertFalse(RecordingAttemptPolicy.acceptsRawStatus(206))
        assertFalse(RecordingAttemptPolicy.acceptsRawStatus(403))
        assertFalse(RecordingAttemptPolicy.acceptsRawStatus(503))
    }

    @Test fun staleSafLengthCannotAuthorizeASecondRawArchiveBody() {
        val progress = RecordingRawProgress()
        assertTrue(progress.mayRetry(archive = true, observedBytes = 0L, segmented = false))
        progress.beforeWrite(128)
        progress.afterWrite(128)
        assertEquals(128L, progress.confirmedBytes)
        assertFalse(progress.mayRetry(archive = true, observedBytes = 0L, segmented = false))
        // A subsequent failed metadata query must not reset the execution's local knowledge.
        assertFalse(progress.mayRetry(archive = true, observedBytes = 0L, segmented = false))
        assertTrue(progress.mayRetry(archive = true, observedBytes = 0L, segmented = true))
        assertTrue(progress.mayRetry(archive = false, observedBytes = 0L, segmented = false))
    }

    @Test fun partiallyAcceptedWriteIsConservativelyNotRepeated() {
        val progress = RecordingRawProgress()
        val bytes = java.io.ByteArrayOutputStream()
        progress.beforeWrite(16)
        bytes.write(ByteArray(16))
        progress.afterWrite(16)
        try {
            progress.beforeWrite(10)
            bytes.write(byteArrayOf(1, 2, 3))
            throw IOException("Provider failed after accepting a prefix")
        } catch (_: IOException) { }
        assertEquals(19, bytes.size())
        // Metadata may still say zero: only the first completed chunk is proven and retained.
        assertEquals(16L, maxOf(progress.confirmedBytes, 0L))
        assertFalse(progress.mayRetry(archive = true, observedBytes = 0L, segmented = false))
    }

    @Test fun alreadyRecordedBytesAreProtectedEvenWhenProviderReportsZeroAtStart() {
        assertFalse(RecordingRawProgress(initialBytes = 1L).mayRetry(true, 0L, false))
        val large = RecordingRawProgress(Long.MAX_VALUE)
        large.beforeWrite(128)
        assertFalse(large.mayRetry(true, 0L, false))
    }

    @Test fun httpStatusAndServerDelaySurviveTheTypedFailureWithoutCredentials() {
        val response = Response.Builder().request(Request.Builder().url("https://example.test/u/secret").build())
            .protocol(Protocol.HTTP_1_1).message("Unavailable").code(503).header("Retry-After", "17").build()
        val error = RecordingAttemptPolicy.httpFailure(response, 0L)
        assertEquals(503, error.status)
        assertEquals(17_000L, error.retryAfterMs)
        assertFalse(error.message!!.contains("secret"))
    }

    @Test fun retryDelayIsMonotonicEvenWhenWallClockMovesBackwards() = runBlocking {
        var elapsed = 0L
        var now = 50_000L
        val waits = mutableListOf<Long>()
        assertTrue(RecordingAttemptPolicy.awaitRetry(2_500L, 100_000L, { now }, { elapsed }) {
            waits += it
            elapsed += it
            now -= 2_000L
        })
        assertEquals(listOf(1_000L, 1_000L, 500L), waits)
    }

    @Test fun recordingWindowClosesBeforeLongRetryAfterAndZeroWaitDoesNotReopenExpiredWindow() = runBlocking {
        var now = 0L
        var elapsed = 0L
        assertFalse(RecordingAttemptPolicy.awaitRetry(Long.MAX_VALUE, 1_500L, { now }, { elapsed }) {
            now += it; elapsed += it
        })
        assertEquals(1_500L, now)
        assertFalse(RecordingAttemptPolicy.awaitRetry(0L, now, { now }))
    }

    @Test fun cancellationStopsRetryWithoutReopening() = runBlocking {
        var resumed = false
        try {
            RecordingAttemptPolicy.awaitRetry(5_000L, 10_000L, { 0L }, { 0L }) { throw CancellationException() }
            resumed = true
        } catch (_: CancellationException) { }
        assertFalse(resumed)
    }

    @Test fun muxSuccessRequiresCopyStopAndValidationInThatOrder() {
        val order = mutableListOf<String>()
        assertTrue(RecordingMuxCompletion.complete({ order += "copy" }, { order += "stop" }, { order += "validate"; true }))
        assertEquals(listOf("copy", "stop", "validate"), order)
        assertFalse(RecordingMuxCompletion.complete({}, {}, { false }))
    }

    @Test fun failedMuxStopCannotReportSuccessOrValidateAnUncommittedContainer() {
        var validated = false
        try {
            RecordingMuxCompletion.complete({}, { throw IOException("mux stop") }, { validated = true; true })
            fail("Expected failure")
        } catch (_: IOException) { }
        assertFalse(validated)
    }

    @Test fun cancelledCaptureRemainsPartialWhileFinalizationKeepsItsBytes() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var result: Pair<RecordingStatus, RecordingFailure>? = null
        val capture = launch {
            try { entered.complete(Unit); awaitCancellation() }
            finally {
                val interrupted = !currentCoroutineContext().isActive
                withContext(NonCancellable) {
                    assertTrue(currentCoroutineContext().isActive)
                    result = RecordingRules.outcomeOf(16L, RecordingFailure.NONE, incomplete = interrupted)
                }
            }
        }
        entered.await()
        capture.cancelAndJoin()
        assertEquals(RecordingStatus.PARTIAL to RecordingFailure.NONE, result)
    }
}

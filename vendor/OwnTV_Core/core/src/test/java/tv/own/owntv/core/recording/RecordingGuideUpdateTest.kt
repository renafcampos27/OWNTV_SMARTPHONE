package tv.own.owntv.core.recording

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RecordingGuideUpdateTest {
    @Test fun successfulImportReconcilesOnceAndReturnsItsCount() = runBlocking {
        val events = mutableListOf<String>()
        val count = RecordingGuideUpdate.run(
            refresh = { events.add("imported"); 42 },
            reconcile = { events.add("timers") },
            onReconcileFailure = { fail("Unexpected timer failure") },
        )
        assertEquals(42, count)
        assertEquals(listOf("imported", "timers"), events)
    }

    @Test fun failedOrCancelledImportNeverTouchesTimers() = runBlocking {
        for (failure in listOf(IllegalStateException("parse failed"), CancellationException("cancelled"))) {
            var reconciled = false
            var caught: Exception? = null
            try {
                RecordingGuideUpdate.run(
                    refresh = { throw failure },
                    reconcile = { reconciled = true },
                    onReconcileFailure = { fail("Not a timer failure") },
                )
            } catch (e: Exception) { caught = e }
            assertSame(failure, caught)
            assertFalse(reconciled)
        }
    }

    @Test fun timerFailureDoesNotRelabelTheSuccessfulFeedAsFailed() = runBlocking {
        val failure = IllegalStateException("alarm failed")
        var reported: Exception? = null
        val count = RecordingGuideUpdate.run(
            refresh = { 42 }, reconcile = { throw failure }, onReconcileFailure = { reported = it },
        )
        assertEquals(42, count)
        assertSame(failure, reported)
    }

    @Test fun cancellationDuringReconciliationEscapesTheBestEffortHandler() = runBlocking {
        val failure = CancellationException("cancelled")
        var reported = false
        var caught: Exception? = null
        try {
            RecordingGuideUpdate.run(
                refresh = { 42 }, reconcile = { throw failure }, onReconcileFailure = { reported = true },
            )
        } catch (e: Exception) { caught = e }
        assertSame(failure, caught)
        assertFalse(reported)
    }
}

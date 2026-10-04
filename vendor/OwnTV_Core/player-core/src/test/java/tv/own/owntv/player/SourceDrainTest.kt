package tv.own.owntv.player

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SourceDrainTest {
    @Test fun `a closure notification opens before the two hundred millisecond fallback`() = runTest {
        val calls = PlaybackHttpCalls()
        calls.factory(okhttp3.OkHttpClient()).newCall(
            okhttp3.Request.Builder().url("https://example.invalid/live.m3u8").build(),
        )
        calls.retireAll()
        val ready = async {
            SourceDrain.await(SourceDrain.HANDOVER_RECHECK_MS, calls::awaitRetirementChange) {
                calls.hasRetiredCalls()
            }
        }
        runCurrent()
        advanceTimeBy(50)
        calls.cancelAll()
        runCurrent()
        assertTrue(ready.isCompleted)
        assertTrue(ready.await())
        assertEquals(50L, testScheduler.currentTime)
    }

    @Test fun `pending resources are rechecked without an extra post-close margin`() = runTest {
        var pending = true
        val ready = async { SourceDrain.await(SourceDrain.HANDOVER_RECHECK_MS) { pending } }
        runCurrent()
        advanceTimeBy(100)
        pending = false
        runCurrent()
        advanceTimeBy(99); runCurrent()
        assertFalse(ready.isCompleted)
        advanceTimeBy(1); runCurrent()
        assertTrue(ready.await())
        assertEquals(200L, testScheduler.currentTime)
    }

    @Test fun `an already drained subsequent source opens immediately`() = runTest {
        assertTrue(SourceDrain.await(SourceDrain.HANDOVER_RECHECK_MS) { false })
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test fun `a newer selection cancels opening during a pending resource recheck`() = runTest {
        var opened = false
        val job = launch { if (SourceDrain.await(SourceDrain.HANDOVER_RECHECK_MS) { true }) opened = true }
        runCurrent()
        advanceTimeBy(100)
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertFalse(opened)
    }

    @Test fun `all resources including a late response must drain before opening`() = runTest {
        var pending = 1
        val ready = async { SourceDrain.await(SourceDrain.HANDOVER_RECHECK_MS) { pending > 0 } }
        runCurrent()
        advanceTimeBy(100); pending = 2
        advanceTimeBy(50); pending = 1
        advanceTimeBy(50); runCurrent()
        assertFalse(ready.isCompleted)
        advanceTimeBy(100); runCurrent()
        assertFalse(ready.isCompleted)
        pending = 0
        advanceUntilIdle()
        assertTrue(ready.await())
        assertEquals(400L, testScheduler.currentTime)
    }

    @Test fun `conditional waiting never authorizes opening an unclosed transport`() = runTest {
        assertFalse(SourceDrain.await(SourceDrain.HANDOVER_RECHECK_MS) { true })
        assertEquals(1500L, testScheduler.currentTime)
    }

    @Test fun `idle transport starts without adding any delay`() = runTest {
        assertTrue(SourceDrain.await { false })
        assertEquals(0L, testScheduler.currentTime)
    }
    @Test fun `opening waits for the last previous body to finish`() = runTest {
        val requests = TuneHttpRequests()
        val old = TuneToken(1, 1)
        val next = TuneToken(2, 1)
        requests.started(old); requests.started(old)
        val ready = async { SourceDrain.await { requests.otherSources(next) > 0 } }
        runCurrent()
        requests.finished(old)
        advanceTimeBy(100); runCurrent()
        assertFalse(ready.isCompleted)
        requests.finished(old)
        advanceUntilIdle()
        assertTrue(ready.await())
    }
    @Test fun `same channel retry still waits for the older source`() = runTest {
        val requests = TuneHttpRequests()
        requests.started(TuneToken(1, 1))
        assertFalse(SourceDrain.await { requests.otherSources(TuneToken(1, 2)) > 0 })
        assertEquals(1500L, testScheduler.currentTime)
    }
    @Test fun `manual selection cancels the pending opening`() = runTest {
        var opened = false
        val job = launch { if (SourceDrain.await { true }) opened = true }
        runCurrent()
        advanceTimeBy(100)
        job.cancel()
        advanceUntilIdle()
        assertTrue(job.isCancelled)
        assertFalse(opened)
    }
}

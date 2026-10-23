package tv.own.owntv.player

import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NativeReadTest {
    @Test fun expiredReadDoesNotQueryJniWhenTheBusyQueueFinallyDrains() = runTest {
        lateinit var queued: Runnable
        var reads = 0
        val result = async { readNativeValue(1000, { queued = it }, { true }) { ++reads } }
        runCurrent(); advanceTimeBy(1001); runCurrent()
        assertNull(result.await())
        queued.run()
        assertEquals(0, reads)
    }

    @Test fun cancelledConsumerDoesNotLeaveAnEffectiveNativeRead() = runTest {
        lateinit var queued: Runnable
        var reads = 0
        val result = async { readNativeValue(1000, { queued = it }, { true }) { ++reads } }
        runCurrent(); result.cancel(); runCurrent()
        queued.run()
        assertEquals(0, reads)
    }

    @Test fun newEngineOrTuneInvalidatesQueuedReadEvenWithTheSameUrl() = runTest {
        lateinit var queued: Runnable
        var current = true
        val result = async { readNativeValue(1000, { queued = it }, { current }) { error("stale JNI") } }
        runCurrent(); current = false; queued.run(); runCurrent()
        assertNull(result.await())
    }

    @Test fun resultFromAReplacedGenerationCannotBePublished() = runTest {
        var current = true
        assertNull(readNativeValue(1000, { it.run() }, { current }) { current = false; 42 })
    }

    @Test fun healthyReadReturnsWithoutDelayAndShutdownIsUnknown() = runTest {
        val before = testScheduler.currentTime
        assertEquals(42, readNativeValue(1000, { it.run() }, { true }) { 42 })
        assertEquals(before, testScheduler.currentTime)
        assertNull(readNativeValue(1000, { throw RejectedExecutionException() }, { true }) { 42 })
    }
}

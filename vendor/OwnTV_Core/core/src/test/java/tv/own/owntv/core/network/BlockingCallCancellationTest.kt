package tv.own.owntv.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BlockingCallCancellationTest {
    @Test fun `cancellation closes a blocking call before the coroutine can complete`() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val worker = launch(Dispatchers.IO) {
            cancelBlockingCallWithCoroutine({ cancelled.countDown() }) {
                entered.countDown()
                assertTrue(cancelled.await(2, TimeUnit.SECONDS))
            }
        }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        worker.cancel()
        assertTrue(cancelled.await(1, TimeUnit.SECONDS))
        worker.join()
    }

    @Test fun `normal completion preserves a reusable successful call`() = runBlocking {
        var cancelled = false
        val result = cancelBlockingCallWithCoroutine({ cancelled = true }) { 42 }
        assertEquals(42, result)
        assertFalse(cancelled)
    }
}

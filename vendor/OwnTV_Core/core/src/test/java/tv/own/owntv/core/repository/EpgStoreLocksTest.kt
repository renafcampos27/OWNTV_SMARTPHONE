package tv.own.owntv.core.repository

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EpgStoreLocksTest {
    @Test fun sameSourceWaitsWhileOtherSourcesRemainIndependent() = runBlocking {
        val locks = EpgStoreLocks()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            locks.withStores(listOf(1)) { entered.complete(Unit); release.await() }
        }
        entered.await()
        val same = async(start = CoroutineStart.UNDISPATCHED) { locks.withStores(listOf(1)) { true } }
        assertFalse(same.isCompleted)
        assertTrue(locks.withStores(listOf(2)) { true })
        release.complete(Unit); first.join(); assertTrue(same.await())
    }

    @Test fun cancellationDuringAcquisitionReleasesPreviouslyAcquiredSources() = runBlocking {
        val locks = EpgStoreLocks()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = launch(start = CoroutineStart.UNDISPATCHED) {
            locks.withStores(listOf(2)) { entered.complete(Unit); release.await() }
        }
        entered.await()
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
            locks.withStores(listOf(1, 2)) { fail() }
        }
        cancelled.cancelAndJoin()
        withTimeout(1000) { assertTrue(locks.withStores(listOf(1)) { true }) }
        release.complete(Unit); first.join()
        withTimeout(1000) { assertTrue(locks.withStores(listOf(2, 1, 1)) { true }) }
    }

    @Test fun reversedMultiSourceRequestsUseTheSameLockOrder() = runBlocking {
        val locks = EpgStoreLocks()
        var active = 0
        withTimeout(2000) {
            coroutineScope {
                repeat(20) { i -> launch {
                    locks.withStores(if (i % 2 == 0) listOf(1, 2) else listOf(2, 1)) {
                        assertEquals(0, active); active++; yield(); active--
                    }
                } }
            }
        }
        assertEquals(0, active)
    }
}

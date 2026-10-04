package tv.own.owntv.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TuneResolverJobsTest {
    @Test fun `A B A cancels the previous request itself and cancellation never publishes a result`() = runTest {
        val resolver = TuneResolverJobs(backgroundScope)
        val cancelled = mutableListOf<String>()
        val published = mutableListOf<String>()
        fun resolve(name: String) = resolver.launch {
            try {
                val response = suspendCancellableCoroutine<String> { /* pending portal request */ }
                published += response
            } finally { cancelled += name }
        }
        resolve("A1"); runCurrent()
        resolve("B"); runCurrent()
        resolve("A2"); runCurrent()
        assertEquals(listOf("A1", "B"), cancelled)
        resolver.cancel(); runCurrent()
        assertEquals(listOf("A1", "B", "A2"), cancelled)
        assertEquals(emptyList<String>(), published)
        assertFalse(resolver.active)
    }
}

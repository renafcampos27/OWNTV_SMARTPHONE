package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NativeSurfaceBindingTest {
    private val binding = NativeSurfaceBinding<Any, Any>()
    private val engine = Any()
    private val calls = mutableListOf<String>()
    private fun attach(request: NativeSurfaceBinding.Request<Any, Any>, valid: Boolean = true) =
        binding.attach(request, { valid }, { _, _ -> calls += "attach" }, { calls += "detach" })

    @Test fun supersededQueuedAttachNeverAcquiresNativeReference() {
        val old = binding.request(Any(), engine)
        val current = binding.request(Any(), engine)
        assertFalse(attach(old))
        assertTrue(attach(current))
        assertEquals(listOf("attach"), calls)
    }

    @Test fun replacedNativeReferenceIsRetiredBeforeBindingTheNextView() {
        assertTrue(attach(binding.request(Any(), engine)))
        assertTrue(attach(binding.request(Any(), engine)))
        assertEquals(listOf("attach", "detach", "attach"), calls)
    }

    @Test fun outgoingViewIsRetiredWhileReplacementIsStillQueued() {
        val old = Any()
        attach(binding.request(old, engine))
        val replacement = binding.request(Any(), engine)
        binding.detach(old) { calls += "detach" }
        assertEquals(listOf("attach", "detach"), calls)
        assertTrue(attach(replacement))
        binding.detach(old) { fail("old destruction must not retire the new view") }
        assertEquals(listOf("attach", "detach", "attach"), calls)
    }

    @Test fun destroyedOrInvalidViewNeverAttachesLater() {
        val surface = Any()
        val pending = binding.request(surface, engine)
        binding.detach(surface) { fail("not installed") }
        assertFalse(attach(pending))
        assertFalse(attach(binding.request(Any(), engine), valid = false))
        assertTrue(calls.isEmpty())
    }

    @Test fun duplicateAttachDoesNotLeakAnotherGlobalReference() {
        val surface = Any()
        attach(binding.request(surface, engine))
        attach(binding.request(surface, engine))
        binding.detach(surface) { calls += "detach" }
        assertEquals(listOf("attach", "detach"), calls)
    }

    @Test fun engineDestructionInvalidatesItsQueuedAndInstalledBindings() {
        attach(binding.request(Any(), engine))
        val pending = binding.request(Any(), engine)
        binding.forget(engine)
        assertFalse(attach(pending))
        binding.detach { fail("destroy already releases this reference") }
        assertTrue(attach(binding.request(Any(), Any())))
    }

    @Test fun partiallyFailedAttachCanStillBeRetired() {
        val surface = Any()
        try {
            binding.attach(binding.request(surface, engine), { true }, { _, _ -> error("native bind failed") }, {})
            fail("expected bind failure")
        } catch (_: IllegalStateException) { }
        var released = false
        binding.detach(surface) { released = true }
        assertTrue(released)
    }

    @Test(timeout = 5000) fun destructionWaitsForInFlightAttachButNotForCommandQueue() {
        val workers = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val destroyed = CountDownLatch(1)
        val surface = Any()
        val pending = binding.request(surface, engine)
        try {
            val attached = workers.submit {
                binding.attach(pending, { true }, { _, _ ->
                    entered.countDown()
                    check(release.await(2, TimeUnit.SECONDS))
                }, {})
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val detached = workers.submit { binding.detach(surface) { destroyed.countDown() } }
            assertFalse(destroyed.await(30, TimeUnit.MILLISECONDS))
            release.countDown()
            attached.get(2, TimeUnit.SECONDS)
            detached.get(2, TimeUnit.SECONDS)
            assertEquals(0L, destroyed.count)
            assertFalse(attach(pending))
        } finally { release.countDown(); workers.shutdownNow() }
    }
}

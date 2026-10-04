package tv.own.owntv.core.network

import org.junit.Assert.*
import org.junit.Test

class ConnectivityReadMemoryTest {
    @Test fun unknownFailureDoesNotInventOnlineOrUnmeteredState() {
        val memory = ConnectivityReadMemory()
        assertFalse(memory.online { error("OEM unavailable") })
        assertTrue(memory.metered { error("OEM unavailable") })
    }
    @Test fun temporaryFailurePreservesLastKnownAndLaterReadRecovers() {
        val memory = ConnectivityReadMemory()
        val first = ConnectivityState(networkId = 1, internet = true, validated = true, metered = true)
        assertEquals(first, memory.read { first })
        assertNull(memory.read { throw SecurityException() })
        assertFalse(memory.online { error("still unavailable") })
        assertTrue(memory.metered { error("still unavailable") })
        val replacement = ConnectivityState(networkId = 2, internet = true, validated = true, metered = false)
        assertEquals(replacement, memory.read { replacement })
        assertFalse(memory.metered { replacement })
    }
    @Test fun registrationFailureCanRetryAndSuccessIsNotDuplicated() {
        var attempts = 0
        val retry = NetworkRegistrationRetry()
        var registered = retry.attempt { attempts++; throw IllegalStateException() }
        assertFalse(registered)
        registered = retry.attempt { attempts++ }
        registered = retry.attempt { attempts++ }
        assertTrue(registered)
        assertEquals(2, attempts)
    }
    @Test fun permanentPermissionFailureDoesNotLoopWhilePollingStillRecovers() {
        val registration = NetworkRegistrationRetry()
        val reads = ConnectivityReadMemory()
        var registerCalls = 0
        repeat(4) {
            assertFalse(registration.attempt { registerCalls++; throw SecurityException() })
            assertNull(reads.read { error("platform unavailable") })
        }
        val restored = ConnectivityState(networkId = 3, internet = true, validated = true)
        assertEquals(restored, reads.read { restored })
        assertEquals(1, registerCalls)
    }
    @Test fun transientRegistrationRetriesAreBounded() {
        val registration = NetworkRegistrationRetry()
        var calls = 0
        repeat(8) { registration.attempt { calls++; error("OEM unavailable") } }
        assertEquals(3, calls)
    }
    @Test fun cancellationIsNotConvertedToNetworkFailure() {
        try { ConnectivityReadMemory().read { throw kotlinx.coroutines.CancellationException() }; fail() }
        catch (_: kotlinx.coroutines.CancellationException) { }
        try { NetworkRegistrationRetry().attempt { throw kotlinx.coroutines.CancellationException() }; fail() }
        catch (_: kotlinx.coroutines.CancellationException) { }
    }
}

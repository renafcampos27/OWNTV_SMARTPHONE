package tv.own.owntv.core.stalker

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StalkerProfileRetryTest {
    @Test fun healthyIdentityProfileNeedsOneRequest() = runBlocking {
        val requests = mutableListOf<String?>()
        val result = StalkerClient.resolveProfile(StalkerDeviceIdentity(serialNumber = "test"), "MAG254") {
            requests.add(it); mapOf("id" to "1")
        }
        assertEquals(listOf<String?>(null), requests)
        assertEquals("1", result["id"])
    }
    @Test fun identityConflictGetsOnlyOneModelRetry() = runBlocking {
        val requests = mutableListOf<String?>()
        val result = StalkerClient.resolveProfile(StalkerDeviceIdentity(serialNumber = "test"), "MAG254") {
            requests.add(it)
            if (it == null) mapOf("msg" to "Device conflict") else mapOf("id" to "1")
        }
        assertEquals(listOf(null, "MAG254"), requests)
        assertEquals("1", result["id"])
    }
    @Test fun macOnlyRejectionDoesNotRetry() = runBlocking {
        var requests = 0
        try {
            StalkerClient.resolveProfile(StalkerDeviceIdentity(), null) {
                requests++; mapOf("block_msg" to "Device conflict")
            }
            fail()
        } catch (_: StalkerClient.StalkerAuthException) { }
        assertEquals(1, requests)
    }
    @Test fun unresolvedIdentityConflictStopsAfterTwoRequests() = runBlocking {
        var requests = 0
        try {
            StalkerClient.resolveProfile(StalkerDeviceIdentity(serialNumber = "test"), null) {
                requests++; mapOf("msg" to "Device conflict")
            }
            fail()
        } catch (_: StalkerClient.StalkerAuthException) { }
        assertEquals(2, requests)
    }
}

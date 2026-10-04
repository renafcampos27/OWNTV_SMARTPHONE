package tv.own.owntv.core.network

import org.junit.Assert.*
import org.junit.Test

class DefaultNetworkTrackerTest {
    private fun online(id: Long, transport: String = "wifi", metered: Boolean = false) =
        ConnectivityState(id, internet = true, validated = true, metered = metered, transport = transport)
    @Test fun oldCallbacksCannotOverwriteReplacement() {
        val tracker = DefaultNetworkTracker()
        tracker.available(1); tracker.capabilities(online(1))
        tracker.available(2); tracker.capabilities(online(2, "cellular", true))
        tracker.lost(1); tracker.capabilities(online(1))
        assertEquals(online(2, "cellular", true), tracker.state)
    }
    @Test fun onlineHandoverStillPublishesIdentityAndCost() {
        val tracker = DefaultNetworkTracker()
        tracker.available(1); val wifi = tracker.capabilities(online(1))
        tracker.available(2); val mobile = tracker.capabilities(online(2, "cellular", true))
        assertTrue(wifi.online && mobile.online)
        assertNotEquals(wifi, mobile)
        assertTrue(mobile.metered)
    }
    @Test fun stalePollIsRejected() {
        val tracker = DefaultNetworkTracker()
        val revision = tracker.revision
        tracker.available(2); tracker.capabilities(online(2))
        assertEquals(online(2), tracker.reconcile(online(1), revision))
    }
    @Test fun currentLossAndOemMissAreHandled() {
        val tracker = DefaultNetworkTracker()
        tracker.available(1); tracker.capabilities(online(1))
        assertFalse(tracker.lost(1).online)
        assertTrue(tracker.reconcile(online(2), tracker.revision).online)
    }
    @Test fun unvalidatedInternetIsNotOnline() {
        assertFalse(ConnectivityState(1, internet = true).online)
    }
}

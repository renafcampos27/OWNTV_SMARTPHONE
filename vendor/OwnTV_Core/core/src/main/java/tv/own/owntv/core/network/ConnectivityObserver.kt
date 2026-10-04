package tv.own.owntv.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Singleton: one callback/poll shared by consumers; no synchronous queries inside callbacks. */
class ConnectivityObserver(context: Context) {
    private val application = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reads = ConnectivityReadMemory()
    private val cm: ConnectivityManager?
        get() = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private fun NetworkCapabilities.snapshot(id: Long) = ConnectivityState(
        networkId = id,
        internet = hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
        validated = hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
        metered = !hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
        transport = when {
            hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            else -> "other"
        },
    )

    private fun snapshotNow(): ConnectivityState {
        val manager = cm ?: throw IllegalStateException("connectivity service unavailable")
        val network = manager.activeNetwork ?: return ConnectivityState()
        return manager.getNetworkCapabilities(network)?.snapshot(network.networkHandle)
            ?: ConnectivityState(networkId = network.networkHandle)
    }

    /** These synchronous snapshots are only for callers outside NetworkCallback. */
    fun isOnlineNow(): Boolean = reads.online(::snapshotNow)
    fun isMeteredNow(): Boolean = reads.metered(::snapshotNow)

    val state: Flow<ConnectivityState> = callbackFlow {
        val lock = Any()
        val tracker = DefaultNetworkTracker()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                synchronized(lock) { trySend(tracker.available(network.networkHandle)) }
            }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                synchronized(lock) { trySend(tracker.capabilities(caps.snapshot(network.networkHandle))) }
            }
            override fun onLost(network: Network) {
                synchronized(lock) { trySend(tracker.lost(network.networkHandle)) }
            }
        }
        var manager: ConnectivityManager? = null
        var registered = false
        val registration = NetworkRegistrationRetry()
        var poll: kotlinx.coroutines.Job? = null
        try {
            // All supported devices are API 26+, which provides this callback.
            fun register() {
                if (registered) return
                // A failed OEM/permission query must not kill the shared producer permanently.
                registered = registration.attempt {
                    val available = cm ?: throw IllegalStateException("connectivity service unavailable")
                    available.registerDefaultNetworkCallback(callback)
                    manager = available
                }
            }
            fun reconcile() {
                val revision = synchronized(lock) { tracker.revision }
                val snapshot = reads.read(::snapshotNow)
                synchronized(lock) {
                    if (snapshot != null) trySend(tracker.reconcile(snapshot, revision))
                    else trySend(tracker.state.copy(observationKnown = false))
                }
            }
            register()
            reconcile()
            // OEM backstop outside callbacks; a newer callback invalidates the snapshot.
            poll = launch { while (isActive) { delay(20_000); register(); reconcile() } }
            awaitClose { }
        } finally {
            poll?.cancel()
            if (registered) runCatching { manager?.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged().shareIn(scope, SharingStarted.WhileSubscribed(5_000, 0), replay = 1)

    val isOnline: Flow<Boolean> = state.map { it.online }.distinctUntilChanged()
}

package tv.own.owntv.core.network

/** No address/SSID/credentials; identity changes remain visible even when both networks are online. */
data class ConnectivityState(
    val networkId: Long? = null,
    val internet: Boolean = false,
    val validated: Boolean = false,
    val metered: Boolean = false,
    val transport: String = "none",
    val observationKnown: Boolean = true,
) {
    val online: Boolean get() = observationKnown && internet && validated
}

/** Called under the observer's lock. Late callbacks cannot overwrite a replacement default. */
internal class DefaultNetworkTracker {
    var revision = 0L
        private set
    var state = ConnectivityState()
        private set
    fun available(id: Long): ConnectivityState {
        revision++
        if (state.networkId != id) state = ConnectivityState(networkId = id)
        return state
    }
    fun capabilities(next: ConnectivityState): ConnectivityState {
        revision++
        if (next.networkId == state.networkId) state = next
        return state
    }
    fun lost(id: Long): ConnectivityState {
        revision++
        if (state.networkId == id) state = ConnectivityState()
        return state
    }
    fun reconcile(next: ConnectivityState, expectedRevision: Long): ConnectivityState {
        if (revision == expectedRevision) { revision++; state = next }
        return state
    }
}

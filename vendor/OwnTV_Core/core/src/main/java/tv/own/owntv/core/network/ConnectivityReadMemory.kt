package tv.own.owntv.core.network

internal class NetworkRegistrationRetry(private val maxAttempts: Int = 3) {
    var registered = false
        private set
    private var attempts = 0
    fun attempt(register: () -> Unit): Boolean {
        if (registered || attempts >= maxAttempts) return registered
        attempts++
        try {
            register()
            registered = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            attempts = maxAttempts // Permission failure is permanent for this subscription.
        } catch (_: Exception) { }
        return registered
    }
}

/** Query failure is unknown, not evidence that a previously available network was lost. */
internal class ConnectivityReadMemory {
    @Volatile var lastKnown: ConnectivityState? = null
        private set

    fun read(query: () -> ConnectivityState): ConnectivityState? = try {
        query().also { lastKnown = it }
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Exception) { null }

    fun online(query: () -> ConnectivityState): Boolean = read(query)?.online ?: false
    // Unknown charging policy must not admit a mobile recording by assuming unmetered Wi-Fi.
    fun metered(query: () -> ConnectivityState): Boolean = read(query)?.metered ?: true
}

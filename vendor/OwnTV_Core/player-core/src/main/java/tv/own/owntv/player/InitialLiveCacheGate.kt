package tv.own.owntv.player

/** Confined to the native-command executor. Property notifications alone cannot release a new load. */
internal class InitialLiveCacheGate {
    private var generation = -1
    private var path: String? = null
    private var armed = false

    fun arm(owner: Int, requestedPath: String, enabled: Boolean) {
        generation = owner
        path = requestedPath
        armed = enabled
    }

    fun release(owner: Int, nativePath: String?, coreIdle: Boolean?, pausedForCache: Boolean?): Boolean {
        if (!armed || owner != generation || nativePath != path || coreIdle != false || pausedForCache != false) return false
        armed = false
        return true
    }
}

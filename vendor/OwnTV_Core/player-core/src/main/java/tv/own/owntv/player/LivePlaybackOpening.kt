package tv.own.owntv.player

/** Native-executor confined. A loaded file or video dimensions alone do not establish playback. */
internal class LivePlaybackOpening {
    private var generation = -1
    private var path: String? = null
    private var pending = false
    private var previousEntryId: String? = null
    private var identifyEntry = false

    fun arm(owner: Int, requestedPath: String, previousEntry: String? = null, entryIdentityAvailable: Boolean = false) {
        generation = owner
        path = requestedPath
        pending = true
        previousEntryId = previousEntry
        identifyEntry = entryIdentityAvailable
    }

    fun confirm(owner: Int, nativePath: String?, fileLoaded: Boolean,
                coreIdle: Boolean?, pausedForCache: Boolean?, nativeEntryId: String? = null): Boolean {
        if (!pending || owner != generation || nativePath != path || !fileLoaded ||
            coreIdle != false || pausedForCache != false) return false
        if (identifyEntry && (nativeEntryId == null || nativeEntryId == previousEntryId)) return false
        pending = false
        return true
    }
}

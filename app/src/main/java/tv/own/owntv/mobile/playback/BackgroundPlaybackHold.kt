package tv.own.owntv.mobile.playback

/** Only the owner and command revision that this lifecycle paused may be restored. */
internal class BackgroundPlaybackHold<T : Any> {
    private data class Ticket<T>(val owner: T, val revision: Long, val item: Long) {
        fun matches(current: T, version: Long, currentItem: Long) =
            owner === current && revision == version && item == currentItem
    }
    data class Restore(val play: Boolean, val video: Boolean)
    private var paused: Ticket<T>? = null
    private var hidden: Ticket<T>? = null

    fun onBackground(
        owner: T, itemRevision: Long, requested: Boolean, audioOnly: Boolean,
        allowBackground: Boolean, dropVideo: Boolean,
        pause: () -> Long, hideVideo: () -> Long,
    ) {
        if (!allowBackground) {
            if (requested) paused = Ticket(owner, pause(), itemRevision)
        } else if (dropVideo && !audioOnly) {
            hidden = Ticket(owner, hideVideo(), 0L)
        }
    }

    fun rememberHiddenVideo(owner: T, audioRevision: Long) { hidden = Ticket(owner, audioRevision, 0L) }

    fun onForeground(owner: T, revision: Long, itemRevision: Long, audioRevision: Long, failed: Boolean): Restore {
        val restore = Restore(
            play = !failed && paused?.matches(owner, revision, itemRevision) == true,
            // Reconnects and manual Pause preserve this hold; an explicit audio-mode choice replaces it.
            video = hidden?.matches(owner, audioRevision, 0L) == true,
        )
        paused = null
        hidden = null
        return restore
    }
}

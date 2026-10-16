package tv.own.owntv.player

/** A repeated URL is still a new load. Tags survive in Media3's event timeline. */
internal class PlaybackEventOwner {
    class Ticket internal constructor()
    var current: Ticket? = null
        private set
    fun begin(): Ticket = Ticket().also { current = it }
    fun invalidate() { current = null }
    fun accepts(ticket: Any?): Boolean = ticket != null && ticket === current
}

internal data class PlaybackRequestStamp(val generation: Int, val revision: Long) {
    fun accepts(generation: Int, revision: Long, requested: Boolean): Boolean =
        this.generation == generation && this.revision == revision && requested
}

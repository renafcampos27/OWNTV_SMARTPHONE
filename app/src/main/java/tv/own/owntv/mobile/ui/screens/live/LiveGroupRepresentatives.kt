package tv.own.owntv.mobile.ui.screens.live

/** A page may be dropped and read again. Keep identity, not a one-shot seen flag. */
internal class LiveGroupRepresentatives {
    private val representatives = mutableMapOf<String, Long>()
    fun accept(group: String, channelId: Long): Boolean =
        representatives.getOrPut(group) { channelId } == channelId
}

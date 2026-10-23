package tv.own.owntv.core.customize

/**
 * Replace the visible keys at their first saved position, keeping the other playlists' relative order.
 * A shared custom category must not drag this playlist ahead of the folders stored before it.
 * Empty rails preserve existing choices; reset remains the store's separate reset operation.
 */
internal fun mergeCategoryOrder(stored: List<String>, onScreen: List<String>): List<String> {
    val ordered = onScreen.distinct()
    val shown = ordered.toHashSet()
    val saved = stored.distinct()
    val firstShown = saved.indexOfFirst { it in shown }
    val rest = saved.filterNot { it in shown }
    val at = if (firstShown < 0) rest.size else firstShown
    return rest.take(at) + ordered + rest.drop(at)
}

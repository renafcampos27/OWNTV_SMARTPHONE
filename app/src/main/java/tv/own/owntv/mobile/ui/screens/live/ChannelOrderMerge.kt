package tv.own.owntv.mobile.ui.screens.live

/** Reorders the visible subset while preserving positions belonging to hidden/unloaded items. */
internal fun mergeChannelOrder(previous: List<Long>, edited: List<Long>): List<Long> {
    val picked = edited.distinct()
    val selected = picked.toSet()
    val pending = picked.iterator()
    val result = previous.distinct().map { old ->
        if (old in selected) pending.next() else old
    }.toMutableList()
    while (pending.hasNext()) result.add(pending.next())
    return result
}

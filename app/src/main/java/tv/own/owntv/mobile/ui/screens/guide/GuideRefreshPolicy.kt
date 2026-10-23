package tv.own.owntv.mobile.ui.screens.guide

/** Table invalidations are facts; settling and screen visibility only defer their consumption. */
internal class GuideRefreshPolicy(private val settleMs: Long) {
    private var dirty = false
    private var changedAt = 0L
    fun changed(atMs: Long) { dirty = true; changedAt = atMs }
    fun takeReady(atMs: Long, visible: Boolean): Boolean {
        if (!visible || !dirty || atMs < changedAt || atMs - changedAt < settleMs) return false
        dirty = false
        return true
    }
    fun takeOnReturn(): Boolean = dirty.also { dirty = false }
}

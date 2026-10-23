package tv.own.owntv.player

internal data class VideoFrameWindow(val rendered: Long, val dropped: Long, val elapsedMs: Long) {
    val percent: Double get() = if (rendered + dropped > 0) dropped.toDouble() * 100 / (rendered + dropped) else 0.0
    val severe: Boolean get() = elapsedMs >= 10_000 && dropped > 100 && dropped > rendered
    val warning: Boolean get() = elapsedMs >= 29_000 && dropped >= 150 && percent > 20
}

/** Counters are decoder output, never frame-submission callbacks or wall-clock FPS estimates. */
internal class VideoFrameHealth {
    private data class Point(val at: Long, val rendered: Long, val dropped: Long)
    private val points = ArrayDeque<Point>(64)
    private var warned = false
    var latest: VideoFrameWindow? = null
        private set
    var severe: Boolean = false
        private set

    fun resetWindow() { points.clear(); latest = null; severe = false }
    fun resetTune() { resetWindow(); warned = false }
    /** Returns one diagnostic warning per tune, with a rolling 30 s snapshot for the info page. */
    fun poll(nowMs: Long, rendered: Long, dropped: Long, eligible: Boolean): Boolean {
        if (!eligible || rendered < 0 || dropped < 0) { resetWindow(); return false }
        points.lastOrNull()?.let { last ->
            if (nowMs <= last.at || nowMs - last.at > 5_000 || rendered < last.rendered || dropped < last.dropped) resetWindow()
            else if (nowMs - last.at < 500) return false
        }
        val point = Point(nowMs, rendered, dropped)
        points.addLast(point)
        while (points.size > 64 || (points.size > 1 && nowMs - points.elementAt(1).at > 30_000)) points.removeFirst()
        val first = points.first()
        latest = VideoFrameWindow(rendered - first.rendered, dropped - first.dropped, nowMs - first.at)
        val ten = points.lastOrNull { nowMs - it.at >= 10_000 }
        severe = ten?.let { VideoFrameWindow(rendered - it.rendered, dropped - it.dropped, nowMs - it.at).severe } == true
        val warn = !warned && latest?.warning == true
        if (warn) warned = true
        return warn
    }
}

/** Once per user tune/item, not per decoder or reconnect. Default disabled; never changes engine. */
internal data class VideoDegradationRecovery(private var used: Boolean = false) {
    private var severeSinceMs: Long? = null
    fun reset() { used = false; severeSinceMs = null }
    fun acquire(nowMs: Long, enabled: Boolean, severe: Boolean, playing: Boolean, bufferMs: Long, surfaceReady: Boolean): Boolean {
        if (!enabled || !severe || !playing || !surfaceReady || bufferMs < 2_000) { severeSinceMs = null; return false }
        if (used) return false
        val since = severeSinceMs ?: nowMs.also { severeSinceMs = it }
        if (nowMs - since < 2_000) return false
        used = true
        return true
    }
}

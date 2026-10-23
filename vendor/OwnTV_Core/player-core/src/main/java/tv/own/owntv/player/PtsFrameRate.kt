package tv.own.owntv.player

import kotlin.math.abs

/** Media timestamps, independent of wall clock, playback speed and UI polling. Bounded to 600 gaps. */
internal class PtsFrameRate {
    private val gaps = LongArray(600)
    private var count = 0
    private var next = 0
    private var previous = Long.MIN_VALUE
    private var version = 0L
    private var readVersion = -1L
    private var cached: Float? = null

    @Synchronized fun add(ptsUs: Long) {
        if (previous != Long.MIN_VALUE) {
            val gap = ptsUs - previous
            if (gap == 0L) return // duplicate timestamp, not a new cadence
            if (gap in 1..200_000) {
                gaps[next] = gap
                next = (next + 1) % gaps.size
                count = (count + 1).coerceAtMost(gaps.size)
            } else { count = 0; next = 0 }
        }
        previous = ptsUs
        version++
    }

    @Synchronized fun rate(): Float? {
        if (readVersion == version) return cached
        readVersion = version
        cached = null
        if (count < 2) return null
        val sorted = gaps.copyOf(count).apply { sort() }
        val median = sorted[count / 2].toDouble()
        var duration = 0L
        var steady = 0
        sorted.forEach { if (it >= median * .75 && it <= median * 1.25) { duration += it; steady++ } }
        if (duration < 4_000_000L || steady < count * .75) return null
        return BroadcastFrameRate.snap(1_000_000.0 * steady / duration).also { cached = it }
    }

    @Synchronized fun reset() {
        count = 0; next = 0; previous = Long.MIN_VALUE; version++; cached = null; readVersion = -1
    }
}

internal object BroadcastFrameRate {
    private val rates = doubleArrayOf(24000.0 / 1001, 24.0, 25.0, 30000.0 / 1001, 30.0,
        48000.0 / 1001, 48.0, 50.0, 60000.0 / 1001, 60.0, 100.0, 120000.0 / 1001, 120.0)
    /** A decoder dropping alternate frames must not halve a valid declared content rate. */
    fun preferMeasured(declared: Float?, measured: Float?): Float? {
        val known = declared?.takeIf { it.isFinite() && it > 0 }
        return if (measured != null && (known == null || abs(measured - known) < known * .01f)) measured else known
    }

    /** A wide tolerance would silently turn 59.94 into 60; uncertain values stay undeclared. */
    fun snap(raw: Double): Float? = rates.minByOrNull { abs(it - raw) }
        ?.takeIf { raw.isFinite() && abs(it - raw) <= it * .0004 }?.toFloat()
}

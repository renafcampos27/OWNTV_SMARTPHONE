package tv.own.owntv.core.settings

/**
 * How close to the live edge to play Live TV, trading latency against stability. Applied on the next
 * channel open (live streams only — VOD is unaffected):
 *  - ExoPlayer live → a `MediaItem.LiveConfiguration` target offset (the main HLS latency lever);
 * The forward reserve is configured separately through the reserve preferences.
 *
 * [BALANCED] is the default and applies no override — the engines keep their existing behaviour, so
 * the manifest's live offset is retained.
 */
enum class LiveLatency {
    LOW,
    BALANCED,
    STABLE,
    CUSTOM;

    companion object {
        val DEFAULT = BALANCED
        fun fromName(name: String?): LiveLatency = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Shared legacy preset values; live offset and forward reserve are resolved independently. */
object LiveBuffer {
    const val LOW_SECS = 2
    const val STABLE_SECS = 15
    const val CUSTOM_MIN = 1
    const val CUSTOM_MAX = 60
    const val CUSTOM_DEFAULT = 8

    /** Below this many seconds counts as "lower than Balanced" — the low-latency warning threshold. */
    const val WARN_BELOW_SECS = CUSTOM_DEFAULT

    fun clampCustom(secs: Int): Int = secs.coerceIn(CUSTOM_MIN, CUSTOM_MAX)

    /** True when [secs] is a real, below-Balanced buffer worth warning the user about. */
    fun isLowLatency(secs: Int?): Boolean = secs != null && secs < WARN_BELOW_SECS

    /**
     * A per-playlist latency override, carrying an effective depth that is **itself** nullable —
     * Balanced means "engine defaults, no target offset". A plain `Int?` could not tell "this playlist
     * says Balanced" from "this playlist says nothing", which is the difference between overriding the
     * global setting and inheriting it.
     */
    @JvmInline
    value class Override(val secs: Int?)

    /** Resolve a preset depth/offset; null leaves the relevant engine default in place. */
    fun effectiveSeconds(mode: LiveLatency, customSecs: Int): Int? = when (mode) {
        LiveLatency.LOW -> LOW_SECS
        LiveLatency.STABLE -> STABLE_SECS
        LiveLatency.CUSTOM -> clampCustom(customSecs)
        LiveLatency.BALANCED -> null
    }

    /**
     * Balanced targets an 8–10 s forward reserve. The lower threshold requests more media; the upper
     * threshold stops read-ahead. Their difference is a media-duration range, not a guarantee about
     * socket idle time: segment availability, download speed and memory also affect loading.
     */
    const val BALANCED_SECS = 8
    const val DEFAULT_EXTRA_SECS = 2
    fun clampExtra(secs: Int): Int = secs.coerceIn(0, 10)

    /** Initial preparation and underrun recovery deliberately use independent thresholds. */
    const val DEFAULT_START_MS = 1_500
    // After an underrun the renderer resumes as soon as it has playable samples.
    const val DEFAULT_RESTART_MS = 0

    /** Initial pre-buffer choices; 0 keeps the automatic initial threshold. */
    val PREROLL_CHOICES = listOf(0) + (2..10).toList()
    const val PREROLL_OFF = 0

    /** Resolved ExoPlayer `DefaultLoadControl` durations for a live tune. */
    data class LoadControlMs(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val bufferForPlaybackMs: Int,
        val bufferForPlaybackAfterRebufferMs: Int,
    )

    /**
     * Resolve the forward reserve ([bufferSecs], null = Balanced), initial pre-buffer and
     * refill range independently of the HLS live offset. Recovery never adds a pre-buffer wait.
     *
     * The pre-roll raises the buffer floor when it has to: `DefaultLoadControl` requires
     * `minBufferMs >= bufferForPlayback*`, and asking to buffer 10 s before starting means the buffer
     * must be allowed to hold 10 s in the first place — so a deep pre-roll wins over a shallow preset
     * rather than throwing.
     */
    fun loadControlFor(bufferSecs: Int?, prerollSecs: Int, extraSecs: Int = DEFAULT_EXTRA_SECS): LoadControlMs {
        val prerollMs = prerollSecs.coerceAtLeast(0) * 1000
        val startMs = if (prerollMs > 0) prerollMs else DEFAULT_START_MS
        val restartMs = DEFAULT_RESTART_MS
        val depthSecs = bufferSecs ?: BALANCED_SECS
        val minMs = (depthSecs * 1000).coerceAtLeast(maxOf(startMs, restartMs))
        return LoadControlMs(
            minBufferMs = minMs,
            maxBufferMs = minMs + clampExtra(extraSecs) * 1000,
            bufferForPlaybackMs = startMs,
            bufferForPlaybackAfterRebufferMs = restartMs,
        )
    }

    /**
     * Allocator byte target, scaled for deeper reserves and initial pre-buffer. This is a soft loading
     * threshold, not a hard cap on application RAM. High-bitrate streams can reach it before the time
     * target and may start with less buffered media. Bound the scaling more conservatively on low-RAM
     * devices; diagnostics expose the requested reserve, actual duration and allocator target.
     */
    fun targetBufferBytes(bufferSecs: Int?, prerollSecs: Int, defaultBytes: Int, lowSpec: Boolean = false): Int {
        val depthSecs = maxOf(bufferSecs ?: BALANCED_SECS, prerollSecs.coerceAtLeast(0))
        if (depthSecs <= BALANCED_SECS) return defaultBytes
        val scaled = defaultBytes.toLong() * depthSecs / BALANCED_SECS
        val maxMultiplier = if (lowSpec) 2 else 3
        return scaled.coerceAtMost(defaultBytes.toLong() * maxMultiplier).toInt()
    }
}

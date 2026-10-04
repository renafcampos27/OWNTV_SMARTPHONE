package tv.own.owntv.player

/** READY alone and a picture left by a stalled/previous source cannot invalidate an error. */
internal fun confirmsLivePlayback(
    ownsSource: Boolean,
    playing: Boolean,
    failed: Boolean,
    hasVideo: Boolean,
    renderedVideo: Boolean,
    lastFrameAgeMs: Long?,
    hasAudio: Boolean,
    audioAdvanced: Boolean,
): Boolean = ownsSource && playing && !failed &&
    if (hasVideo) renderedVideo && lastFrameAgeMs != null && lastFrameAgeMs in 0L..2_000L
    else hasAudio && audioAdvanced

package tv.own.owntv.core.recording

/** Known requested-window loss only; unmeasured bytes are never presented as measured duration. */
internal object HlsCaptureCompleteness {
    fun missingWindow(
        capturedMs: Long, longestSegmentMs: Long, programmeStartMs: Long, programmeStopMs: Long,
        startedAtMs: Long, archive: Boolean,
    ): Boolean {
        val tolerance = longestSegmentMs.coerceIn(1_000L, 30_000L)
        return if (archive) {
            val intended = (programmeStopMs - programmeStartMs).coerceAtLeast(0)
            capturedMs > 0 && intended > capturedMs && intended - capturedMs > tolerance
        } else {
            val lateStart = startedAtMs > programmeStartMs && startedAtMs - programmeStartMs > tolerance
            val intended = (programmeStopMs - maxOf(startedAtMs, programmeStartMs)).coerceAtLeast(0L)
            lateStart || (capturedMs > 0 && intended > capturedMs && intended - capturedMs > tolerance)
        }
    }
}

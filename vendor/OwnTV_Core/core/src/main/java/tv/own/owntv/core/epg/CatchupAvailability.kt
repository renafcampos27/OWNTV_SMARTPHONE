package tv.own.owntv.core.epg

/** Declared archive coverage is metadata, never proof that an individual stream is available. */
object CatchupAvailability {
    enum class State { NO_ARCHIVE, FUTURE, OUTSIDE_LIMIT, OUTSIDE_DECLARED, DECLARED, UNKNOWN_WINDOW }
    fun state(catchup: Boolean, days: Int, startMs: Long, nowMs: Long): State = when {
        !catchup -> State.NO_ARCHIVE
        startMs > nowMs -> State.FUTURE
        !GuideHistoryPolicy.canAttemptCatchup(true, startMs, nowMs) -> State.OUTSIDE_LIMIT
        days <= 0 -> State.UNKNOWN_WINDOW
        !GuideHistoryPolicy.canCatchup(true, days, startMs, nowMs) -> State.OUTSIDE_DECLARED
        else -> State.DECLARED
    }
}

package tv.own.owntv.core.update

/** Device-wide capture takes priority over replacing the application. */
internal fun updateRecordingBlocks(runningCount: Int, scheduled: List<Pair<Long, Long>>, now: Long): Boolean =
    runningCount > 0 || scheduled.any { (start, end) -> end > now && start <= now + 120_000L }

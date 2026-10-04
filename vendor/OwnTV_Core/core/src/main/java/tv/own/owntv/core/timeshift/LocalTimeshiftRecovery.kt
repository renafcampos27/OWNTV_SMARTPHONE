package tv.own.owntv.core.timeshift

/** Cadence-based no-progress deadline; transport and retry waits still have their own finite limits. */
internal object LocalTimeshiftRecovery {
    const val ATTEMPTS = 3
    const val MAX_PROVIDER_WAIT_MS = 60_000L
    const val MAX_NO_PROGRESS_MS = 300_000L
    fun noProgressMs(targetDurationSecs: Double): Long {
        val cadence = targetDurationSecs.takeIf { it.isFinite() && it > 0 } ?: 6.0
        return (cadence * 3500.0).coerceIn(45_000.0, MAX_NO_PROGRESS_MS.toDouble()).toLong()
    }
}

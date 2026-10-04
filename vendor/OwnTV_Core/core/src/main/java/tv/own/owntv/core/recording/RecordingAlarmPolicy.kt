package tv.own.owntv.core.recording

/** Permission can change between inspection and arming; immediately preserve an inexact wakeup. */
internal object RecordingAlarmPolicy {
    fun arm(canBeExact: () -> Boolean, exact: () -> Unit, inexact: () -> Unit): Result<Unit> = runCatching {
        val allowed = try { canBeExact() } catch (_: SecurityException) { false }
        if (!allowed) inexact()
        else try { exact() } catch (_: SecurityException) { inexact() }
    }
}

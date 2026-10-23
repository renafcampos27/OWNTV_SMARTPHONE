package tv.own.owntv.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Expired/cancelled queued reads never enter JNI; an in-flight native call cannot be interrupted here. */
internal suspend fun <T> readNativeValue(
    timeoutMs: Long,
    execute: (Runnable) -> Unit,
    current: () -> Boolean,
    read: () -> T,
): T? {
    val result = CompletableDeferred<T?>()
    val queued = runCatching {
        execute(Runnable {
            if (!result.isActive || !current()) result.complete(null)
            else result.complete(runCatching(read).getOrNull())
        })
    }.isSuccess
    if (!queued) return null
    return try {
        withTimeoutOrNull(timeoutMs) { result.await() }.takeIf { current() }
    } finally { result.cancel() }
}

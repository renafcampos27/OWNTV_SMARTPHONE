package tv.own.owntv.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Check immediately; wait only while old resources remain, without a post-close margin. */
internal object SourceDrain {
    const val HANDOVER_RECHECK_MS = 200L

    suspend fun await(
        recheckMs: Long = 20L,
        awaitChange: (suspend (Long) -> Unit)? = null,
        hasPending: () -> Boolean,
    ): Boolean {
        require(recheckMs in 1L..HANDOVER_RECHECK_MS)
        return withTimeoutOrNull(1500L) {
            while (hasPending()) {
                if (awaitChange == null) delay(recheckMs) else awaitChange(recheckMs)
            }
            true
        } ?: false
    }
}

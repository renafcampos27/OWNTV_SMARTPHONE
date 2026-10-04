package tv.own.owntv.core.recording

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A failed or cancelled guide import must not change recording timers. */
object RecordingGuideUpdate {
    suspend fun <T> run(
        refresh: suspend () -> T,
        reconcile: suspend () -> Unit,
        onReconcileFailure: (Exception) -> Unit,
    ): T {
        val result = refresh()
        currentCoroutineContext().ensureActive()
        try {
            reconcile()
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            // The feed was imported successfully; an alarm failure must not label it a failed sync.
            onReconcileFailure(e)
        }
        return result
    }
}

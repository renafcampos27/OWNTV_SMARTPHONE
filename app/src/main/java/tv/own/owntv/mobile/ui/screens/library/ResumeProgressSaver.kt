package tv.own.owntv.mobile.ui.screens.library

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import tv.own.owntv.core.model.MediaType

/** This timer owns only periodic resume writes; explicit final saves belong to stop/switch paths. */
internal suspend fun saveResumeWhileActive(
    playing: Flow<VodPlayback?>,
    wait: suspend () -> Unit = { delay(5_000L) },
    save: suspend () -> Unit,
) {
    playing.collectLatest { item ->
        if (item == null || item.mediaType == MediaType.LIVE) return@collectLatest
        while (true) {
            wait()
            save()
        }
    }
}

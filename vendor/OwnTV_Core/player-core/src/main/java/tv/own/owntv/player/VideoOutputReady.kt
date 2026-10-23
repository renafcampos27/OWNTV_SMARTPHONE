package tv.own.owntv.player

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

/** No polling or fixed start delay. Audio-only playback deliberately does not require a video view. */
internal suspend fun <S : Any> awaitVideoOutput(
    surfaces: StateFlow<S?>,
    audioOnly: StateFlow<Boolean>,
    valid: (S) -> Boolean,
) {
    combine(surfaces, audioOnly) { surface, audio -> audio || (surface != null && valid(surface)) }.first { it }
}

/** A terminal source loses callback ownership before releasing loaders or publishing its error. */
internal fun retireTerminalSource(
    ownership: TuneOwnership,
    expected: TuneToken,
    confirmedPlayback: Boolean,
    releaseSource: () -> Unit,
): Boolean {
    if (ownership.current != expected || ownership.suspended || confirmedPlayback) return false
    ownership.suspendCurrent()
    releaseSource()
    return true
}

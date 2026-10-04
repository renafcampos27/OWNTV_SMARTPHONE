package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * The wall clock, as something a screen can be drawn from.
 *
 * `System.currentTimeMillis()` read inside a composable is answered once and then frozen until
 * something *else* causes a recomposition. Two places in this app are about the passage of time and
 * were drawn that way: the Guide's "On now" list, whose progress bars stopped where they were when
 * the screen opened, and the player's live timeline, whose ticks are positioned against the live
 * edge. Leave either on screen for an hour and it still describes the hour before.
 *
 * Thirty seconds, not one: a programme is half an hour long and a bar that moves by a pixel a minute
 * is smooth enough. Recomposing a list of channels once a second would cost far more than it shows.
 */
@Composable
fun rememberClockTick(periodMs: Long = 30_000L): State<Long> {
    val tick = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            tick.longValue = System.currentTimeMillis()
        }
    }
    return tick
}

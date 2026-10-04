package tv.own.owntv.mobile.ui.player

import tv.own.owntv.mobile.ui.theme.LocalAccentOnVideo
import tv.own.owntv.mobile.ui.components.MobileIcons
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import org.koin.compose.koinInject
import tv.own.owntv.mobile.R
import tv.own.owntv.player.ScreenOff
import tv.own.owntv.player.SleepTimer
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SheetScroll
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.MobileSheetShape

/** The artwork tile, big enough to read a channel logo on and small enough for landscape. */
private val ARTWORK_SIZE = 132.dp

/** Where the bars sit while the controls are up: between the top bar and the transport buttons. */
private val COMPACT_TOP_PADDING = 84.dp

private val WAVE_PEAKS = listOf(0.5f, 0.9f, 0.65f, 1f, 0.45f)
private val WAVE_HEIGHT = 44.dp
private val WAVE_WIDTH = 56.dp

/** How tall a bar stands when nothing is moving it: short, but never a bare line. */
private const val WAVE_RESTING = 0.18f

/**
 * What the player shows where the picture would be, while there is no picture.
 *
 * Dropping the video is the single biggest thing a phone can do for its battery and its data
 * allowance, and it should not cost the user the player they were already in: the controls, the
 * gestures and the way back to video all stay exactly where they were. Only the black rectangle
 * changes — into the artwork, what is playing, and something that moves so the stream plainly is
 * still running.
 *
 * It is told what to say rather than which tuner to ask, for the reason [MiniPlayer] is.
 */
@Composable
fun AudioOnlyBackdrop(
    title: String,
    playing: Boolean,
    /** True while the player's own controls are up, which is what the middle of the screen is for. */
    compact: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    artworkUrl: String? = null,
    programmeEndMs: Long? = null,
    sleepTimer: SleepTimer = koinInject(),
) {
    val remaining by sleepTimer.remainingMs.collectAsStateWithLifecycle()
    var timerSheet by remember { mutableStateOf(false) }

    Box(
        modifier.fillMaxSize().background(Color.Black),
        // With the controls up the transport buttons own the middle of the screen, so what is playing
        // moves out from under them rather than being drawn through them.
        contentAlignment = if (compact) Alignment.TopCenter else Alignment.Center,
    ) {
        Column(
            Modifier.padding(
                horizontal = MobileDimens.ScreenPaddingH,
                vertical = if (compact) COMPACT_TOP_PADDING else 0.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) Box(
                Modifier
                    .size(ARTWORK_SIZE)
                    .clip(MobileSheetShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                // A channel logo is the only artwork a live stream has, and plenty of them have none
                // at all — hence a glyph underneath rather than an empty square.
                Icon(
                    MobileIcons.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White.copy(alpha = 0.6f),
                )
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(MobileDimens.GapMedium),
                )
            }
            if (!compact) Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MobileDimens.GapMedium),
            )
            subtitle.takeIf { !compact }?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Waveform(
                active = playing,
                modifier = Modifier.padding(top = MobileDimens.GapMedium),
            )
            // The one thing the player's own bar does not already offer, and the reason most people
            // drop the picture in the first place. The bar itself covers this spot, so it waits for
            // the controls to go away — which they do on their own after a few seconds.
            if (!compact) TextButton(onClick = { timerSheet = true }) {
                Icon(MobileIcons.Bedtime, contentDescription = null, tint = Color.White)
                Text(
                    text = remaining?.let {
                        stringResource(R.string.player_sleep_timer_remaining, minutesLabel(it))
                    } ?: stringResource(R.string.player_sleep_timer),
                    color = Color.White,
                    modifier = Modifier.padding(start = MobileDimens.GapSmall),
                )
            }
        }
    }

    if (timerSheet) {
        SleepTimerSheet(programmeEndMs = programmeEndMs, onDismiss = { timerSheet = false })
    }
}

/** The sleep timer's own picker — reached from this screen and from the floating window's menu. */
@Composable
fun SleepTimerSheet(
    programmeEndMs: Long?,
    onDismiss: () -> Unit,
    sleepTimer: SleepTimer = koinInject(),
) {
    val remaining by sleepTimer.remainingMs.collectAsStateWithLifecycle()
    // Only while a movie or episode plays; live and catch-up have "End of programme" instead.
    val endKind = remember { sleepTimer.itemEndKind() }
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.player_sleep_timer)) {
        SheetScroll {
            if (remaining != null) {
                MobileListRow(
                    title = stringResource(R.string.common_off),
                    onClick = {
                        sleepTimer.cancel()
                        onDismiss()
                    },
                )
            }
            SleepTimer.CHOICES_MINUTES.forEach { minutes ->
                MobileListRow(
                    title = stringResource(R.string.player_duration_minutes, minutes),
                    onClick = {
                        sleepTimer.start(minutes * 60_000L)
                        onDismiss()
                    },
                )
            }
            // Only with a guide behind it, and only while that programme is still on: "end of
            // programme" with no programme, or one already over, is a button that stops the stream at once.
            programmeEndMs?.takeIf { it > System.currentTimeMillis() }?.let { endMs ->
                MobileListRow(
                    title = stringResource(R.string.player_sleep_timer_end_of_programme),
                    onClick = {
                        sleepTimer.start(endMs - System.currentTimeMillis())
                        onDismiss()
                    },
                )
            }
            endKind?.let { kind ->
                MobileListRow(
                    title = stringResource(if (kind == SleepTimer.EndKind.EPISODE) R.string.player_sleep_timer_end_of_episode else R.string.player_sleep_timer_end_of_movie),
                    onClick = {
                        sleepTimer.startUntilItemEnd()
                        onDismiss()
                    },
                )
            }
            ScreenOffRow()
        }
    }
}

/**
 * "Also turn off the screen": on is the system grant itself ([ScreenOff]), re-read after the system
 * screen answers rather than stored. Turning it off gives the grant back.
 */
@Composable
private fun ScreenOffRow(screenOff: ScreenOff = koinInject()) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(screenOff.isAllowed()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { allowed = screenOff.isAllowed() }
    val toggle: () -> Unit = {
        if (allowed) {
            screenOff.revoke()
            allowed = false
        } else {
            runCatching { ask.launch(screenOff.requestIntent()) }.onFailure {
                Toast.makeText(context, R.string.player_sleep_timer_screen_off_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
    MobileListRow(
        title = stringResource(R.string.player_sleep_timer_screen_off),
        trailing = { Switch(checked = allowed, onCheckedChange = { toggle() }) },
        onClick = toggle,
    )
}

/** The countdown reads in whole minutes, rounded up — see [SleepTimer.minutesLeft]. */
@Composable
private fun minutesLabel(remainingMs: Long): String =
    stringResource(R.string.player_duration_minutes, SleepTimer.minutesLeft(remainingMs))

/**
 * Something moving, so a screen with no picture still looks like it is playing.
 *
 * The television's equaliser, drawn the same way: bars standing on a baseline and dancing on their
 * own clocks, flattened to a stub while paused. Centre-expanding bars read as a level meter rather
 * than an equaliser, which is why this is a canvas and not a row of boxes.
 *
 * Shared by the mini player's bar and the full screen backdrop a radio channel gets — the same black
 * rectangle and the same problem.
 *
 * **It moves even with Reduce animations on, which the television's does too.** This is not
 * decoration: it is the only thing on a screen with no picture that says the sound is still coming.
 * Frozen it reads as stopped, which is the exact confusion the component exists to prevent — so the
 * one thing it must never do is hold still while something is playing.
 */
@Composable
fun Waveform(active: Boolean, modifier: Modifier = Modifier) {
    // Paused draws flat bars, so no transition exists then: nothing ticks frames behind a still stub.
    val transition = if (active) rememberInfiniteTransition(label = "waveform") else null
    // Each bar swings on its own clock, or the row would pump as one block.
    val heights = transition?.let { WAVE_PEAKS.mapIndexed { index, peak ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = peak,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 420 + index * 90, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bar$index",
        )
    } }
    // Over the picture — or, here, over where the picture would be. The scheme's primary is a dark
    // accent on a dark scene in the light theme.
    val color = LocalAccentOnVideo.current
    Canvas(modifier.size(width = WAVE_WIDTH, height = WAVE_HEIGHT)) {
        val gap = size.width * 0.12f
        val barWidth = (size.width - gap * (WAVE_PEAKS.size - 1)) / WAVE_PEAKS.size
        WAVE_PEAKS.indices.forEach { index ->
            // Flat only when the stream really is paused, which is the one thing it should say.
            val fraction = heights?.get(index)?.value ?: WAVE_RESTING
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

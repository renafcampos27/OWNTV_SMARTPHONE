package tv.own.owntv.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.cast.CastPlaybackEngine
import tv.own.owntv.mobile.cast.CastRouteButton
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.theme.LocalAccentOnVideo
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.MobileSheetShape
import tv.own.owntv.player.describe

/** The artwork tile, the same size the sound-only backdrop uses. */
private val ARTWORK_SIZE = 132.dp

/** How much one press of the volume buttons moves the receiver, out of a hundred. */
private const val VOLUME_STEP = 5

/**
 * What the player shows while the picture is on the television.
 *
 * It covers the whole player rather than sitting beside it, deliberately: every control underneath is
 * wired to the engine on this phone, and while casting there is no such engine — a zoom, a subtitle
 * track or an engine swap would be a button that quietly does nothing. So the screen becomes what is
 * actually true: what is playing, where it is playing, and the three or four things the receiver can
 * really be told to do.
 *
 * The route button stays in the corner, because stopping the cast is reached through it.
 */
@Composable
fun CastStage(
    engine: CastPlaybackEngine,
    deviceName: String?,
    title: String,
    subtitle: String?,
    artworkUrl: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val res = LocalResources.current
    val isPlaying by engine.isPlaying.collectAsStateWithLifecycle()
    val buffering by engine.buffering.collectAsStateWithLifecycle()
    val error by engine.error.collectAsStateWithLifecycle()
    val position by engine.position.collectAsStateWithLifecycle()
    val duration by engine.duration.collectAsStateWithLifecycle()
    val live = engine.isLiveContent

    Box(modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().systemBarsPadding().padding(MobileDimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CtrlButton(MobileIcons.ArrowBack, stringResource(R.string.content_close), onBack)
            Box(Modifier.weight(1f))
            CastRouteButton(light = true)
        }

        Column(
            Modifier.align(Alignment.Center).padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(ARTWORK_SIZE)
                    .clip(MobileSheetShape)
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    MobileIcons.Tv,
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
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = MobileDimens.GapMedium),
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The one line that answers "why is my phone black?" — and the reason the receiver's name
            // is worth carrying all the way from the session manager.
            deviceName?.let {
                Text(
                    text = stringResource(R.string.player_cast_playing_on, it),
                    style = MaterialTheme.typography.labelLarge,
                    color = LocalAccentOnVideo.current,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = MobileDimens.GapMedium),
                )
            }

            val failure = error
            if (failure != null) {
                PlayerToast(Modifier.padding(top = MobileDimens.GapMedium)) {
                    Text(
                        text = failure.describe { id, args -> res.getString(id, *args.toTypedArray()) },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = engine::retry) { Text(stringResource(R.string.common_retry)) }
                }
                return@Column
            }

            // A recording has a length to scrub through; a live channel has neither an end nor an
            // archive the receiver can reach, so it gets no bar rather than a bar that lies.
            if (!live && duration > 0L) {
                CastProgress(position = position, duration = duration, onSeekTo = { target ->
                    engine.seekBy(target - engine.position.value)
                })
            }

            Row(
                Modifier.padding(top = MobileDimens.GapMedium),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportCapsule {
                    CtrlButton(
                        MobileIcons.VolumeOff,
                        stringResource(R.string.player_tool_volume),
                        { engine.adjustVolume(-VOLUME_STEP) },
                    )
                    if (!live) CtrlButton(
                        MobileIcons.FastRewind,
                        stringResource(R.string.player_skip_back),
                        { engine.seekBy(-SKIP_MS) },
                    )
                    if (buffering && !isPlaying) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        CtrlButton(
                            if (isPlaying) MobileIcons.Pause else MobileIcons.PlayArrow,
                            stringResource(R.string.settings_remote_action_play_pause),
                            engine::togglePlayPause,
                        )
                    }
                    if (!live) CtrlButton(
                        MobileIcons.FastForward,
                        stringResource(R.string.player_skip_forward),
                        { engine.seekBy(SKIP_MS) },
                    )
                    CtrlButton(
                        MobileIcons.VolumeUp,
                        stringResource(R.string.player_tool_volume),
                        { engine.adjustVolume(VOLUME_STEP) },
                    )
                }
            }
        }
    }
}

/** Fixed, as the notification's and the floating window's are: this is not where a step is chosen. */
private const val SKIP_MS = 10_000L

/** Where the recording is up to on the other screen, and a way to move it. */
@Composable
private fun CastProgress(position: Long, duration: Long, onSeekTo: (Long) -> Unit) {
    // While a finger is down the bar shows the finger, not the receiver: a status update arriving
    // mid-drag would otherwise snatch the handle back to where the television still is.
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = MobileDimens.GapMedium)) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeekTo((it * duration).toLong()) }
                dragging = null
            },
            colors = SliderDefaults.colors(
                thumbColor = LocalAccentOnVideo.current,
                activeTrackColor = LocalAccentOnVideo.current,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatTimestamp((fraction * duration).toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
            Text(
                text = formatTimestamp(duration),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

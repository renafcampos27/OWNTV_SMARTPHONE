package tv.own.owntv.mobile.ui.player

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.theme.MobileCardShape
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.screens.live.LiveTuner
import tv.own.owntv.player.OwnTVPlayer
import tv.own.owntv.player.PlaybackEngine
import tv.own.owntv.player.ZoomMode

/** How wide the window is at each of the three sizes the pinch moves between. */
private val WINDOW_WIDTHS = mapOf(
    SettingsRepository.PipSize.SMALL to 132.dp,
    SettingsRepository.PipSize.MEDIUM to 176.dp,
    SettingsRepository.PipSize.LARGE to 232.dp,
)

/** How far a pinch has to travel before it counts as a size change rather than a wobble. */
private const val PINCH_IN = 0.85f
private const val PINCH_OUT = 1.18f

/** A drag that ends this far down, having gone nowhere sideways, means "get rid of it". */
private const val DISMISS_DRAG_PX = 180f

/** How long the two buttons stay after a tap. The same three seconds the full player uses. */
private const val CONTROLS_MS = 3_000L

private const val LONG_PRESS_MS = 500L
private const val DOUBLE_TAP_MS = 280L

private val MARGIN = 12.dp

/**
 * How far from square the window is allowed to get.
 *
 * The window is the picture's own shape, the way the system's own floating window is — but a stream
 * that reports something absurd must not turn it into a letterbox slot or a tower.
 */
private const val MIN_WINDOW_ASPECT = 0.6f
private const val MAX_WINDOW_ASPECT = 2.5f

/** Four buttons have to fit across the narrowest window, so they are smaller than a normal one. */
private val BUTTON_SIZE = 32.dp
private val BUTTON_ICON = 18.dp

/**
 * What is playing, in a little window the user drags around the app.
 *
 * The phone's answer to the television's docked row: a phone screen has no spare band to give up,
 * and a window put where it suits the user covers whatever they are least interested in. It is the
 * same engine and the same stream as the full screen player — moving between them costs nothing, and
 * closing this is the only thing here that actually stops playback.
 *
 * Every gesture it takes has a button elsewhere in the app; nothing is reachable *only* by dragging.
 * The position is deliberately not stored (Rule X-B): a window the user drags has no setting to set.
 */
@Composable
fun FloatingMiniPlayer(
    /** Handed to [VideoStage], which resolves the live engine itself — see its own comment. */
    player: OwnTVPlayer,
    /**
     * The engine actually holding the stream. Everything this window READS and DRIVES comes from
     * here: live plays on ExoPlayer, where `player` is a stopped mpv, so the window's play/pause
     * button did nothing and its glyph said "paused" over a moving picture.
     */
    engine: PlaybackEngine,
    title: String,
    onExpand: () -> Unit,
    onStop: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    /** A live channel has nothing to skip through, so it gets the expand button in their place. */
    isLive: Boolean = false,
    artworkUrl: String? = null,
    settings: SettingsRepository = koinInject(),
    tuner: LiveTuner = koinInject(),
) {
    val playing by engine.isPlaying.collectAsStateWithLifecycle()
    val audioOnly by engine.audioOnlyMedia.collectAsStateWithLifecycle()
    // The picture's shape is the one thing [PlaybackEngine] cannot answer — neither engine publishes
    // it through the shared interface — so it is asked of the two directly, the way [VideoStage]
    // does. Widening the interface would be a core change this needs nothing else from.
    val liveOnExo by tuner.liveOnExo.collectAsStateWithLifecycle()
    val videoAspect by (if (liveOnExo) tuner.exoEngine.videoAspect else player.videoAspect)
        .collectAsStateWithLifecycle()
    val step by engine.seekStepMs.collectAsStateWithLifecycle()
    val size by settings.pipSize.collectAsStateWithLifecycle(SettingsRepository.PipSize.MEDIUM)
    val snapToEdges by settings.pipSnap.collectAsStateWithLifecycle(true)
    val scope = rememberCoroutineScope()

    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(CONTROLS_MS)
            controlsVisible = false
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val margin = with(density) { MARGIN.toPx() }
        val areaWidth = with(density) { maxWidth.toPx() }
        val areaHeight = with(density) { maxHeight.toPx() }
        var windowWidth by remember { mutableFloatStateOf(0f) }
        var windowHeight by remember { mutableFloatStateOf(0f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }
        var pinch by remember { mutableFloatStateOf(1f) }

        fun maxX() = (areaWidth - windowWidth - margin).coerceAtLeast(margin)
        fun maxY() = (areaHeight - windowHeight - margin).coerceAtLeast(margin)

        // Bottom right to begin with, and re-placed whenever the window or the screen changes size —
        // a rotation or a pinch must never leave it half off the edge.
        LaunchedEffect(windowWidth, windowHeight, areaWidth, areaHeight) {
            if (windowWidth == 0f) return@LaunchedEffect
            offsetX = if (offsetX == 0f) maxX() else offsetX.coerceIn(margin, maxX())
            offsetY = if (offsetY == 0f) maxY() else offsetY.coerceIn(margin, maxY())
        }

        // The picture's own shape, not a fixed 16:9 — the same thing the system's floating window
        // does. A 2.39:1 film in a 16:9 window is a small picture with black bands above and below it
        // on a window that is already tiny; shaping the window to the film gives the picture all of
        // it. Sound only has no shape of its own, so it keeps the familiar one.
        val windowAspect = when {
            audioOnly -> 16f / 9f
            else -> (videoAspect ?: (16f / 9f)).coerceIn(MIN_WINDOW_ASPECT, MAX_WINDOW_ASPECT)
        }
        Box(
            Modifier
                .offset { IntOffset(offsetX.toInt(), offsetY.toInt()) }
                .width(WINDOW_WIDTHS.getValue(size))
                .aspectRatio(windowAspect)
                .onSizeChanged {
                    windowWidth = it.width.toFloat()
                    windowHeight = it.height.toFloat()
                }
                .clip(MobileCardShape)
                .background(Color.Black)
                .windowGestures(
                    key = size,
                    onDrag = { pan ->
                        offsetX = (offsetX + pan.x).coerceIn(margin, maxX())
                        offsetY = (offsetY + pan.y).coerceIn(margin, maxY())
                    },
                    onDragEnd = { total ->
                        // Straight down and far enough: the one gesture that stops playback.
                        if (total.y > DISMISS_DRAG_PX && kotlin.math.abs(total.x) < total.y / 2f) {
                            onStop()
                        } else if (snapToEdges) {
                            offsetX = if (offsetX + windowWidth / 2f < areaWidth / 2f) margin else maxX()
                        }
                    },
                    onZoom = { zoom ->
                        pinch *= zoom
                        // One step per pinch: the gesture picks a size, it does not sweep through all
                        // three on the way.
                        val next = when {
                            pinch > PINCH_OUT -> size.larger()
                            pinch < PINCH_IN -> size.smaller()
                            else -> null
                        }
                        if (next != null && next != size) {
                            pinch = 1f
                            scope.launch { settings.setPipSize(next) }
                        }
                    },
                    onTap = { controlsVisible = !controlsVisible },
                    onDoubleTap = onExpand,
                    onLongPress = onMenu,
                ),
        ) {
            if (audioOnly) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // key(...): a SurfaceView that is *resized* keeps the buffer it was first given, and
                // the engine goes on drawing the old size into the corner of it — the picture ends up
                // in the top left with black down the right and along the bottom. Rebuilding the view
                // when the window changes shape or size gives it a surface that matches.
                key(size, windowAspect) {
                    // FIT, not the zoom the user set for full screen — see [VideoStage]. With the
                    // window already shaped to the picture, FIT fills it exactly, edge to edge.
                    VideoStage(
                        player = player,
                        modifier = Modifier.fillMaxSize(),
                        zoomOverride = ZoomMode.FIT,
                    )
                }
            }
            if (controlsVisible) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A live stream has nothing to skip through, so the space goes to the one thing
                    // the window cannot otherwise offer without a gesture: getting back to full screen.
                    if (isLive) {
                        WindowButton(MobileIcons.OpenInFull, R.string.player_pip_expand, onExpand)
                    } else {
                        WindowButton(MobileIcons.FastRewind, R.string.player_skip_back) {
                            engine.seekBy(-step)
                        }
                    }
                    WindowButton(
                        icon = if (playing) MobileIcons.Pause else MobileIcons.PlayArrow,
                        labelRes = R.string.settings_remote_action_play_pause,
                        onClick = engine::togglePlayPause,
                    )
                    if (!isLive) {
                        WindowButton(MobileIcons.FastForward, R.string.player_skip_forward) {
                            engine.seekBy(step)
                        }
                    }
                    WindowButton(MobileIcons.Close, R.string.content_close, onStop)
                }
            }
        }
    }
}

/** One of the window's buttons: small enough that four of them fit across the narrowest size. */
@Composable
private fun WindowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(BUTTON_SIZE)) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(labelRes),
            tint = Color.White,
            modifier = Modifier.size(BUTTON_ICON),
        )
    }
}

/**
 * Six gestures on one small target, in one loop.
 *
 * Compose's ready-made detectors cannot be stacked here: the drag detector consumes the movement a
 * pinch is made of, and the tap detector would call long-press half a second into a drag. One loop
 * decides what the finger is doing and then only reports that.
 */
private fun Modifier.windowGestures(
    key: Any?,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit,
    onZoom: (Float) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
) = pointerInput(key) {
    var lastTapMs = 0L
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        var moved = false
        var longPressed = false
        val downMs = System.currentTimeMillis()
        while (true) {
            val left = LONG_PRESS_MS - (System.currentTimeMillis() - downMs)
            val event = if (moved || longPressed || left <= 0L) {
                awaitPointerEvent()
            } else {
                withTimeoutOrNull(left) { awaitPointerEvent() }
            }
            if (event == null) {
                // Held still for half a second: the menu, and the finger stops meaning anything else.
                longPressed = true
                onLongPress()
                continue
            }
            if (event.changes.none { it.pressed }) break
            if (longPressed) continue
            if (event.changes.count { it.pressed } > 1) {
                val zoom = event.calculateZoom()
                if (zoom != 1f) {
                    moved = true
                    onZoom(zoom)
                    event.changes.forEach { it.consume() }
                }
            } else {
                val pan = event.calculatePan()
                total += pan
                if (!moved && total.getDistance() > viewConfiguration.touchSlop) moved = true
                if (moved) {
                    onDrag(pan)
                    event.changes.forEach { it.consume() }
                }
            }
        }
        when {
            longPressed -> Unit
            moved -> onDragEnd(total)
            else -> {
                val now = System.currentTimeMillis()
                // The first tap shows the controls and the second expands: no waiting to find out
                // which one it was, because showing the buttons is harmless either way.
                if (now - lastTapMs < DOUBLE_TAP_MS) {
                    lastTapMs = 0L
                    onDoubleTap()
                } else {
                    lastTapMs = now
                    onTap()
                }
            }
        }
    }
}

private fun SettingsRepository.PipSize.larger() = when (this) {
    SettingsRepository.PipSize.SMALL -> SettingsRepository.PipSize.MEDIUM
    else -> SettingsRepository.PipSize.LARGE
}

private fun SettingsRepository.PipSize.smaller() = when (this) {
    SettingsRepository.PipSize.LARGE -> SettingsRepository.PipSize.MEDIUM
    else -> SettingsRepository.PipSize.SMALL
}

/**
 * The floating window's long-press menu.
 *
 * Deliberately its own short sheet rather than the channel list's menu: everything on it is about
 * the *window* and the stream inside it, and pushing window actions through the content menu would
 * change the order the user arranged their content actions in.
 */
@Composable
fun FloatingWindowMenu(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAudioOnly: () -> Unit,
    onSleepTimer: () -> Unit,
    onExpand: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss) {
        MobileListRow(
            title = stringResource(
                if (isFavorite) R.string.content_remove_favourite else R.string.content_add_favourite,
            ),
            leading = {
                Icon(
                    imageVector = if (isFavorite) MobileIcons.Star else MobileIcons.StarBorder,
                    contentDescription = null,
                )
            },
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
        )
        MobileListRow(
            title = stringResource(R.string.player_tool_audio_only),
            leading = { Icon(MobileIcons.MusicNote, contentDescription = null) },
            onClick = {
                onAudioOnly()
                onDismiss()
            },
        )
        MobileListRow(
            title = stringResource(R.string.player_sleep_timer),
            leading = { Icon(MobileIcons.Bedtime, contentDescription = null) },
            onClick = {
                onDismiss()
                onSleepTimer()
            },
        )
        MobileListRow(
            title = stringResource(R.string.player_pip_expand),
            leading = { Icon(MobileIcons.OpenInFull, contentDescription = null) },
            onClick = {
                onDismiss()
                onExpand()
            },
        )
        MobileListRow(
            title = stringResource(R.string.content_close),
            leading = { Icon(MobileIcons.Close, contentDescription = null) },
            onClick = {
                onDismiss()
                onStop()
            },
        )
    }
}

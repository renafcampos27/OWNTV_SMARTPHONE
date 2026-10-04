package tv.own.owntv.mobile.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.screens.guide.rememberGuideTimeFormat
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.mobile.ui.theme.SquircleShape
import tv.own.owntv.player.LIVE_WINDOW_SEC
import tv.own.owntv.player.LiveProgramme
import tv.own.owntv.player.liveGapSpans
import tv.own.owntv.player.liveTicks
import tv.own.owntv.player.offsetFrac
import tv.own.owntv.player.programmeAt
import java.util.Date
import kotlin.math.roundToInt

/**
 * The scrubber, drawn rather than assembled from an M3 [androidx.compose.material3.Slider].
 *
 * A stock slider can show one value and nothing else. An instrument has to show three at once — how
 * much has played, how much the engine actually holds, and where the finger is about to land — and
 * none of those fit inside a `Slider`'s single thumb. So this draws its own three layers.
 *
 * Everything about it is sized for a thumb rather than a focus ring: the playhead is 30 dp of halo
 * over a 16 dp core, and the track grows *upward* while dragging, because the finger is sitting on
 * exactly the part of the bar that would otherwise be doing the growing.
 */

/** Resting and dragging track heights. */
private val TrackIdle = 4.dp
private val TrackActive = 8.dp

/** How far the whole bar lifts out from under the finger while it is being dragged. */
private val Rise = 6.dp

private val HaloSize = 30.dp
private val CoreSize = 16.dp

/** The fixed end column, so the bar never shifts sideways when a digit changes width. */
private val CapWidth = 64.dp

/** The bar's own row height — the touch target, of which the track is only the visible part. */
private val BarHeight = 36.dp

private const val TRACK_MS = 140

/** The unplayed track, and the buffer ghost drawn on it. */
private val TrackColour = Color.White.copy(alpha = 0.22f)
private val TrackColourActive = Color.White.copy(alpha = 0.40f)
private val BufferColour = Color.White.copy(alpha = 0.28f)

private val BubbleShape = SquircleShape(10.dp)
private val BubbleFill = Color.Black.copy(alpha = 0.90f)

/** How far above the track the bubble floats — clear of the thumb that is covering the bar. */
private val BubbleLift = 34.dp

/**
 * Position along the parent's width, clamped so the thing placed never hangs off either end.
 *
 * A `fillMaxWidth(fraction)` box with its content at the end — which is how the television does it —
 * puts half the playhead off the screen at zero and half off at one. On a phone that matters: both
 * ends are where a thumb naturally goes.
 */
private fun Modifier.atFraction(fraction: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val width = constraints.maxWidth
    val x = (fraction * width - placeable.width / 2f).roundToInt()
        .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
    layout(width, placeable.height) { placeable.place(x, 0) }
}

/**
 * The scrub bubble: where letting go would land, and how far that is from here.
 *
 * It follows the finger horizontally and stops at the edges rather than sliding off them, and it is
 * drawn above the bar so the thumb never covers the one number it exists to show.
 */
@Composable
private fun BubbleFrame(fraction: Float, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .atFraction(fraction)
                .graphicsLayer { translationY = -BubbleLift.toPx() }
                .clip(BubbleShape)
                .background(BubbleFill)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
internal fun ScrubBubble(fraction: Float, targetMs: Long, deltaMs: Long?, modifier: Modifier = Modifier) {
    BubbleFrame(fraction, modifier) {
        Text(
                text = formatTimestamp(targetMs),
            style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        if (deltaMs != null && deltaMs != 0L) {
            Text(
                text = formatSignedTimestamp(deltaMs),
                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                color = OnVideo,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** "+1:20" or "−1:20" — the same duration format the rest of the app uses, with its sign in front. */
@Composable
internal fun formatSignedTimestamp(deltaMs: Long): String {
    val magnitude = formatTimestamp(kotlin.math.abs(deltaMs))
    return if (deltaMs < 0) {
        stringResource(R.string.player_time_remaining, magnitude)
    } else {
        stringResource(R.string.common_plus) + magnitude
    }
}

/**
 * The bar itself: track, buffer ghost, played, playhead, and the bubble while it is being moved.
 *
 * @param fraction where the playhead is, 0..1.
 * @param bufferedFraction how far ahead the engine holds data. Never drawn behind the playhead, so a
 *   stale value simply shows nothing rather than a stripe that contradicts the fill.
 * @param dragging whether the value is currently being moved — by this bar or by the screen gesture.
 * @param bubble the target and delta to float above the finger, or null for no bubble.
 */
@Composable
internal fun MobileSeekBar(
    fraction: Float,
    bufferedFraction: Float,
    dragging: Boolean,
    accent: Color,
    bubbleTargetMs: Long?,
    bubbleDeltaMs: Long?,
    onSeekTo: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ms = LocalAnimations.current.scale(TRACK_MS)
    val trackHeight by animateDpAsState(if (dragging) TrackActive else TrackIdle, tween(ms), label = "track")
    val rise by animateFloatAsState(if (dragging) 1f else 0f, tween(ms), label = "rise")
    var barWidth by remember { mutableIntStateOf(0) }

    Box(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .onSizeChanged { barWidth = it.width }
            .pointerInput(barWidth) {
                if (barWidth <= 0) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { onDrag((it.x / barWidth).coerceIn(0f, 1f)) },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onDrag((change.position.x / barWidth).coerceIn(0f, 1f))
                    },
                )
            }
            .pointerInput(barWidth) {
                if (barWidth <= 0) return@pointerInput
                detectTapGestures { onSeekTo((it.x / barWidth).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationY = -Rise.toPx() * rise },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(50))
                    .background(if (dragging) TrackColourActive else TrackColour),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(bufferedFraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(BufferColour),
                )
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
            }
            Box(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .atFraction(fraction)
                        .size(HaloSize)
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(CoreSize).clip(RoundedCornerShape(50)).background(accent))
                }
            }
            if (bubbleTargetMs != null) {
                ScrubBubble(fraction = fraction, targetMs = bubbleTargetMs, deltaMs = bubbleDeltaMs)
            }
        }
    }
}

/** How far a tick rises out of the track, resting and while the bar is being dragged. */
private val TickIdle = 10.dp
private val TickActive = 12.dp

/** The live edge's own mark, the same lit red the badge and the pill carry. */
private val LiveDot = Color(0xFFFF4D4D)

/**
 * The live timeline: the last two hours of a catch-up channel, as programmes rather than as a smear.
 *
 * Same geometry as the television's, because it is literally the same code — `LiveTimelineGeometry`
 * lives in `player-core` and both apps read their ticks out of it. What differs is only the input: a
 * finger drags it, so it carries the seek bar's thumb-sized playhead and its bubble instead of a
 * focus ring, and the bubble says *what* is at that point — "20:54 · Premier League" — rather than
 * only how far back it is.
 *
 * @param offsetSec seconds behind the live edge; 0 is live.
 * @param programmes the channel's guide window. Empty is normal and draws a plain bar.
 * @param liveEdgeMs wall clock at the right-hand end.
 */
@Composable
internal fun MobileLiveTimeline(
    offsetSec: Int,
    programmes: List<LiveProgramme>,
    liveEdgeMs: Long,
    accent: Color,
    onScrub: (deltaSec: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** N4 — wall-clock holes in a saved copy, drawn as dark stretches the rewind jumps over. */
    gaps: () -> List<LongRange> = { emptyList() },
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(1f) }
    val fraction = if (dragging) dragFraction else offsetFrac(offsetSec)
    // The offset the bar is showing — the dragged one while a finger is down, the real one otherwise.
    val shownOffsetSec = if (dragging) {
        ((1f - dragFraction) * LIVE_WINDOW_SEC).toInt()
    } else {
        offsetSec
    }
    // Keyed on the minute: the live edge moves every second, and no tick on a two-hour bar can move
    // visibly in less than that.
    val ticks = remember(programmes, liveEdgeMs / 60_000L) { liveTicks(programmes, liveEdgeMs) }
    val here = remember(programmes, liveEdgeMs / 60_000L, shownOffsetSec) {
        programmeAt(programmes, liveEdgeMs, shownOffsetSec)
    }
    val hereStartFrac = here?.let { offsetFrac(((liveEdgeMs - it.startMs) / 1000L).toInt()) }

    val ms = LocalAnimations.current.scale(TRACK_MS)
    val trackHeight by animateDpAsState(if (dragging) TrackActive else TrackIdle, tween(ms), label = "track")
    val rise by animateFloatAsState(if (dragging) 1f else 0f, tween(ms), label = "rise")
    var barWidth by remember { mutableIntStateOf(0) }

    fun settle() {
        dragging = false
        onScrub(((1f - dragFraction) * LIVE_WINDOW_SEC).toInt() - offsetSec)
    }

    // A time axis is physical: the live edge is on the right in every language, because that is where
    // the bar's own arithmetic puts it.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxWidth()
                .height(BarHeight)
                .onSizeChanged { barWidth = it.width }
                .pointerInput(barWidth) {
                    if (barWidth <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; dragFraction = (it.x / barWidth).coerceIn(0f, 1f) },
                        onDragEnd = { settle() },
                        onDragCancel = { dragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            dragFraction = (change.position.x / barWidth).coerceIn(0f, 1f)
                        },
                    )
                }
                .pointerInput(barWidth) {
                    if (barWidth <= 0) return@pointerInput
                    detectTapGestures { dragFraction = (it.x / barWidth).coerceIn(0f, 1f); settle() }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = -Rise.toPx() * rise },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(trackHeight)
                        .clip(RoundedCornerShape(50))
                        .background(if (dragging) TrackColourActive else TrackColour),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(accent),
                    )
                }
                // N4 — where the saved copy has no picture (the connection dropped): dark, so a jump over
                // it is expected rather than a surprise.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    liveGapSpans(gaps(), liveEdgeMs).forEach { span ->
                        Box(Modifier.fillMaxWidth(span.endFrac), contentAlignment = Alignment.CenterEnd) {
                            Box(
                                Modifier
                                    .fillMaxWidth((span.endFrac - span.startFrac) / span.endFrac)
                                    .height(trackHeight)
                                    .background(Color.Black.copy(alpha = 0.75f)),
                            )
                        }
                    }
                }
                // Programme boundaries. The one the picture is inside is brighter — that is the edge
                // to rewind to for a programme from its start. Matched on position, not on title, so
                // a show that runs twice inside the window lights only the boundary being watched.
                Box(
                    Modifier.fillMaxWidth().height(TickActive),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    ticks.forEach { tick ->
                        if (tick.startFrac <= 0f || tick.startFrac >= 1f) return@forEach
                        val current = tick.startFrac == hereStartFrac
                        Box(Modifier.fillMaxWidth(tick.startFrac), contentAlignment = Alignment.CenterEnd) {
                            Box(
                                Modifier
                                    .width(if (current) 2.dp else 1.dp)
                                    .height(if (dragging) TickActive else TickIdle)
                                    .background(Color.White.copy(alpha = if (current) 0.85f else 0.35f)),
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(LiveDot))
                }
                Box(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier
                            .atFraction(fraction)
                            .size(HaloSize)
                            .clip(RoundedCornerShape(50))
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(CoreSize).clip(RoundedCornerShape(50)).background(accent))
                    }
                }
                if (dragging) {
                    val clock = rememberGuideTimeFormat()
                    val atMs = liveEdgeMs - shownOffsetSec.coerceAtLeast(0) * 1000L
                    BubbleFrame(fraction) {
                        Text(
                            text = when {
                                // The name says far more about whether to stop here than "−12:04" does.
                                here != null -> stringResource(
                                    R.string.player_live_scrub_at,
                                    clock.format(Date(atMs)),
                                    here.title,
                                )
                                shownOffsetSec <= 1 -> stringResource(R.string.player_live)
                                else -> stringResource(
                                    R.string.player_live_offset,
                                    formatTimestamp(shownOffsetSec * 1000L),
                                )
                            },
                            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 240.dp),
                        )
                    }
                }
            }
        }
    }
}

/** An end cap: elapsed at one end, remaining at the other, each in a column of its own fixed width. */
@Composable
internal fun TimeCap(text: String, alignment: Alignment.Horizontal) {
    Column(Modifier.width(CapWidth), horizontalAlignment = alignment) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
            color = OnVideo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

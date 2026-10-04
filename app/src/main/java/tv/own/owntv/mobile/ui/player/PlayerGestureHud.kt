package tv.own.owntv.mobile.ui.player

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.theme.LocalAccentOnVideo
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.player.ZoomMode

/**
 * What a gesture is currently saying, as a value rather than as a sentence.
 *
 * The readout used to be a bare string on a grey rectangle, which meant every gesture got the same
 * shape no matter what it was doing — a percentage, a zoom and a skip all read alike. Making it a
 * type lets each one be drawn as the thing it is: a level as a level, a skip as a direction.
 */
internal sealed interface GestureFeedback {
    /** Volume or brightness: a value between 0 and 100, on the side of the screen it came from. */
    data class Level(val volume: Boolean, val percent: Int) : GestureFeedback

    /** The picture's fit, with the frame that shows what it does to the edges. */
    data class Zoom(val mode: ZoomMode) : GestureFeedback

    /** The finger is being held down for fast play. */
    data class Speed(val rate: Double) : GestureFeedback

    /** The sound has just been silenced. Coming back the other way shows the level instead. */
    data object Muted : GestureFeedback

    /** A double tap: which way, and by how much. */
    data class Skip(val forward: Boolean, val deltaMs: Long) : GestureFeedback
}

private const val IN_MS = 120
private const val OUT_MS = 180
private const val CHEVRON_MS = 700

private val LevelTrack = 6.dp
private val LevelHeight = 110.dp

/**
 * Every gesture's answer, on one material and in one place.
 *
 * It lays itself out rather than being positioned by the screen, because *where* is part of what each
 * one means: a level belongs on the side the finger is on — reaching for volume and being answered on
 * the far edge is a small lie about what the gesture is attached to — a skip belongs on the half that
 * was tapped, and everything else is central.
 */
@Composable
internal fun GestureHud(feedback: GestureFeedback?, modifier: Modifier = Modifier) {
    val animations = LocalAnimations.current
    Box(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = feedback,
            transitionSpec = {
                fadeIn(tween(animations.scale(IN_MS))) togetherWith fadeOut(tween(animations.scale(OUT_MS)))
            },
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize(),
            label = "gestureHud",
        ) { shown ->
            if (shown == null) return@AnimatedContent
            Box(Modifier.fillMaxSize(), contentAlignment = alignmentFor(shown)) {
                when (shown) {
                    is GestureFeedback.Level -> LevelBody(shown)
                    is GestureFeedback.Zoom -> ZoomBody(shown.mode)
                    is GestureFeedback.Speed -> SpeedBody(shown.rate)
                    GestureFeedback.Muted -> MutedBody()
                    is GestureFeedback.Skip -> SkipBody(shown)
                }
            }
        }
    }
}

/** Where each answer belongs: beside the finger that asked, or in the middle when no side applies. */
private fun alignmentFor(feedback: GestureFeedback): Alignment = when (feedback) {
    // Brightness is the left third of the screen and volume the right one — the gesture's own halves.
    is GestureFeedback.Level -> if (feedback.volume) Alignment.CenterEnd else Alignment.CenterStart
    is GestureFeedback.Skip -> if (feedback.forward) Alignment.CenterEnd else Alignment.CenterStart
    else -> Alignment.Center
}

@Composable
private fun LevelBody(level: GestureFeedback.Level) {
    val accent = LocalAccentOnVideo.current
    PlayerToast(Modifier.padding(horizontal = 24.dp)) {
        Icon(
            imageVector = if (level.volume) MobileIcons.VolumeUp else MobileIcons.BrightnessMedium,
            contentDescription = stringResource(
                if (level.volume) R.string.player_tool_volume else R.string.player_tool_brightness,
            ),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(LevelTrack)
                .height(LevelHeight)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight((level.percent / 100f).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
        }
        Spacer(Modifier.height(10.dp))
        HudText(stringResource(R.string.player_percent, level.percent))
    }
}

@Composable
private fun ZoomBody(mode: ZoomMode) {
    val accent = LocalAccentOnVideo.current
    // Whether the picture is being let into the frame or pushed past it — the one thing the mode's
    // name does not show at a glance.
    val crops = mode != ZoomMode.FIT && mode != ZoomMode.ORIGINAL
    PlayerToast {
        Box(
            Modifier
                .size(width = 46.dp, height = 30.dp)
                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .then(if (crops) Modifier.fillMaxSize() else Modifier.size(width = 40.dp, height = 20.dp))
                    .background(accent.copy(alpha = 0.55f)),
            )
        }
        Spacer(Modifier.height(8.dp))
        HudText(stringResource(mode.labelRes))
    }
}

@Composable
private fun SpeedBody(rate: Double) {
    PlayerToast {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Chevrons(forward = true)
            HudText(formatSpeed(rate))
        }
    }
}

@Composable
private fun MutedBody() {
    PlayerToast {
        Icon(MobileIcons.VolumeOff, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        HudText(stringResource(R.string.player_mute))
    }
}

/**
 * A double tap: the ripple it came out of, then the direction and the distance.
 *
 * The circle is anchored to the middle of the half that was tapped rather than to the exact pixel —
 * the gesture pass classifies taps and deliberately does not report coordinates, and a fixed circle
 * per side is what every other player does with this anyway.
 */
@Composable
private fun SkipBody(skip: GestureFeedback.Skip) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(190.dp)
                .background(
                    Brush.radialGradient(
                        0.0f to Color.White.copy(alpha = 0.14f),
                        1.0f to Color.Transparent,
                    ),
                    RoundedCornerShape(50),
                ),
        )
        PlayerToast {
            Chevrons(forward = skip.forward)
            Spacer(Modifier.height(6.dp))
            HudText(formatSignedTimestamp(if (skip.forward) skip.deltaMs else -skip.deltaMs))
        }
    }
}

/** Three marks travelling the way the picture is: still, and therefore silent, when motion is off. */
@Composable
private fun Chevrons(forward: Boolean) {
    // NEVER hand a scaled duration to infiniteRepeatable: at zero it is a divide-by-zero on the very
    // next frame. Animations off skips the transition outright.
    // The animated State, read only in each mark's graphicsLayer (draw phase), so the sweep does not
    // recompose the row every frame. Null means all three lit.
    val phase = if (LocalAnimations.current == AnimationLevel.OFF) {
        null
    } else {
        rememberInfiniteTransition(label = "chevrons").animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(tween(CHEVRON_MS), RepeatMode.Restart),
            label = "chevronPhase",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
        repeat(3) { index ->
            Icon(
                imageVector = if (forward) MobileIcons.FastForward else MobileIcons.FastRewind,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { alpha = if ((phase?.value ?: 1f) >= index) 1f else 0.35f },
            )
        }
    }
}

@Composable
private fun HudText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content),
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
    )
}

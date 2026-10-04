package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** The unfilled part of the track — visible, but never mistaken for the filled part. */
private val TRACK_REST = Color(0xFF89938F).copy(alpha = 0.35f)

/** The knob is near-white rather than the accent, so it stands out against its own filled track. */
private val THUMB_FILL = Color(0xFFEAFCF8)

private val TRACK_HEIGHT = 4.dp
private val THUMB_SIZE = 17.dp
private val THUMB_RING = 2.dp
private val THUMB_SHADOW = 7.dp

/**
 * A value slider — volume, brightness, zoom, a row count.
 *
 * Material 3's own slider is now a 12 dp bar with tick marks stamped along it and a stubby vertical
 * thumb; three of them stacked on a settings page read as a bar chart. This is the thin track and
 * round knob the rest of the app is drawn to, and it keeps Material's touch handling underneath.
 *
 * The player's seek bar is deliberately not this: a scrubber has its own shape and its own gestures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MobileSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interaction,
        thumb = { MobileSliderThumb() },
        track = { state -> MobileSliderTrack(state) },
    )
}

@Composable
private fun MobileSliderThumb() {
    Box(
        Modifier
            .size(THUMB_SIZE)
            .shadow(THUMB_SHADOW, CircleShape)
            .background(THUMB_FILL, CircleShape)
            .border(THUMB_RING, MaterialTheme.colorScheme.primary, CircleShape),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MobileSliderTrack(state: SliderState) {
    val shape = RoundedCornerShape(TRACK_HEIGHT / 2)
    val fraction = with(state) {
        if (valueRange.endInclusive > valueRange.start) {
            ((this.value - valueRange.start) / (valueRange.endInclusive - valueRange.start))
                .coerceIn(0f, 1f)
        } else {
            0f
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT)
            .clip(shape)
            .background(TRACK_REST),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(TRACK_HEIGHT)
                .background(MaterialTheme.colorScheme.primary, shape),
        )
    }
}

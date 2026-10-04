package tv.own.owntv.mobile.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.ui.theme.LocalMobileMotion

/** The track when the setting is off — a neutral grey that reads as "not accented". */
private val TRACK_OFF = Color(0xFF89938F).copy(alpha = 0.42f)

/** The knob: near-white when off, and the dark of the accent when the accent is behind it. */
private val THUMB_OFF = Color(0xFFE6ECE9)
private val THUMB_ON = Color(0xFF04120F)

private val TRACK_WIDTH = 44.dp
private val TRACK_HEIGHT = 26.dp
private val THUMB_SIZE = 20.dp
private val THUMB_INSET = 3.dp

/** A control the user cannot use is drawn as faint as any other disabled thing. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The app's switch — smaller and flatter than Material's own.
 *
 * Material 3's switch is 52 x 32 dp with a knob that grows when you press it, and inside a list row
 * it is the tallest thing there: it carries a 48 dp touch target of its own and stretches every row
 * it appears in past the height of the rows above and below. This one is the size it looks, so a
 * page of settings keeps one rhythm whether a row ends in a switch, a value or a chevron.
 *
 * [onCheckedChange] is null on a row that already toggles when tapped anywhere — then the switch is
 * a picture of the state, and the row is the button.
 */
@Composable
fun MobileSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val motion = LocalMobileMotion.current
    val track by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else TRACK_OFF,
        animationSpec = motion.fast(),
        label = "switchTrack",
    )
    val thumb by animateColorAsState(
        targetValue = if (checked) THUMB_ON else THUMB_OFF,
        animationSpec = motion.fast(),
        label = "switchThumb",
    )
    // The thumb travels, so it is spatial: it has a little weight to it, and none at all when
    // animations are off.
    val thumbX by animateDpAsState(
        targetValue = if (checked) TRACK_WIDTH - THUMB_SIZE - THUMB_INSET * 2 else 0.dp,
        animationSpec = motion.spatial(),
        label = "switchThumbX",
    )
    Box(
        modifier = modifier
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier
                        .minimumInteractiveComponentSize()
                        .toggleable(
                            value = checked,
                            enabled = enabled,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                },
            )
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TRACK_WIDTH, TRACK_HEIGHT)
                .clip(CircleShape)
                .background(track)
                .padding(THUMB_INSET),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = thumbX)
                    .size(THUMB_SIZE)
                    .clip(CircleShape)
                    .background(thumb),
            )
        }
    }
}

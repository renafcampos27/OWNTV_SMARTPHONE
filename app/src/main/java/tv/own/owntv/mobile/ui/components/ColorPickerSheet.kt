package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.parseAccentHex
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** An HSV triple (h 0..360, s/v 0..1) as an uppercase "#RRGGBB" string. */
fun hsvToHex(h: Float, s: Float, v: Float): String {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    return "#%06X".format(java.util.Locale.ROOT, argb and 0xFFFFFF)
}

/** The rainbow across the hue bar, 0°→360°. */
private val HueSpectrum: List<Color> = (0..360 step 30).map {
    Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f)))
}

private val BarHeight = 28.dp
private val KnobSize = 22.dp
private val SwatchSize = 44.dp
private val PreviewSize = 56.dp

/**
 * Pick a colour: presets, a hue bar, a saturation/brightness square and a hex code — the same four
 * ways the television offers, worked by a finger instead of a remote.
 *
 * The television's bar and square are enter-to-edit D-pad controls, which is the right answer for a
 * cursor and the wrong one for a touchscreen, so these are dragged directly. Everything else matches:
 * a preset is one tap, the hex field takes an exact colour, and the preview shows what you have
 * before you keep it.
 *
 * @param presets Swatches offered above the picker, as "#RRGGBB". Tapping one applies it and closes.
 * @param initial The colour to open on, "#RRGGBB" or blank for the default seed.
 * @param onPick Given the chosen "#RRGGBB". The sheet closes itself afterwards.
 */
@Composable
fun ColorPickerSheet(
    title: String,
    presets: List<String>,
    initial: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seeded from the colour in force, so opening the picker starts where the setting already is
    // rather than somewhere unrelated.
    val seed = remember(initial) {
        FloatArray(3).also { out ->
            val argb = parseAccentHex(initial)?.toInt() ?: DEFAULT_SEED
            android.graphics.Color.colorToHSV(argb, out)
        }
    }
    var hue by remember { mutableFloatStateOf(seed[0]) }
    var sat by remember { mutableFloatStateOf(seed[1]) }
    var value by remember { mutableFloatStateOf(seed[2]) }
    var hexInput by remember { mutableStateOf(initial.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }

    // The square is dragged up and down inside a Column that also scrolls up and down. Without
    // this the scroller wins the touch-slop race and dragging the square just scrolls the sheet.
    var dragging by remember { mutableStateOf(false) }

    val picked = hsvToHex(hue, sat, value)
    // Dragging the bar or the square is what the hex field is showing, so it follows them.
    fun syncHex() {
        hexInput = picked.removePrefix("#")
        hexError = false
    }

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        // The sheet host does not scroll for its callers, and this is its tallest content: a
        // landscape phone is ~400dp high, which is less than the picker needs, and without this the
        // Apply and Use colour buttons sit below the bottom of the screen.
        Column(
            Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState(), enabled = !dragging)
                .padding(horizontal = MobileDimens.ScreenPaddingH),
        ) {
            if (presets.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.settings_presets),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(MobileDimens.GapSmall))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    presets.forEach { hex ->
                        val swatch = parseAccentHex(hex)?.let { Color(it) } ?: return@forEach
                        Box(
                            modifier = Modifier
                                .size(SwatchSize)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (hex.equals(picked, true)) 3.dp else 1.dp,
                                    color = if (hex.equals(picked, true)) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape,
                                )
                                .pointerInput(hex) { detectTapGestures { onPick(hex); onDismiss() } },
                        )
                    }
                }
                Spacer(Modifier.height(MobileDimens.GapLarge))
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_color_picker),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(MobileDimens.GapSmall))
                    HueBar(hue = hue, onDragging = { dragging = it }) { hue = it; syncHex() }
                }
                Box(
                    modifier = Modifier
                        .size(PreviewSize)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
            }

            Spacer(Modifier.height(MobileDimens.GapMedium))
            SatValSquare(hue = hue, sat = sat, value = value, onDragging = { dragging = it }) { s, v ->
                sat = s; value = v; syncHex()
            }

            Spacer(Modifier.height(MobileDimens.GapLarge))
            MobileTextField(
                value = hexInput,
                onValueChange = { hexInput = it.removePrefix("#").take(6); hexError = false },
                label = stringResource(R.string.settings_hex_code),
                placeholder = "52DBC8",
                keyboardType = KeyboardType.Ascii,
                isError = hexError,
                supportingText = if (hexError) stringResource(R.string.settings_hex_error) else null,
            )

            Spacer(Modifier.height(MobileDimens.GapMedium))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = MobileDimens.GapLarge),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                MobileButton(
                    text = stringResource(R.string.settings_apply),
                    style = MobileButtonStyle.SECONDARY,
                    onClick = {
                        // The typed code wins when it is valid; an unparseable one says so instead of
                        // silently keeping whatever the sliders happen to hold.
                        val typed = "#" + hexInput.trim().removePrefix("#").uppercase()
                        if (parseAccentHex(typed) != null) {
                            onPick(typed)
                            onDismiss()
                        } else {
                            hexError = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                MobileButton(
                    text = stringResource(R.string.settings_use_color),
                    onClick = { onPick(picked); onDismiss() },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The default seed when nothing is set yet — the app's own teal. */
private const val DEFAULT_SEED = 0xFF52DBC8.toInt()

/** The rainbow strip: drag anywhere along it to set the hue. */
@Composable
private fun HueBar(hue: Float, onDragging: (Boolean) -> Unit, onHue: (Float) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .clip(RoundedCornerShape(BarHeight / 2))
            .background(Brush.horizontalGradient(HueSpectrum)),
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val set = { x: Float -> onHue((x / widthPx).coerceIn(0f, 1f) * 360f) }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx) {
                    detectTapGestures { set(it.x) }
                }
                .pointerInput(widthPx) {
                    detectDragGestures(
                        onDragStart = { onDragging(true) },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) },
                    ) { change, _ -> change.consume(); set(change.position.x) }
                },
        )
        Box(
            modifier = Modifier
                .offset(x = maxWidth * (hue / 360f) - KnobSize / 2)
                .align(Alignment.CenterStart)
                .size(KnobSize)
                .clip(CircleShape)
                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))))
                .border(3.dp, Color.White, CircleShape),
        )
    }
}

/** Saturation across, brightness up: drag the dot to anywhere in the square. */
@Composable
private fun SatValSquare(
    hue: Float,
    sat: Float,
    value: Float,
    onDragging: (Boolean) -> Unit,
    onChange: (Float, Float) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(MobileDimens.GapSmall))
            .background(Brush.horizontalGradient(listOf(Color.White, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))))),
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        // Black over the hue wash: across is saturation, down is darkness — the arrangement every
        // colour picker uses, so the dot ends up where a user expects to find it.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black))),
        )
        val set = { x: Float, y: Float ->
            onChange((x / widthPx).coerceIn(0f, 1f), 1f - (y / heightPx).coerceIn(0f, 1f))
        }
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(widthPx, heightPx) {
                    detectTapGestures { set(it.x, it.y) }
                }
                .pointerInput(widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { onDragging(true) },
                        onDragEnd = { onDragging(false) },
                        onDragCancel = { onDragging(false) },
                    ) { change, _ ->
                        change.consume()
                        set(change.position.x, change.position.y)
                    }
                },
        )
        Box(
            modifier = Modifier
                .offset(
                    x = maxWidth * sat - KnobSize / 2,
                    y = maxHeight * (1f - value) - KnobSize / 2,
                )
                .size(KnobSize)
                .clip(CircleShape)
                .background(Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value))))
                .border(3.dp, Color.White, CircleShape),
        )
    }
}

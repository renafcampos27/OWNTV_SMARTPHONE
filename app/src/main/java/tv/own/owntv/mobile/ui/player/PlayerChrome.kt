package tv.own.owntv.mobile.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.theme.LocalAccentOnVideo
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.mobile.ui.theme.LocalGlass
import tv.own.owntv.mobile.ui.theme.SquircleShape
import tv.own.owntv.mobile.ui.theme.luminousEdge
import tv.own.owntv.mobile.ui.theme.luminousRim
import tv.own.owntv.mobile.ui.theme.materialFor

/**
 * The player's own material.
 *
 * Everything here is drawn on the picture, which is why none of it uses the page's colours: the
 * theme's surfaces and its accent are chosen to sit on a page, and over a film there is no page. The
 * dock is black glass with a white hairline, the text is white, and the one accent used is
 * [LocalAccentOnVideo].
 *
 * What the picture costs us: the video arrives on a `SurfaceView`, and Compose can neither sample it
 * nor blur it — putting anything over it also drops it off the hardware overlay path. So the dock
 * cannot frost the *video*. Scoping *Player controls* into the Glass Effect gives it the Chrome
 * material's light instead — the moulded rim, the top sheen, the far edge and the cast shadow — over
 * the same black fill.
 */
private val DockFill = Color.Black.copy(alpha = 0.55f)
private val DockRim = Color.White.copy(alpha = 0.10f)

internal val DockShape: Shape = SquircleShape(22.dp)
internal val CapsuleShape: Shape = SquircleShape(44.dp)
private val ButtonShape: Shape = SquircleShape(14.dp)

/** Idle white: bright enough to read on a bright scene, quiet enough not to fight the picture. */
internal val OnVideo = Color.White.copy(alpha = 0.78f)

private const val EXPAND_MS = 140
private const val HOLD_MS = 400L
private const val LABEL_MAX_DP = 132

/** One pane of the player's chrome: black glass, hairline rim, and the material's light when scoped. */
@Composable
private fun Modifier.playerPane(
    shape: Shape,
    surface: GlassSurface = GlassSurface.PLAYER_CONTROLS,
): Modifier {
    val glass = LocalGlass.current
    if (!glass.isGlassy(surface)) {
        return this.background(DockFill, shape).border(1.dp, DockRim, shape)
    }
    // 0.55 is the shipped baseline for the light control and therefore renders at 1x, as on the TV.
    val light = (glass.highlightStrength / GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH).coerceIn(0f, 1.8f)
    val material = materialFor(surface)
    return this
        .then(if (glass.depthEffects) Modifier.shadow(material.shadow, shape) else Modifier)
        .background(DockFill, shape)
        .drawWithCache {
            val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
            val stroke = 1.dp.toPx()
            val sheen = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.10f * material.sheen * light), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.45f,
            )
            val rim = luminousRim(size, material.rim * light, Color.White)
            val edge = luminousEdge(size, material.edge * light)
            onDrawBehind {
                drawPath(path, sheen)
                drawPath(path, edge, style = Stroke(width = stroke))
                drawPath(path, rim, style = Stroke(width = stroke))
            }
        }
        .border(1.dp, DockRim, shape)
}

/**
 * The bottom container: the instrument over the tools, in one pane.
 *
 * Deliberately **not** clipped to its own shape. The fill and the rim are drawn to [DockShape]
 * already, so a clip would add nothing except cutting off the one thing that has to escape upward —
 * the scrub bubble, which is above the bar precisely so a thumb is not covering it.
 */
@Composable
internal fun PlayerDock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .playerPane(DockShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        content = content,
    )
}

/** The transport buttons in one capsule, rather than floating loose over the picture. */
@Composable
internal fun TransportCapsule(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .clip(CapsuleShape)
            .playerPane(CapsuleShape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}


/**
 * Hold to read, tap to act.
 *
 * The label is never *only* held behind a gesture: it is also the button's accessibility name, it is
 * a semantic click action a screen reader can perform outright, and the pinned tools show it.
 *
 * A raw pointer loop has no semantics of its own — that is the trap here. `IconButton` gave the old
 * tool bar its button role and its activation for free; this has to declare both.
 */
@Composable
private fun Modifier.holdToReveal(
    label: String,
    onExpanded: (Boolean) -> Unit,
    onClick: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    return this.semantics {
        contentDescription = label
        role = Role.Button
        onClick(label) { onClick(); true }
    }.pointerInput(onClick) {
        detectTapGestures(
            onPress = {
                val reveal = scope.launch {
                    delay(HOLD_MS)
                    onExpanded(true)
                }
                tryAwaitRelease()
                reveal.cancel()
                onExpanded(false)
            },
            // Present so a hold does not also count as a tap: holding asks what the button is, and
            // being answered by the button firing is the opposite of an answer.
            onLongPress = { },
            onTap = { onClick() },
        )
    }
}

/**
 * A tool: a 48 dp square, and nothing but its glyph.
 *
 * **No name is ever drawn.** The bar sits over the picture on a phone-width screen and has very
 * little room; a button that grew sideways into a word pushed its neighbours off the end of the row.
 * The name is not lost — [holdToReveal] publishes it as the button's `contentDescription` and as a
 * named click action, so a screen reader still announces every control by name.
 */
@Composable
internal fun CtrlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    var held by remember { mutableStateOf(false) }
    val tint = if (active) LocalAccentOnVideo.current else if (held) Color.White else OnVideo
    Row(
        modifier
            .height(48.dp)
            .widthIn(min = 48.dp)
            .clip(ButtonShape)
            .holdToReveal(label, onExpanded = { held = it }, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** The playback rate, where the rate itself is the glyph — "1.5x" already says what the button does. */
@Composable
internal fun SpeedButton(rate: String, label: String, active: Boolean, onClick: () -> Unit) {
    TextControl(text = rate, label = label, active = active, icon = null, onClick = onClick)
}

/** MPV or EXO: which engine is playing, and one tap to swap it. */
@Composable
internal fun EngineToggle(engine: String, label: String, active: Boolean, icon: ImageVector, onClick: () -> Unit) {
    TextControl(text = engine, label = label, active = active, icon = icon, onClick = onClick)
}

@Composable
private fun TextControl(
    text: String,
    label: String,
    active: Boolean,
    icon: ImageVector?,
    onClick: () -> Unit,
) {
    var held by remember { mutableStateOf(false) }
    val tint = if (active) LocalAccentOnVideo.current else if (held) Color.White else OnVideo
    Row(
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(ButtonShape)
            .holdToReveal(label, onExpanded = { held = it }, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        // The value IS the glyph here - "1.5x" and "EXO" already say what the button is. The
        // control's *name* is never drawn, same as CtrlButton: it lives in the semantics only.
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The channel's logo, falling back to the first letters of its name on the house plate.
 *
 * [number] is the provider's own channel number, and it is drawn across the foot of the plate rather
 * than beside the title: a user who knows their channels by number looks for it where the logo is,
 * and the title row on a phone has no width to give away. Null hides it entirely, which is what
 * turning *Channel numbers* off means.
 */
@Composable
internal fun ChannelLogo(
    logoUrl: String?,
    title: String?,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    number: Int? = null,
) {
    Box(
        modifier.size(size).clip(SquircleShape(10.dp)).background(Color(0xFF004F46)),
        contentAlignment = Alignment.Center,
    ) {
        ChannelLogoImage(
            url = logoUrl,
            modifier = Modifier.fillMaxSize(),
            fallback = {
                if (!title.isNullOrBlank()) {
                    Text(
                        title.take(3).uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6FF8E4),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
        if (number != null) {
            Text(
                stringResource(R.string.player_channel_number, number),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 2.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A message over the picture — the volume readout, the speed, a failure.
 *
 * Its own surface, so a user who wants the chrome frosted but the messages plain (or the other way
 * about) can have that: *On-screen messages* is a separate switch on the Glass Effect screen.
 */
@Composable
internal fun PlayerToast(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .clip(ToastShape)
            .playerPane(ToastShape, GlassSurface.TOASTS)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

private val ToastShape: Shape = SquircleShape(18.dp)

/**
 * The scrims: a flat panel behind the chrome, feathered to nothing only at its inner edge.
 *
 * A plain two-stop gradient fades out exactly where the title and the times sit, so those wash out on
 * a bright scene; a hard band instead draws a seam across the picture. Panel first, then the feather.
 */
internal val TopScrim = Brush.verticalGradient(
    0.0f to Color.Black.copy(alpha = 0.72f),
    0.5f to Color.Black.copy(alpha = 0.68f),
    1.0f to Color.Transparent,
)

internal val BottomScrim = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.45f to Color.Black.copy(alpha = 0.68f),
    1.0f to Color.Black.copy(alpha = 0.78f),
)

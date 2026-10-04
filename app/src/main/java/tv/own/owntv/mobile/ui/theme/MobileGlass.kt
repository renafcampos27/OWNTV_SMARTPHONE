package tv.own.owntv.mobile.ui.theme

import tv.own.owntv.core.theme.AnimationLevel
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import android.app.ActivityManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.GlassSurface
import kotlin.math.ln
import kotlin.math.pow

/** The user's Glass Effect settings, read from core and provided at the theme root. */
val LocalGlass = staticCompositionLocalOf { GlassConfig() }

/**
 * The wallpaper's own dominant colour, or null when there is no wallpaper.
 *
 * A pane of glass takes the colour of what is behind it. Tinting the panels a little way towards the
 * wallpaper is what stops a grey Material surface sitting on a warm photograph looking like a sticker
 * laid on top of it — and it costs one downsampled decode when the wallpaper changes, not per frame.
 */
val LocalGlassTint = staticCompositionLocalOf<Color?> { null }

/** How far a glass panel is pulled towards the wallpaper's colour. Enough to relate, not to stain. */
private const val TINT_MIX = 0.22f

/**
 * The fill of a glass panel that has no frost behind it — the TV app's own tonal value.
 *
 * Not 1.0: a hair of translucency is what still separates it from a plain Material surface. Not the
 * user's alpha either, because that number describes how much wallpaper shows through the frost, and
 * here there is no frost for it to describe.
 */
private const val CERAMIC_ALPHA = 0.94f

/** The contrast ratio text over a glass panel has to keep. WCAG AA for body text. */
private const val MINIMUM_CONTRAST = 4.5f

/** How deep the frost goes at full strength. */
private val MaxBlurRadius = 44.dp

/** The shallowest rung of the ladder — a hint of diffusion rather than a sharp photograph. */
private val MinBlurRadius = 2.dp

/** Rungs on the frost ladder. Ten, the same count the television builds. */
private const val FROST_LEVELS = 10

/** A low-RAM phone gets a coarser ladder over the same range, not a shallower one. */
private const val LOW_RAM_FROST_LEVELS = 5

/** The wallpaper luminance grid: coarse enough to be 576 floats, fine enough to find a bright patch. */
private const val LUMA_COLUMNS = 32
private const val LUMA_ROWS = 18

/**
 * How bright the wallpaper is, per cell of a coarse grid, in linear light.
 *
 * Sampled once when the wallpaper is decoded so that asking "how bright is it behind this panel?"
 * during a draw costs sixteen array reads and no pixels at all.
 */
@Stable
class BackdropLuminanceMap internal constructor(
    private val columns: Int,
    private val rows: Int,
    private val values: FloatArray,
) {
    /** Mean of a 4×4 sample across [bounds], which are in root coordinates. */
    fun meanIn(bounds: Rect, rootSize: Size): Float {
        if (bounds.width <= 0f || bounds.height <= 0f || rootSize.width <= 0f || rootSize.height <= 0f) {
            return 0.5f
        }
        var total = 0f
        repeat(4) { y ->
            val rootY = bounds.top + bounds.height * ((y + 0.5f) / 4f)
            val gridY = ((rootY / rootSize.height) * rows).toInt().coerceIn(0, rows - 1)
            repeat(4) { x ->
                val rootX = bounds.left + bounds.width * ((x + 0.5f) / 4f)
                val gridX = ((rootX / rootSize.width) * columns).toInt().coerceIn(0, columns - 1)
                total += values[gridY * columns + gridX]
            }
        }
        return (total / 16f).coerceIn(0f, 1f)
    }
}

/** The two ladder rungs bracketing a requested frost depth, and how far between them it sits. */
@Immutable
data class FrostSelection(
    internal val lower: GraphicsLayer,
    internal val upper: GraphicsLayer?,
    internal val upperWeight: Float,
)

/**
 * The wallpaper, blurred at ten depths, that every glass surface samples its own slice of.
 *
 * One ladder for the whole screen rather than one blur per panel: each rung is recorded a single time
 * and each surface merely draws the rung it wants back, shifted so the slice lines up with what is
 * actually behind it. A phone with fifty glass list rows on screen therefore costs the same as one.
 *
 * The rungs are geometric — 2 dp, 2.8 dp, 4 dp … 44 dp — because blur reads logarithmically, so equal
 * ratios are equal-looking steps and the shallow end, where chrome lives, gets the fine resolution.
 */
@Stable
class GlassBackdrop internal constructor(
    internal val levels: List<GraphicsLayer>,
    /** Rung radii in pixels, ascending, parallel to [levels]. */
    internal val radiiPx: FloatArray,
    internal val luminance: BackdropLuminanceMap?,
    internal var rootOffset: Offset,
    internal var rootSize: Size,
) {
    /**
     * The rungs bracketing [strength] of the full frost depth, cross-faded.
     *
     * The pair is chosen by the radius the caller is really asking for — `MaxBlurRadius * strength` —
     * rather than by ladder index, so a request keeps meaning the same depth however many rungs the
     * device could afford, and the weight is taken in log space because the ladder is geometric.
     */
    fun frostFor(strength: Float): FrostSelection? {
        if (levels.isEmpty() || strength <= 0f) return null
        val target = radiiPx.last() * strength.coerceIn(0f, 1f)
        // Below the shallowest rung there is nothing to fade towards, so it is the whole answer.
        if (target <= radiiPx.first()) return FrostSelection(levels.first(), null, 0f)
        val upper = radiiPx.indexOfFirst { it >= target }
        val weight = ln(target / radiiPx[upper - 1]) / ln(radiiPx[upper] / radiiPx[upper - 1])
        return FrostSelection(levels[upper - 1], levels[upper], weight.coerceIn(0f, 1f))
    }

    /**
     * How bright the wallpaper is under [bounds], which are relative to this backdrop's root, or
     * null while it has not been read yet — a guess here would make every panel visibly settle once
     * the real answer arrived.
     */
    fun sampledLuminance(bounds: Rect): Float? = luminance?.meanIn(bounds, rootSize)
}

/** The blurred backdrop, or null when there is no wallpaper or the device predates hardware blur. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/**
 * Real backdrop blur is [android.graphics.RenderEffect], which is API 31. Below that the glass is
 * translucency, rim light and depth only — never a per-frame software blur, which on a phone would
 * cost battery on every scroll for an effect nobody asked to pay for.
 */
val supportsBackdropBlur: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Minimum fill opacity needed for [minimumContrast] text contrast over the sampled wallpaper.
 *
 * All three luminances are relative luminance in 0..1. The answer is the smallest alpha at which the
 * fill, composited over the wallpaper, is far enough from the text to stay readable — 0 when the
 * wallpaper is already out of the way, 1 when no amount of this fill can help.
 */
fun requiredLegibilityAlpha(
    backdropLuma: Float,
    fillLuma: Float,
    textLuma: Float,
    minimumContrast: Float = MINIMUM_CONTRAST,
): Float {
    val backdrop = backdropLuma.coerceIn(0f, 1f)
    val fill = fillLuma.coerceIn(0f, 1f)
    val text = textLuma.coerceIn(0f, 1f)
    return if (text >= 0.5f) {
        val maximumBackground = ((text + 0.05f) / minimumContrast - 0.05f).coerceIn(0f, 1f)
        when {
            backdrop <= maximumBackground -> 0f
            fill >= backdrop -> 1f
            else -> ((backdrop - maximumBackground) / (backdrop - fill)).coerceIn(0f, 1f)
        }
    } else {
        val minimumBackground = (minimumContrast * (text + 0.05f) - 0.05f).coerceIn(0f, 1f)
        when {
            backdrop >= minimumBackground -> 0f
            fill <= backdrop -> 1f
            else -> ((minimumBackground - backdrop) / (fill - backdrop)).coerceIn(0f, 1f)
        }
    }
}

/** What one decode of the wallpaper yields: its overall colour, and where its bright parts are. */
private class WallpaperAnalysis(val tint: Color?, val luminance: BackdropLuminanceMap?)

private val EmptyWallpaper = WallpaperAnalysis(null, null)

/**
 * Decode the wallpaper small, once, and read both things the glass needs off it.
 *
 * Cropped to the root's aspect first, because the wallpaper is drawn with [ContentScale.Crop] and the
 * luminance grid has to answer for the part of the picture that is actually on screen.
 */
private suspend fun analyseWallpaper(path: String, rootSize: Size): WallpaperAnalysis =
    withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching EmptyWallpaper
            // 128 px wide is four pixels per luminance cell and far more than the mean colour needs.
            val sample = maxOf(1, bounds.outWidth / 128)
            val decoded = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return@runCatching EmptyWallpaper

            val aspect = if (rootSize.height > 0f) rootSize.width / rootSize.height else 0f
            val cropped = cropToAspect(decoded, aspect)
            val tint = meanColour(cropped)
            val luminance = buildLuminanceMap(cropped)
            cropped.recycle()
            WallpaperAnalysis(tint, luminance)
        }.getOrDefault(EmptyWallpaper)
    }

/** Centre-crop to [aspect], matching what [ContentScale.Crop] shows. Returns the source when it fits. */
private fun cropToAspect(source: Bitmap, aspect: Float): Bitmap {
    if (aspect <= 0f) return source
    val current = source.width.toFloat() / source.height.toFloat()
    if (kotlin.math.abs(current - aspect) <= 0.02f) return source
    val width: Int
    val height: Int
    if (current > aspect) {
        height = source.height
        width = (height * aspect).toInt()
    } else {
        width = source.width
        height = (width / aspect).toInt()
    }
    val cropped = Bitmap.createBitmap(
        source,
        ((source.width - width) / 2).coerceAtLeast(0),
        ((source.height - height) / 2).coerceAtLeast(0),
        width.coerceIn(1, source.width),
        height.coerceIn(1, source.height),
    )
    if (cropped !== source) source.recycle()
    return cropped
}

/** Averaged over a thumbnail — "what colour is this picture, roughly". */
private fun meanColour(bitmap: Bitmap): Color {
    var r = 0L
    var g = 0L
    var b = 0L
    for (x in 0 until bitmap.width) {
        for (y in 0 until bitmap.height) {
            val pixel = bitmap.getPixel(x, y)
            r += (pixel shr 16) and 0xFF
            g += (pixel shr 8) and 0xFF
            b += pixel and 0xFF
        }
    }
    val n = (bitmap.width * bitmap.height).coerceAtLeast(1)
    return Color(red = (r / n).toInt(), green = (g / n).toInt(), blue = (b / n).toInt())
}

/** A [LUMA_COLUMNS]×[LUMA_ROWS] grid of relative luminance, sampled at each cell's centre. */
private fun buildLuminanceMap(bitmap: Bitmap): BackdropLuminanceMap {
    val values = FloatArray(LUMA_COLUMNS * LUMA_ROWS)
    fun linear(channel: Int): Float {
        val value = channel / 255f
        return if (value <= 0.04045f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
    }
    repeat(LUMA_ROWS) { y ->
        val py = (((y + 0.5f) / LUMA_ROWS) * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        repeat(LUMA_COLUMNS) { x ->
            val px = (((x + 0.5f) / LUMA_COLUMNS) * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
            val pixel = bitmap.getPixel(px, py)
            values[y * LUMA_COLUMNS + x] =
                0.2126f * linear((pixel shr 16) and 0xFF) +
                    0.7152f * linear((pixel shr 8) and 0xFF) +
                    0.0722f * linear(pixel and 0xFF)
        }
    }
    return BackdropLuminanceMap(LUMA_COLUMNS, LUMA_ROWS, values)
}

/**
 * The root the whole app sits in: the wallpaper sharp underneath, and a ladder of blurred copies of
 * it kept aside for [glassSurface] to sample.
 *
 * With no wallpaper set, or on a device without hardware blur, this is just the theme background and
 * the backdrop local stays null.
 */
@Composable
fun GlassBackdropRoot(content: @Composable () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val bgImagePath by settings.bgImagePath.collectAsStateWithLifecycle("")
    val glass = LocalGlass.current
    // The wallpaper belongs to the Glass Effect, so it goes away with it: switching glass off and
    // being left with a picture the panels no longer sample is the wrong half of the feature.
    val hasImage = bgImagePath.isNotBlank() && glass.enabled
    val frosted = hasImage && supportsBackdropBlur && glass.blurStrength > 0f

    // Ten full-screen textures is real memory even though only the rungs actually drawn are ever
    // rasterized, so a low-RAM phone gets half the rungs — a coarser ladder over the same range,
    // which costs a little cross-fade accuracy rather than the deep frost the top rung provides.
    val context = LocalContext.current
    val levelCount = remember(context) {
        val manager = context.getSystemService(ActivityManager::class.java)
        if (manager?.isLowRamDevice == true) LOW_RAM_FROST_LEVELS else FROST_LEVELS
    }

    // Recorded sharp, drawn sharp behind the content; each rung replays it through its own blur, so
    // the wallpaper is decoded and rasterized exactly once for all of them.
    val sharp = rememberGraphicsLayer()
    val levels = List(levelCount) { rememberGraphicsLayer() }
    val density = LocalDensity.current
    val radiiPx = remember(density, levelCount) {
        val min = with(density) { MinBlurRadius.toPx() }
        val max = with(density) { MaxBlurRadius.toPx() }
        val ratio = (max / min).pow(1f / (levelCount - 1))
        FloatArray(levelCount) { min * ratio.pow(it.toFloat()) }
    }

    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(Size.Zero) }
    val analysis by produceState(EmptyWallpaper, bgImagePath, rootSize, hasImage) {
        value = if (hasImage && rootSize != Size.Zero) analyseWallpaper(bgImagePath, rootSize) else EmptyWallpaper
    }
    val backdrop = remember(frosted, radiiPx, analysis) {
        if (frosted) GlassBackdrop(levels, radiiPx, analysis.luminance, Offset.Zero, Size.Zero) else null
    }
    backdrop?.rootOffset = rootOffset
    backdrop?.rootSize = rootSize

    // Clearing the wallpaper takes the image away, and with it the only thing that ever re-records
    // the ladder — so ten full-screen textures would sit in graphics memory for the rest of the
    // session. Re-recording each rung empty at 1×1 is what actually hands them back.
    LaunchedEffect(frosted, density) {
        if (!frosted) {
            levels.forEach { it.record(density, LayoutDirection.Ltr, IntSize(1, 1)) {} }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned {
                rootOffset = it.positionInRoot()
                rootSize = it.size.toSize()
            },
    ) {
        if (hasImage) {
            AsyncImage(
                model = bgImagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        sharp.record { this@drawWithContent.drawContent() }
                        drawLayer(sharp)
                        if (frosted) {
                            levels.forEachIndexed { index, layer ->
                                val radius = radiiPx[index]
                                layer.renderEffect = BlurEffect(radius, radius, TileMode.Decal)
                                layer.record { drawLayer(sharp) }
                            }
                        }
                    },
            )
        }
        CompositionLocalProvider(
            LocalGlassBackdrop provides backdrop,
            LocalGlassTint provides analysis.tint,
        ) {
            content()
        }
    }
}

/** How far a pressed pane sinks. Small on purpose: it should read as pressure, not as a bounce. */
private const val PRESS_SCALE = 0.985f
private const val PRESS_TINT_GAIN = 0.06f
private const val PRESS_FROST_DAMP = 0.85f
private const val PRESS_RIM_GAIN = 2.4f
private const val PRESS_SHADOW_DAMP = 0.4f

/** A selected pane wears the accent on its rim and warms its body. Persistent, not momentary. */
private const val SELECTED_RIM = 0.55f
private const val SELECTED_BODY_GAIN = 1.25f
private const val SELECTED_TINT_GAIN = 0.03f

/** The top highlight at full material sheen, before the user's highlight strength scales it. */
private const val SHEEN_BASE = 0.11f

/** How long a pane's arrival glint takes to cross it, and how bright it is at its brightest. */
private const val ARRIVAL_MS = 420
private const val GLINT_PEAK = 0.20f

/**
 * How much slower the frosted wallpaper moves than the pane in front of it.
 *
 * Small deliberately: this is the difference between a panel that sits *on* the wallpaper and one
 * that floats above it, and any more than a few per cent reads as the picture sliding about.
 */
private const val BACKDROP_PARALLAX = 0.06f

/**
 * Fill a panel with the material its [surface] is made of.
 *
 * Glassed, it draws in the order light actually arrives: the frosted slice of wallpaper behind the
 * pane, the tint over it, the pane's internal glow, a specular rim that is brightest where the light
 * comes from and a dark edge where it does not reach. How much of that a given pane gets is decided by
 * its material and by how many panes of glass it already sits behind.
 *
 * With the effect off — which is the default — the four materials become four tonal steps with the
 * matching elevation, so the same hierarchy reads either way and every caller can use this
 * unconditionally.
 *
 * The tint's opacity is the user's alpha, raised where it has to be: the wallpaper's brightness under
 * this exact panel decides the least opacity at which the text on it still reads, and that wins —
 * unless the user has ticked *Allow full transparency*, which is what that switch is for.
 *
 * Pass the [interactionSource] a clickable already has, and [selected] for a persistent state, and the
 * pane answers the finger itself. That is why glass controls draw no Material ripple: a ripple over
 * frosted glass reads as a smear across it.
 */
@Composable
fun Modifier.glassSurface(
    surface: GlassSurface,
    shape: Shape = MobileCardShape,
    // The colour this panel is when the effect is off, and the tone its glass tint is measured from.
    // The material decides it; a caller overrides only where it genuinely owns its own colour.
    fill: Color = tonalFillFor(surface),
    interactionSource: InteractionSource? = null,
    selected: Boolean = false,
): Modifier {
    val glass = LocalGlass.current
    val material = materialFor(surface)
    val accent = MaterialTheme.colorScheme.primary

    val pressed = interactionSource?.collectIsPressedAsState()?.value == true
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = LocalMobileMotion.current.fast(),
        label = "glassPress",
    )
    // A graphics layer per card is not free, so one is taken only while a pane is actually moving.
    val sink = if (scale == 1f) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }

    if (!glass.isGlassy(surface)) {
        // Rule Y-B: pressed and selected are states of the app, not of the effect. Off, the pane
        // answers with the tonal step above it and the accent, which is the same message in the
        // vocabulary this mode has.
        val tonal = when {
            pressed -> fill.shiftedBy(0.08f)
            selected -> lerp(fill, accent, 0.18f)
            else -> fill
        }
        return this
            .then(sink)
            .then(if (material.elevation > 0.dp) Modifier.shadow(material.elevation, shape) else Modifier)
            .background(tonal, shape)
    }

    val layer = effectiveGlassLayer(surface, LocalGlassLayer.current)
    val treatment = glassLayerTreatment(layer)
    val backdrop = LocalGlassBackdrop.current
    // Pulled towards the wallpaper's colour so the pane looks lit by the picture it sits on, then
    // moved off it by the material — up towards white for a dialog, down towards black for a card.
    val wallpaperTinted = LocalGlassTint.current?.let { lerp(fill, it, TINT_MIX) } ?: fill
    val tintShift = material.tint +
        (if (pressed) PRESS_TINT_GAIN else 0f) +
        (if (selected) SELECTED_TINT_GAIN else 0f)
    val tinted = wallpaperTinted.shiftedBy(tintShift)

    val onSurface = MaterialTheme.colorScheme.onSurface
    val rimColour = if (selected) accent else onSurface
    // 0.55 is the shipped baseline and therefore renders at 1x, exactly as on the TV.
    val light = (glass.highlightStrength / GlassConfig.DEFAULT_HIGHLIGHT_STRENGTH).coerceIn(0f, 1.8f)
    val rimAlpha = when {
        pressed -> material.rim * PRESS_RIM_GAIN
        selected -> SELECTED_RIM
        else -> material.rim
    } * light
    val bodyPeak = material.body * (if (selected) SELECTED_BODY_GAIN else 1f) * light
    val bodyColour = if (selected) accent else Color.White
    // Relative to the deepest material, so a chrome bar stays a fraction of whatever frost the user
    // has chosen instead of overriding it (Rule Y-C).
    val frostAsk = glass.blurStrength * (material.frost / DEEPEST_FROST) *
        (if (pressed) PRESS_FROST_DAMP else 1f)
    val shadowDp = material.shadow * (if (pressed) PRESS_SHADOW_DAMP else 1f)
    val fillLuma = tinted.luminance()
    val textLuma = onSurface.luminance()
    val freeAlpha = glass.allowFullTransparency

    var position by remember { mutableStateOf(Offset.Zero) }
    var inWindow by remember { mutableStateOf(Offset.Zero) }
    val windowSize = LocalWindowInfo.current.containerSize
    val arrival = rememberGlassArrival(glass.glint)

    return this
        .then(sink)
        // Depth is what stops a translucent panel reading as a stain on the wallpaper: it has to sit
        // above it, not in it. Off, the glass is flat and the wallpaper is the only depth cue.
        .then(
            if (glass.depthEffects && shadowDp > 0.dp) Modifier.shadow(shadowDp, shape) else Modifier,
        )
        .onGloballyPositioned {
            position = it.positionInRoot()
            inWindow = it.positionInWindow()
        }
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            val strokePx = 1.dp.toPx()

            // A pane that runs into the side of the screen has no edge there — a rim drawn along the
            // window's own border is a scrim outline, not the moulded edge of a piece of glass. The
            // top bar, the navigation bar and the page panel are all in this case, and none of them
            // has to ask: where the pane ends is what decides it.
            val flushLeft = inWindow.x <= 0.5f
            val flushTop = inWindow.y <= 0.5f
            val flushRight = inWindow.x + size.width >= windowSize.width - 0.5f
            val flushBottom = inWindow.y + size.height >= windowSize.height - 0.5f

            val sheen = if (treatment.sheen && material.sheen > 0f) {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = SHEEN_BASE * material.sheen * light),
                        Color.Transparent,
                    ),
                    startY = 0f,
                    endY = size.height * 0.45f,
                )
            } else {
                null
            }
            val body = if (treatment.sheen && bodyPeak > 0f) luminousBody(size, bodyPeak, bodyColour) else null
            val shade = if (body != null) luminousShade(size) else null
            val rim = if (treatment.rim && rimAlpha > 0f) luminousRim(size, rimAlpha, rimColour) else null
            val edge = if (treatment.edge && material.edge > 0f) luminousEdge(size, material.edge * light) else null

            val frost = if (treatment.frost && material.hasBackdrop) backdrop?.frostFor(frostAsk) else null
            // The pane's own place on the wallpaper, pulled back a little so the picture behind it
            // travels slower than it does as the page scrolls. Depth off means no parallax: it is a
            // depth cue, and that switch is what turns depth cues off.
            val raw = if (backdrop == null) Offset.Zero else position - backdrop.rootOffset
            val slice = if (glass.depthEffects) raw * (1f - BACKDROP_PARALLAX) else raw
            val backdropLuma =
                if (frost == null || freeAlpha) null else backdrop?.sampledLuminance(Rect(slice, size))
            val tintAlpha = if (backdropLuma == null) {
                glass.alpha
            } else {
                maxOf(glass.alpha, requiredLegibilityAlpha(backdropLuma, fillLuma, textLuma))
            }

            onDrawBehind {
                clipPath(path) {
                    when {
                        frost != null -> {
                            translate(-slice.x, -slice.y) {
                                frost.lower.alpha = 1f
                                drawLayer(frost.lower)
                                frost.upper?.let { upper ->
                                    upper.alpha = frost.upperWeight
                                    drawLayer(upper)
                                }
                            }
                            // The frosted slice is opaque underneath, so the tint is what decides how
                            // much of the wallpaper survives.
                            drawRect(tinted.copy(alpha = tintAlpha))
                        }

                        backdrop != null -> {
                            // No frost of its own — a card, or a pane already sitting on frosted
                            // glass. What is behind it is blurred already; blurring it again is what
                            // turns a stack of panes into soup. So it only tints.
                            drawRect(tinted.copy(alpha = glass.alpha))
                        }

                        else -> {
                            // Nothing frosted anywhere: no wallpaper, a device without hardware blur,
                            // or a sheet, which is its own window and cannot replay the main window's
                            // layer. A 22 % fill over nothing is not glass, it is an invisible panel,
                            // so this is the tonal ceramic instead — near-opaque, wallpaper-tinted,
                            // and still edge-lit below.
                            drawRect(tinted.copy(alpha = CERAMIC_ALPHA))
                        }
                    }
                    body?.let { drawRect(it) }
                    shade?.let { drawRect(it) }
                    sheen?.let { drawRect(it) }
                    // The arrival: one band of light travelling across the pane, once. It is drawn
                    // only while it is travelling, so a settled screen pays nothing for it.
                    if (arrival > 0f && arrival < 1f) {
                        drawRect(glint(size, arrival))
                    }
                }
                if (rim == null && edge == null) return@onDrawBehind
                clipRect(
                    left = if (flushLeft) strokePx else 0f,
                    top = if (flushTop) strokePx else 0f,
                    right = size.width - if (flushRight) strokePx else 0f,
                    bottom = size.height - if (flushBottom) strokePx else 0f,
                ) {
                    edge?.let { drawPath(path, it, style = Stroke(width = strokePx)) }
                    rim?.let { drawPath(path, it, style = Stroke(width = strokePx)) }
                }
            }
        }
}

/**
 * One number per glass pane: how far its arrival is through, from 0 to 1.
 *
 * One `Animatable` per *surface*, never per element — that is the whole reason this is cheap. It runs
 * once, on the pane's first composition, and a settled pane holds 1 forever after. With animations off
 * — or with [enabled] false, the user's own switch — it is 1 from the first frame, so nothing is drawn
 * at all rather than being drawn quickly.
 */
@Composable
private fun rememberGlassArrival(enabled: Boolean): Float {
    val instant = !enabled || LocalAnimations.current == AnimationLevel.OFF
    val progress = remember { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(instant) {
        if (instant) progress.snapTo(1f) else if (progress.value < 1f) {
            progress.animateTo(1f, tween(ARRIVAL_MS, easing = LinearEasing))
        }
    }
    return progress.value
}

/** The travelling band of light itself: a narrow diagonal sweep, brightest in its middle. */
private fun glint(size: Size, progress: Float): Brush {
    // It starts off the leading edge and leaves by the trailing one, so the pane is never half-lit at
    // either end of the run.
    val span = size.width + size.height
    val head = -span * 0.35f + progress * span * 1.35f
    // Brightest halfway across and gone at both ends: a sweep that stopped dead would read as a flash.
    val strength = GLINT_PEAK * kotlin.math.sin(progress * Math.PI).toFloat()
    return Brush.linearGradient(
        0f to Color.Transparent,
        0.5f to Color.White.copy(alpha = strength),
        1f to Color.Transparent,
        start = Offset(head, 0f),
        end = Offset(head + span * 0.35f, size.height),
    )
}

/**
 * Click a glass surface: no ripple, and the phone ticks once under the finger.
 *
 * The pressed state is drawn by [glassSurface] from the same [interactionSource], which is the whole
 * point — a ripple spreading across a frosted pane looks like a smear on it, and a pane that sinks
 * under the finger is the feedback a touch device actually wants.
 */
@Composable
fun Modifier.glassClickable(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
): Modifier {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            // On press, not on click: the tick has to arrive while the finger is still down, or it
            // confirms something the user has already stopped doing.
            if (interaction is PressInteraction.Press) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
        }
    }
    return combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onLongClickLabel = onLongClickLabel,
        // No tick of our own here: `combinedClickable` already performs the long-press one, and the
        // two together buzz twice for a single press.
        onLongClick = onLongClick,
        onClick = onClick,
    )
}

/**
 * Blur what is behind a real dialog window.
 *
 * Bottom sheets live in the app's own window now and frost the wallpaper the same way every other
 * pane does. The handful of genuine dialogs that remain cannot: they are separate windows, and a
 * separate window has no access to this one's canvas. What it *can* do, from Android 12 onward, is
 * ask the compositor to blur everything behind it — which is the same effect arrived at from the
 * other side.
 *
 * The system switches cross-window blur off in battery saver and on low-end devices, so the flag is
 * followed live rather than read once: a dialog opened while the blur is on and still open when the
 * battery saver kicks in has to let go of it.
 */
@Composable
fun Modifier.glassDialogWindow(): Modifier {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val glass = LocalGlass.current
    val view = LocalView.current
    val context = LocalContext.current
    val blurPx = with(LocalDensity.current) { (MaxBlurRadius * glass.blurStrength).roundToPx() }
    val manager = remember(context) { context.getSystemService(WindowManager::class.java) }
    var systemAllows by remember { mutableStateOf(manager.isCrossWindowBlurEnabled) }
    DisposableEffect(manager) {
        val listener = Consumer<Boolean> { systemAllows = it }
        manager.addCrossWindowBlurEnabledListener(listener)
        onDispose { manager.removeCrossWindowBlurEnabledListener(listener) }
    }
    val radius = if (systemAllows && glass.isGlassy(GlassSurface.DIALOGS)) blurPx else 0
    val window = (view.parent as? DialogWindowProvider)?.window
    DisposableEffect(window, radius) {
        if (window != null) {
            if (radius > 0) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = radius }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
        }
        onDispose {}
    }
    return this
}

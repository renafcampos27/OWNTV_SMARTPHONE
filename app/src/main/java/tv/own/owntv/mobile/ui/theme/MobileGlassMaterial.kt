package tv.own.owntv.mobile.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.GlassSurface

/**
 * The recipe for one depth of glass — how far off the page a surface sits.
 *
 * The four instances below are the app's whole vocabulary for "this is in front of that". Glassed
 * they differ in tint, frost, rim and cast shadow; not glassed they differ in tonal step and
 * elevation, so the same hierarchy reads either way.
 *
 * @param tint moves the fill off its tonal colour — positive towards white, negative towards black.
 * @param frost how deep this material's blur goes, relative to the deepest material rather than
 *   absolutely, so it stays a fraction of whatever the user's frost setting is (Rule Y-C).
 * @param rim the specular edge at rest.
 * @param body the internal glow's peak. Zero for everything but the floating material.
 * @param sheen the highlight along the top edge, where a pane catches the light.
 * @param edge the dark far edge, where the pane's own thickness shows instead of a highlight.
 * @param shadow how far the pane is cast off what is behind it.
 * @param hasBackdrop false for a material that never samples the frost ladder at all.
 * @param tone the surface colour this material becomes with the Glass Effect off.
 * @param elevation the Material elevation that goes with [tone], so the tonal mode has the same
 *   ordering the glass mode gets from [shadow].
 */
@Immutable
data class MobileGlassMaterial(
    val tint: Float,
    val frost: Float,
    val rim: Float,
    val body: Float,
    val sheen: Float,
    val edge: Float,
    val shadow: Dp,
    val hasBackdrop: Boolean,
    val elevation: Dp,
    private val tone: (MobileSurfaceTones) -> Color,
) {
    /** This material's colour with the effect off, and the base its glass tint is measured from. */
    fun tone(tones: MobileSurfaceTones): Color = tone.invoke(tones)
}

// The four instances. These are the app's design tokens: the TV's numbers are the starting point, but
// a television is three metres away and a phone is thirty centimetres, so the rim and the sheen come
// down (a hairline that reads as "moulded" at three metres reads as a wire at arm's length) and the
// tint spread goes up (the four depths have to separate on a 6-inch screen, not a 55-inch one).

/** Dialogs, sheets, toasts — the only material with a light of its own. */
val FloatingGlass = MobileGlassMaterial(
    tint = 0.16f, frost = 0.30f, rim = 0.13f, body = 0.34f, sheen = 0.78f, edge = 0.24f, shadow = 12.dp,
    hasBackdrop = true, elevation = 12.dp,
    tone = { it.floating },
)

/** Bars, rails, the mini player, the player's own controls. Barely there, on purpose. */
val ChromeGlass = MobileGlassMaterial(
    tint = 0.05f, frost = 0.24f, rim = 0.10f, body = 0f, sheen = 0.55f, edge = 0.18f, shadow = 6.dp,
    hasBackdrop = true, elevation = 6.dp,
    tone = { it.chrome },
)

/** Page panels and the detail backdrop. Pure frost, no colour: it holds other things. */
val ContainerGlass = MobileGlassMaterial(
    tint = 0f, frost = 0.20f, rim = 0.08f, body = 0f, sheen = 0.40f, edge = 0.14f, shadow = 2.dp,
    hasBackdrop = true, elevation = 2.dp,
    tone = { it.container },
)

/** Cards and rows. Negative tint, and no backdrop at all — it groups by darkening. */
val InlineGlass = MobileGlassMaterial(
    tint = -0.12f, frost = 0.18f, rim = 0.04f, body = 0f, sheen = 0f, edge = 0.11f, shadow = 0.dp,
    hasBackdrop = false, elevation = 0.dp,
    tone = { it.inline },
)

/** The deepest material's frost, which [MobileGlassMaterial.frost] is a fraction of. */
internal const val DEEPEST_FROST = 0.30f

/** Which material a surface is made of. */
fun materialFor(surface: GlassSurface): MobileGlassMaterial = when (surface) {
    GlassSurface.DIALOGS, GlassSurface.TOASTS -> FloatingGlass
    GlassSurface.SIDEBAR, GlassSurface.TOPBAR, GlassSurface.MINI_PLAYER,
    GlassSurface.PLAYER_CONTROLS,
    -> ChromeGlass
    GlassSurface.PANELS, GlassSurface.PREVIEW -> ContainerGlass
    GlassSurface.CARDS -> InlineGlass
}

/**
 * How many panes of glass deep the caller already is.
 *
 * A row inside a panel inside a sheet is three sheets of frost stacked on one wallpaper, and the
 * result is soup — every layer blurs what the layer below already blurred, and nothing has an edge
 * any more. So each pane declares a depth and the ones behind the first give things up.
 */
val LocalGlassLayer = compositionLocalOf { 0 }

/** What survives at a given nesting depth. */
@Immutable
data class GlassLayerTreatment(
    val frost: Boolean,
    val sheen: Boolean,
    val rim: Boolean,
    val edge: Boolean,
)

/** Layer 1 gets everything, layer 2 loses the frost, layer 3 and below keep only the dark edge. */
fun glassLayerTreatment(layer: Int): GlassLayerTreatment = when {
    layer <= 1 -> GlassLayerTreatment(frost = true, sheen = true, rim = true, edge = true)
    layer == 2 -> GlassLayerTreatment(frost = false, sheen = true, rim = true, edge = true)
    else -> GlassLayerTreatment(frost = false, sheen = false, rim = false, edge = true)
}

/** Previews and cards start one layer in, because they are always drawn on top of something else. */
private fun GlassSurface.defaultLayer(): Int = when (this) {
    GlassSurface.PREVIEW, GlassSurface.CARDS -> 2
    else -> 1
}

/**
 * The layer a surface actually draws at: its own, or the ambient floor, whichever is deeper.
 *
 * A floor rather than an increment, because `glassSurface` is a modifier and a modifier cannot hand a
 * `CompositionLocal` to the content it wraps — [GlassNest] is what raises it.
 */
fun effectiveGlassLayer(surface: GlassSurface, floor: Int): Int =
    maxOf(surface.defaultLayer(), floor)

/** Everything inside [content] is a layer deeper than a [host] pane. */
@Composable
fun GlassNest(host: GlassSurface, content: @Composable () -> Unit) {
    val deeper = effectiveGlassLayer(host, LocalGlassLayer.current) + 1
    CompositionLocalProvider(LocalGlassLayer provides deeper, content = content)
}

/** This material's colour with the Glass Effect off. */
@Composable
@ReadOnlyComposable
fun tonalFillFor(surface: GlassSurface): Color =
    materialFor(surface).tone(LocalSurfaceTones.current)

/** The fill shifted by a material's tint — towards white above zero, towards black below it. */
internal fun Color.shiftedBy(tint: Float): Color = when {
    tint > 0f -> androidx.compose.ui.graphics.lerp(this, Color.White, tint)
    tint < 0f -> androidx.compose.ui.graphics.lerp(this, Color.Black, -tint)
    else -> this
}

/**
 * The internal glow of a floating pane: light entering from above the top-left corner and falling
 * away, with the bottom weighted down so the pane has a lit end and a resting end.
 */
internal fun luminousBody(size: Size, peak: Float, colour: Color): Brush = Brush.radialGradient(
    colors = listOf(colour.copy(alpha = 0.10f * peak), Color.Transparent),
    center = Offset(size.width * 0.14f, -size.height * 0.08f),
    radius = size.maxDimension * 0.72f,
)

/** The weight under a floating pane, which is what keeps the glow from reading as a wash. */
internal fun luminousShade(size: Size): Brush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.07f)),
    startY = size.height * 0.58f,
    endY = size.height,
)

/**
 * The bright half of the rim: strongest at the top-left where the light comes from, gone by the middle.
 *
 * This is the single change that makes a pane read as moulded glass rather than as a rounded rectangle
 * with a hairline round it — the edge has to know where the light is.
 */
internal fun luminousRim(size: Size, peak: Float, colour: Color): Brush = Brush.linearGradient(
    0f to colour.copy(alpha = peak),
    0.28f to colour.copy(alpha = peak * 0.35f),
    0.55f to Color.Transparent,
    start = Offset.Zero,
    end = Offset(size.width, size.height),
)

/** The dark half: the far edge, where the pane's thickness shows as shadow instead of highlight. */
internal fun luminousEdge(size: Size, dark: Float): Brush = Brush.linearGradient(
    0f to Color.Black.copy(alpha = dark * 0.25f),
    0.74f to Color.Black.copy(alpha = dark * 0.55f),
    1f to Color.Black.copy(alpha = dark),
    start = Offset.Zero,
    end = Offset(size.width, size.height),
)

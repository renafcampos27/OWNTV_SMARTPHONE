package tv.own.owntv.mobile.ui.stage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import tv.own.owntv.core.theme.GlassSurface

/** Stage neutrals from Mobile 1.1.0, without its library/sports navigation or render-effect stack. */
@Immutable
data class StagePalette(val page: Color, val text: Color, val muted: Color, val body: Color,
    val card: Color, val chrome: Color, val ink: Color, val danger: Color) {
    val control: Color get() = ink.copy(alpha = .06f).compositeOver(page)
    val outline: Color get() = ink.copy(alpha = .12f).compositeOver(page)
    companion object {
        val Dark = StagePalette(Color(0xFF05080A), Color(0xFFF1F5F3), Color(0xFFA7B3AE),
            Color(0xFFC9D3CF), Color(0xFF111A1E), Color(0xFF121A1C), Color.White, Color(0xFFFF8A7A))
        val Light = StagePalette(Color(0xFFF4F7F5), Color(0xFF0F1A16), Color(0xFF4C5A54),
            Color(0xFF2D3A35), Color(0xFFE4EAE7), Color.White, Color(0xFF0C1814), Color(0xFFD23B2E))
    }
}

val LocalStagePalette = staticCompositionLocalOf { StagePalette.Dark }

/** Flat Stage panels: no blur textures, arrival animation, shadows or per-frame native paint. */
@Composable
fun Modifier.stageSurface(surface: GlassSurface, shape: Shape, selected: Boolean = false, pressed: Boolean = false, fillOverride: Color? = null): Modifier {
    val palette = LocalStagePalette.current
    val accent = MaterialTheme.colorScheme.primary
    val base = fillOverride ?: when (surface) {
        GlassSurface.PANELS, GlassSurface.PREVIEW -> palette.page
        GlassSurface.CARDS -> palette.card
        else -> palette.chrome
    }
    val fill = when { selected -> accent.copy(alpha = .13f).compositeOver(base)
        pressed -> palette.ink.copy(alpha = .07f).compositeOver(base)
        else -> base }
    return background(fill, shape).border(1.dp, if (selected) accent.copy(alpha = .55f) else palette.outline, shape)
}

/** A static background wash, cached by size/theme. No moving or sampled full-screen layers. */
@Composable
fun Modifier.stageBackdrop(): Modifier {
    val palette = LocalStagePalette.current
    val accent = MaterialTheme.colorScheme.primary
    return drawWithCache {
        val glow = Brush.radialGradient(listOf(accent.copy(alpha = .09f), Color.Transparent),
            center = Offset(size.width * .85f, 0f), radius = size.maxDimension.coerceAtLeast(1f) * .8f)
        onDrawBehind { drawRect(palette.page); drawRect(glow) }
    }
}

/** Selection remains visible without changing text contrast, in both LTR and RTL. */
fun Modifier.stageSelectedEdge(color: Color): Modifier = drawWithCache {
    val width = 3.dp.toPx()
    onDrawBehind {
        drawRoundRect(color, topLeft = Offset(if (layoutDirection == LayoutDirection.Rtl) size.width - width else 0f, 8.dp.toPx()),
            size = Size(width, (size.height - 16.dp.toPx()).coerceAtLeast(0f)), cornerRadius = CornerRadius(width))
    }
}

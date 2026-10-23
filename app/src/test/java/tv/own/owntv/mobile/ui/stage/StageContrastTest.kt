package tv.own.owntv.mobile.ui.stage

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.mobile.ui.theme.mobileColorScheme

class StageContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance()); val lo = minOf(a.luminance(), b.luminance())
        return (hi + .05f) / (lo + .05f)
    }
    @Test fun bodyAndSecondaryTextRemainReadableOnPageCardsAndMenus() {
        for (palette in listOf(StagePalette.Dark, StagePalette.Light)) {
            for (surface in listOf(palette.page, palette.card, palette.chrome, palette.control)) {
                assertTrue(contrast(palette.text, surface) >= 4.5f)
                assertTrue(contrast(palette.muted, surface) >= 4.5f)
            }
        }
    }
    @Test fun selectedChannelNeverUsesBlackTextOverTheDarkAccentWash() {
        for (dark in listOf(true, false)) for (accent in AccentColor.entries) {
            val scheme = mobileColorScheme(dark, accent)
            val selected = scheme.primary.copy(alpha = .13f).compositeOver(scheme.surfaceContainer)
            assertTrue("$dark $accent", contrast(scheme.onSurface, selected) >= 4.5f)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        }
    }
    @Test fun currentPaletteIsStageAndRetainsTheUserAccent() {
        for (dark in listOf(true, false)) {
            val scheme = mobileColorScheme(dark, AccentColor.TEAL)
            assertEquals(if (dark) StagePalette.Dark.page else StagePalette.Light.page, scheme.background)
            assertEquals(if (dark) StagePalette.Dark.text else StagePalette.Light.text, scheme.onSurface)
            assertNotEquals(scheme.primary, scheme.background)
        }
    }
}

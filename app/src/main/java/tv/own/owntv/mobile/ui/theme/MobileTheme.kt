package tv.own.owntv.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.roles
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.core.theme.UiZoom

/**
 * The app's theme, driven entirely by the settings the user already has.
 *
 * Every value is core's, so the choices made on the TV app — dark or light, the accent, the zoom,
 * the font, the Glass Effect — come across with a backup restore and apply here unchanged.
 */
@Composable
fun MobileTheme(content: @Composable () -> Unit) {
    val settings: SettingsRepository = koinInject()
    val themeMode by settings.themeMode.collectAsStateWithLifecycle(ThemeMode.DARK)
    val accent by settings.accent.collectAsStateWithLifecycle(AccentColor.TEAL)
    val customAccent by settings.customAccent.collectAsStateWithLifecycle("")
    val uiZoomPercent by settings.uiZoomPercent.collectAsStateWithLifecycle(UiZoom.DEFAULT)
    val fonts by settings.fontCustomization.collectAsStateWithLifecycle(FontCustomization())
    val glass by settings.glassConfig.collectAsStateWithLifecycle(GlassConfig())
    val animations by settings.animationLevel.collectAsStateWithLifecycle(AnimationLevel.FULL)

    MobileTheme(
        themeMode = themeMode,
        accent = accent,
        customAccent = customAccent,
        uiZoomPercent = uiZoomPercent,
        fonts = fonts,
        glass = glass,
        animations = animations,
        content = content,
    )
}

/** The same theme with its inputs supplied directly — for previews and for the theme gallery. */
@Composable
fun MobileTheme(
    themeMode: ThemeMode,
    accent: AccentColor,
    customAccent: String = "",
    uiZoomPercent: Int = UiZoom.DEFAULT,
    fonts: FontCustomization = FontCustomization(),
    glass: GlassConfig = GlassConfig(),
    animations: AnimationLevel = AnimationLevel.FULL,
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val base = LocalDensity.current

    CompositionLocalProvider(
        // UI Zoom scales every dp and the font setting scales every sp, so a layout keeps its
        // proportions at any setting instead of text outgrowing the box it sits in.
        LocalDensity provides Density(
            density = base.density * UiZoom.factor(uiZoomPercent),
            fontScale = base.fontScale * UiFontScale.factor(fonts.sizePercent),
        ),
        LocalGlass provides glass,
        LocalAnimations provides animations,
        LocalAccentOnVideo provides accentOnVideo(accent, customAccent),
        LocalSurfaceTones provides mobileSurfaceTones(isDark),
        LocalMobileMotion provides remember(animations) { MobileMotion(animations) },
    ) {
        val colors = mobileColorScheme(isDark, accent, customAccent)
        MaterialTheme(
            colorScheme = colors,
            typography = mobileTypography(fonts.mainFamily.asComposeFamily()),
        ) {
            // Nothing in this app sits on a Material `Surface` — the shell paints its own glass — so
            // the content colour is never set for us and stays at Compose's own default of black.
            // Any text or icon that does not name a colour would then be black on a dark pane.
            CompositionLocalProvider(LocalContentColor provides colors.onSurface, content = content)
        }
    }
}

/**
 * The user's reduce-motion setting, read once here rather than by every control that animates.
 *
 * Off means off in this app, not softened: [AnimationLevel.scale] collapses a duration to zero.
 */
val LocalAnimations = staticCompositionLocalOf { AnimationLevel.FULL }

/**
 * The accent to use on top of the picture — see [accentOnVideo].
 *
 * Nothing drawn over video reads `colorScheme.primary`: in the light theme that is a dark accent on
 * a dark scene.
 */
val LocalAccentOnVideo = staticCompositionLocalOf { Color(AccentColor.TEAL.roles(isDark = true).primary) }

/**
 * What each glass material's surface is when the effect is off — see [MobileSurfaceTones].
 *
 * A composition local rather than a lookup on the colour scheme, because three of the four are the
 * shell's per-region fills and those are not M3 roles.
 */
val LocalSurfaceTones = staticCompositionLocalOf { mobileSurfaceTones(isDark = true) }

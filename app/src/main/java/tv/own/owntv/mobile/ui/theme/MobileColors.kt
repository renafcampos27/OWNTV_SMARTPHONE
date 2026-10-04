package tv.own.owntv.mobile.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import tv.own.owntv.mobile.R
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AccentRoleValues
import tv.own.owntv.core.theme.OwnTVPalette
import tv.own.owntv.core.theme.accentRolesFromSeed
import tv.own.owntv.core.theme.parseAccentHex
import tv.own.owntv.core.theme.roles

/**
 * OwnTV's palette as a Material 3 [ColorScheme] for touch.
 *
 * Every value comes from core's [OwnTVPalette], which is also what the TV app draws with, so the
 * two apps are recognisably one product and a colour change lands in both at once. What differs
 * here is only the mapping: `androidx.compose.material3` rather than `androidx.tv.material3`.
 */
/** The accent's name, from core's strings — the same label the TV app's settings screen shows. */
val AccentColor.labelRes: Int
    @StringRes get() = when (this) {
        AccentColor.TEAL -> R.string.settings_accent_teal
        AccentColor.BLUE -> R.string.settings_accent_blue
        AccentColor.VIOLET -> R.string.settings_accent_violet
        AccentColor.GREEN -> R.string.settings_accent_green
        AccentColor.AMBER -> R.string.settings_accent_amber
    }

/**
 * The accent as it must look over a picture.
 *
 * The player draws on video, never on a themed surface, so the light theme's accent — chosen to
 * carry against a near-white page — is the wrong colour there: it is dark, and a dark seek bar on a
 * dark scene is invisible. The dark theme's accent is derived to sit on black, which is exactly what
 * is behind the player in both themes, so the player asks for that one regardless of the theme.
 */
fun accentOnVideo(accent: AccentColor, customAccent: String = ""): Color {
    val roles = parseAccentHex(customAccent)
        ?.let { accentRolesFromSeed(it, isDark = true) }
        ?: accent.roles(isDark = true)
    return Color(roles.primary)
}

/**
 * The colour each of the four glass materials becomes with the Glass Effect off.
 *
 * Three of them are the shell's per-region fills rather than M3 container steps, because that is
 * what the TV app paints and the two apps are one product: the navigation takes the rail colour,
 * the page takes the content colour, a row takes the detail-pane colour. The elevation ladder is
 * greyer and flattens the regions into each other, which is the look those three exist to avoid.
 */
@Immutable
data class MobileSurfaceTones(
    /** Dialogs, sheets and toasts — the TV app's own dialog fill. */
    val floating: Color,
    /** Bars, rails and the mini player. */
    val chrome: Color,
    /** Page panels and the detail backdrop. */
    val container: Color,
    /** Cards and rows. */
    val inline: Color,
)

fun mobileSurfaceTones(isDark: Boolean): MobileSurfaceTones = if (isDark) {
    MobileSurfaceTones(
        floating = Color(OwnTVPalette.DarkSurfaceContainerHigh),
        chrome = Color(OwnTVPalette.DarkRailPanel),
        container = Color(OwnTVPalette.DarkContentPanel),
        inline = Color(OwnTVPalette.DarkPreviewPanel),
    )
} else {
    MobileSurfaceTones(
        floating = Color(OwnTVPalette.LightSurfaceContainerHigh),
        chrome = Color(OwnTVPalette.LightRailPanel),
        container = Color(OwnTVPalette.LightContentPanel),
        inline = Color(OwnTVPalette.LightPreviewPanel),
    )
}

fun mobileColorScheme(
    isDark: Boolean,
    accent: AccentColor,
    customAccent: String = "",
): ColorScheme {
    // A valid custom hex wins over the preset: the seed renders exactly, and its supporting
    // contrast roles are derived from it (core's derivation, the same one the TV app uses).
    val roles: AccentRoleValues = parseAccentHex(customAccent)
        ?.let { accentRolesFromSeed(it, isDark) }
        ?: accent.roles(isDark)

    return if (isDark) {
        darkColorScheme(
            primary = Color(roles.primary),
            onPrimary = Color(roles.onPrimary),
            primaryContainer = Color(roles.primaryContainer),
            onPrimaryContainer = Color(roles.onPrimaryContainer),
            secondary = Color(OwnTVPalette.DarkSecondary),
            onSecondary = Color(OwnTVPalette.DarkOnSecondary),
            secondaryContainer = Color(OwnTVPalette.DarkSecondaryContainer),
            onSecondaryContainer = Color(OwnTVPalette.DarkOnSecondaryContainer),
            tertiary = Color(OwnTVPalette.DarkTertiary),
            onTertiary = Color(OwnTVPalette.DarkOnTertiary),
            tertiaryContainer = Color(OwnTVPalette.DarkTertiaryContainer),
            onTertiaryContainer = Color(OwnTVPalette.DarkOnTertiaryContainer),
            background = Color(OwnTVPalette.DarkBackground),
            onBackground = Color(OwnTVPalette.DarkOnSurface),
            surface = Color(OwnTVPalette.DarkSurface),
            onSurface = Color(OwnTVPalette.DarkOnSurface),
            surfaceVariant = Color(OwnTVPalette.DarkSurfaceContainerHigh),
            onSurfaceVariant = Color(OwnTVPalette.DarkOnSurfaceVariant),
            surfaceContainerLowest = Color(OwnTVPalette.DarkSurfaceContainerLowest),
            surfaceContainerLow = Color(OwnTVPalette.DarkSurfaceContainerLow),
            surfaceContainer = Color(OwnTVPalette.DarkSurfaceContainer),
            surfaceContainerHigh = Color(OwnTVPalette.DarkSurfaceContainerHigh),
            surfaceContainerHighest = Color(OwnTVPalette.DarkSurfaceContainerHighest),
            outline = Color(OwnTVPalette.DarkOutline),
            outlineVariant = Color(OwnTVPalette.DarkOutlineVariant),
            error = Color(OwnTVPalette.DarkError),
        )
    } else {
        lightColorScheme(
            primary = Color(roles.primary),
            onPrimary = Color(roles.onPrimary),
            primaryContainer = Color(roles.primaryContainer),
            onPrimaryContainer = Color(roles.onPrimaryContainer),
            secondary = Color(OwnTVPalette.LightSecondary),
            onSecondary = Color(OwnTVPalette.LightOnSecondary),
            secondaryContainer = Color(OwnTVPalette.LightSecondaryContainer),
            onSecondaryContainer = Color(OwnTVPalette.LightOnSecondaryContainer),
            tertiary = Color(OwnTVPalette.LightTertiary),
            onTertiary = Color(OwnTVPalette.LightOnTertiary),
            tertiaryContainer = Color(OwnTVPalette.LightTertiaryContainer),
            onTertiaryContainer = Color(OwnTVPalette.LightOnTertiaryContainer),
            background = Color(OwnTVPalette.LightBackground),
            onBackground = Color(OwnTVPalette.LightOnSurface),
            surface = Color(OwnTVPalette.LightSurface),
            onSurface = Color(OwnTVPalette.LightOnSurface),
            surfaceVariant = Color(OwnTVPalette.LightSurfaceContainerHigh),
            onSurfaceVariant = Color(OwnTVPalette.LightOnSurfaceVariant),
            surfaceContainerLowest = Color(OwnTVPalette.LightSurfaceContainerLowest),
            surfaceContainerLow = Color(OwnTVPalette.LightSurfaceContainerLow),
            surfaceContainer = Color(OwnTVPalette.LightSurfaceContainer),
            surfaceContainerHigh = Color(OwnTVPalette.LightSurfaceContainerHigh),
            surfaceContainerHighest = Color(OwnTVPalette.LightSurfaceContainerHighest),
            outline = Color(OwnTVPalette.LightOutline),
            outlineVariant = Color(OwnTVPalette.LightOutlineVariant),
            error = Color(OwnTVPalette.LightError),
        )
    }
}

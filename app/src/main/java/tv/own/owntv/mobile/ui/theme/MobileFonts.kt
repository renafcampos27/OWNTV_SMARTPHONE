package tv.own.owntv.mobile.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.mobile.R

/**
 * The font families the user can pick from, on the phone.
 *
 * The choice is stored in core and shared with the TV app, so every one has to exist here too —
 * a user who picked Poppins on the TV and restores that backup gets Poppins here. Lora ships in
 * core (its companion QR screen uses it); the rest are this app's own `res/font`, copied from the
 * TV app's, because a font binary is a per-app resource rather than shared code.
 */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun variableFont(resourceId: Int, weight: FontWeight, style: FontStyle = FontStyle.Normal) =
    Font(
        resourceId,
        weight = weight,
        style = style,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )

private val LoraFamily = FontFamily(
    variableFont(R.font.lora_variable, FontWeight.Normal),
    variableFont(R.font.lora_variable, FontWeight.Medium),
    variableFont(R.font.lora_variable, FontWeight.SemiBold),
    variableFont(R.font.lora_variable, FontWeight.Bold),
    variableFont(R.font.lora_italic_variable, FontWeight.Normal, FontStyle.Italic),
    variableFont(R.font.lora_italic_variable, FontWeight.Bold, FontStyle.Italic),
)

private val PlayfairDisplayFamily = FontFamily(
    variableFont(R.font.playfair_display_variable, FontWeight.Normal),
    variableFont(R.font.playfair_display_variable, FontWeight.Medium),
    variableFont(R.font.playfair_display_variable, FontWeight.SemiBold),
    variableFont(R.font.playfair_display_variable, FontWeight.Bold),
    variableFont(R.font.playfair_display_italic_variable, FontWeight.Normal, FontStyle.Italic),
    variableFont(R.font.playfair_display_italic_variable, FontWeight.Bold, FontStyle.Italic),
)

private val DancingScriptFamily = FontFamily(
    variableFont(R.font.dancing_script_variable, FontWeight.Normal),
    variableFont(R.font.dancing_script_variable, FontWeight.Medium),
    variableFont(R.font.dancing_script_variable, FontWeight.SemiBold),
    variableFont(R.font.dancing_script_variable, FontWeight.Bold),
)

/**
 * Shipped rather than asked for, unlike [FontFamily.SansSerif].
 *
 * `FontFamily.Monospace` resolves to whatever the phone registers as "monospace", and an OEM font
 * pack is free to alias that to the same face it uses for "sans-serif" — which is exactly what
 * happened: two entries in the font picker, one typeface. A file in the app cannot be aliased away.
 * It is the same JetBrains Mono the TV app already carries for its settings value column.
 */
// The default since core made it one (the TV's Stage redesign): every weight from one variable file.
private val PlusJakartaSansFamily = FontFamily(
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.Normal),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.Medium),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.SemiBold),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.Bold),
    variableFont(R.font.plus_jakarta_sans_variable, FontWeight.ExtraBold),
)

private val JetBrainsMonoFamily = FontFamily(
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Normal),
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Medium),
    variableFont(R.font.jetbrains_mono_variable, FontWeight.SemiBold),
    variableFont(R.font.jetbrains_mono_variable, FontWeight.Bold),
)

private val PoppinsFamily = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

/**
 * The font *file* the playback engine copies into libass's font directory, so mpv draws a subtitle
 * in the face the user chose rather than in its own built-in one.
 *
 * `0` means "no bundled file", which is the right answer for the two system faces — libass asks
 * fontconfig for those by name. One weight each: libass picks a file, not a variable-font axis, and
 * a subtitle is not drawn bold.
 *
 * This is the television's `subtitleFontResource`, over the same nine font files. Without it the
 * setting reached the app's own subtitle layer and nothing else, so a font chosen on the television
 * and synced here was quietly ignored for most subtitles.
 */
val AppFontFamily.subtitleFontResource: Int
    get() = when (this) {
        AppFontFamily.LORA -> R.font.lora_variable
        AppFontFamily.PLAYFAIR_DISPLAY -> R.font.playfair_display_variable
        AppFontFamily.DANCING_SCRIPT -> R.font.dancing_script_variable
        AppFontFamily.POPPINS -> R.font.poppins_regular
        AppFontFamily.PLUS_JAKARTA_SANS -> R.font.plus_jakarta_sans_variable
        AppFontFamily.SYSTEM_SANS,
        AppFontFamily.MONOSPACE,
        -> 0
    }

fun AppFontFamily.asComposeFamily(): FontFamily = when (this) {
    AppFontFamily.LORA -> LoraFamily
    AppFontFamily.SYSTEM_SANS -> FontFamily.SansSerif
    AppFontFamily.MONOSPACE -> JetBrainsMonoFamily
    AppFontFamily.PLAYFAIR_DISPLAY -> PlayfairDisplayFamily
    AppFontFamily.DANCING_SCRIPT -> DancingScriptFamily
    AppFontFamily.POPPINS -> PoppinsFamily
    AppFontFamily.PLUS_JAKARTA_SANS -> PlusJakartaSansFamily
}

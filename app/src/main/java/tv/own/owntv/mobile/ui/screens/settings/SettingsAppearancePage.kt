package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.GlassConfig
import tv.own.owntv.core.theme.GlassPreset
import tv.own.owntv.core.theme.GlassSurface
import tv.own.owntv.core.theme.ThemeMode
import tv.own.owntv.core.theme.UiZoom
import tv.own.owntv.core.theme.roles
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ColorPickerSheet
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.labelRes
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/**
 * How the app looks. Everything on this page is applied by the theme the moment it is set, and the
 * panel at the top is a piece of the app drawn with the current values — the font, the accent and
 * the zoom are visible in it without leaving the page to check.
 */
@Composable
fun SettingsAppearancePage(
    onOpenLeaf: (SettingsLeaf) -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val themeMode = vm.settings.themeMode.pref(ThemeMode.DARK)
    val accent = vm.settings.accent.pref(AccentColor.TEAL)
    val glass = vm.settings.glassConfig.pref(GlassConfig())
    val zoom = vm.settings.uiZoomPercent.pref(UiZoom.DEFAULT)
    val animations = vm.settings.animationLevel.pref(AnimationLevel.FULL)
    val highlightWidth = vm.settings.focusHighlightWidth.pref(2)
    val customAccent = vm.settings.customAccent.pref("")

    var sheet by remember { mutableStateOf<AppearanceSheet?>(null) }
    // The colour picker is opened from the page rather than from inside another sheet, so the
    // two never stack: the highlight sheet closes and hands over.
    var picker by remember { mutableStateOf<ColorTarget?>(null) }
    // The zoom the low-memory warning is holding, and whether its risk has already been accepted.
    var pendingLowZoom by remember { mutableStateOf<Int?>(null) }
    var lowZoomAccepted by remember { mutableStateOf(zoom < UiZoom.LOW_RAM_WARN) }

    SettingsPage(modifier) {
        item(key = "preview") { AppearancePreview() }
        settingsLeafRows(SettingsGroup.APPEARANCE, onOpenLeaf)

        settingsGroup(key = "theme") {
            SettingRow(
                title = stringResource(R.string.settings_theme),
                subtitle = stringResource(R.string.settings_theme_description),
                value = stringResource(themeMode.labelRes()),
                onClick = { sheet = AppearanceSheet.THEME },
            )

            SettingRow(
                title = stringResource(R.string.settings_accent),
                subtitle = stringResource(R.string.settings_accent_description),
                // A custom colour wins over the preset in the theme, so it is what the row reports.
                value = customAccent.ifBlank { stringResource(accent.labelRes) },
                onClick = { sheet = AppearanceSheet.ACCENT },
            )
        }
        settingsGroup(key = "highlight") {
            SettingRow(
                title = stringResource(R.string.settings_selection_highlight),
                subtitle = stringResource(R.string.settings_selection_highlight_description),
                value = focusWidthLabel(highlightWidth),
                onClick = { sheet = AppearanceSheet.HIGHLIGHT },
            )
        }
        // The glow is a light behind solid panels, so it only means anything on a dark theme that is
        // not already showing a photograph through them — the same condition the TV app uses.
        if (themeMode == ThemeMode.DARK && !glass.enabled) {
            settingsGroup(key = "glow") {
                SettingRow(
                    title = stringResource(R.string.settings_ambient_glow),
                    subtitle = stringResource(R.string.settings_ambient_glow_description),
                    checked = vm.settings.ambientGlowEnabled.pref(false),
                    onCheckedChange = { vm.edit { setAmbientGlowEnabled(it) } },
                )

                SettingRow(
                    title = stringResource(R.string.settings_ambient_glow_pulse),
                    checked = vm.settings.ambientGlowPulse.pref(false),
                    onCheckedChange = { vm.edit { setAmbientGlowPulse(it) } },
                )
            }
        }
        settingsGroup(key = "zoom") {
            SettingsSlider(
                title = stringResource(R.string.settings_ui_zoom),
                subtitle = stringResource(R.string.settings_ui_zoom_description),
                value = zoom,
                range = UiZoom.MIN..UiZoom.MAX,
                steps = stepsFor(UiZoom.MIN, UiZoom.MAX, UiZoom.STEP),
                // Below the warning point a screen holds so many more items that a small-memory
                // device can run out of it. Ask once, on the drag that crosses the line, and
                // remember the answer for the rest of the visit rather than asking at every step.
                onValueChange = { raw ->
                    val pct = UiZoom.clamp(raw)
                    if (pct < UiZoom.LOW_RAM_WARN && !lowZoomAccepted) {
                        pendingLowZoom = pct
                    } else {
                        vm.edit { setUiZoomPercent(pct) }
                    }
                },
            )

            SettingRow(
                title = stringResource(R.string.settings_animations),
                subtitle = stringResource(R.string.settings_animations_description_mobile),
                checked = animations == AnimationLevel.FULL,
                onCheckedChange = { on ->
                    vm.edit { setAnimationLevel(if (on) AnimationLevel.FULL else AnimationLevel.OFF) }
                },
            )
        }

    }

    when (sheet) {
        AppearanceSheet.THEME -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_theme),
            choices = ThemeMode.entries.map { SettingsChoice(it, stringResource(it.labelRes())) },
            selected = themeMode,
            onSelect = { mode -> vm.edit { setThemeMode(mode) } },
            onDismiss = { sheet = null },
        )
        AppearanceSheet.ACCENT -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_accent),
            choices = AccentColor.entries.map { SettingsChoice<AccentColor?>(it, stringResource(it.labelRes)) },
            // A custom colour overrides the preset in the theme, so while one is set no preset is
            // the answer and none of them is ticked.
            selected = accent.takeIf { customAccent.isBlank() },
            // A preset and a custom hex are two ways to answer the same question, so choosing a
            // preset clears the hex — otherwise the hex would keep winning and the taps do nothing.
            onSelect = { color -> color?.let { picked -> vm.edit { setCustomAccent(""); setAccent(picked) } } },
            onDismiss = { sheet = null },
            footer = {
                MobileListRow(
                    title = stringResource(R.string.settings_color_picker),
                    subtitle = customAccent.ifBlank { null },
                    selected = customAccent.isNotBlank(),
                    onClick = { sheet = null; picker = ColorTarget.ACCENT },
                )
            },
        )
        AppearanceSheet.HIGHLIGHT -> HighlightSheet(
            vm,
            onOpenPicker = { sheet = null; picker = ColorTarget.HIGHLIGHT },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    picker?.let { target ->
        val isDark = themeMode != ThemeMode.LIGHT
        val accentHexes = AccentColor.entries.map { "#%06X".format(it.roles(isDark).primary and 0xFFFFFF) }
        when (target) {
            ColorTarget.ACCENT -> ColorPickerSheet(
                title = stringResource(R.string.settings_accent),
                presets = accentHexes,
                initial = customAccent,
                // A preset accent and a custom colour answer the same question, and the custom one
                // wins in the theme — so picking here is what makes the choice stick.
                onPick = { hex -> vm.edit { setCustomAccent(hex) } },
                onDismiss = { picker = null },
            )
            ColorTarget.HIGHLIGHT -> ColorPickerSheet(
                title = stringResource(R.string.settings_selection_highlight),
                // Gold and white lead, because they are what people ask for when they want the
                // cursor to shout; the accents follow.
                presets = listOf("#F5B400", "#FFFFFF") + accentHexes,
                initial = vm.settings.focusHighlight.pref(""),
                onPick = { hex -> vm.edit { setFocusHighlight(hex) } },
                onDismiss = { picker = null },
            )
        }
    }

    pendingLowZoom?.let { target ->
        AlertDialog(
            modifier = Modifier.glassDialogWindow(),
            onDismissRequest = { pendingLowZoom = null },
            title = { Text(stringResource(R.string.settings_low_zoom_warning_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.settings_low_zoom_warning_mobile,
                        UiZoom.LOW_RAM_WARN,
                        UiZoom.LOW_RAM_WARN,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        lowZoomAccepted = true
                        pendingLowZoom = null
                        vm.edit { setUiZoomPercent(target) }
                    },
                ) { Text(stringResource(R.string.settings_low_zoom_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingLowZoom = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private enum class AppearanceSheet { THEME, ACCENT, HIGHLIGHT }

/** A slider's stops, so it lands on core's step size instead of anywhere between two of them. */
internal fun stepsFor(min: Int, max: Int, step: Int): Int = ((max - min) / step) - 1

/** The ring's width as a word, the way the TV app names it: Thin, Normal, Thick, Extra thick. */
@Composable
private fun focusWidthLabel(dp: Int): String = stringResource(
    when (dp) {
        1 -> R.string.settings_focus_width_thin
        4, 5 -> R.string.settings_focus_width_thick
        6 -> R.string.settings_focus_width_extra
        else -> R.string.settings_focus_width_normal
    },
)

@Composable
private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.LIGHT -> R.string.settings_theme_light
}

@Composable
internal fun AppFontFamily.labelRes(): Int = when (this) {
    AppFontFamily.LORA -> R.string.settings_font_lora
    AppFontFamily.SYSTEM_SANS -> R.string.settings_font_system_sans
    AppFontFamily.MONOSPACE -> R.string.settings_font_monospace
    AppFontFamily.PLAYFAIR_DISPLAY -> R.string.settings_font_playfair_display
    AppFontFamily.DANCING_SCRIPT -> R.string.settings_font_dancing_script
    AppFontFamily.POPPINS -> R.string.settings_font_poppins
    AppFontFamily.PLUS_JAKARTA_SANS -> R.string.settings_font_plus_jakarta_sans
}

@Composable
internal fun GlassSurface.labelRes(): Int = when (this) {
    GlassSurface.PANELS -> R.string.settings_glass_surface_panels
    GlassSurface.SIDEBAR -> R.string.settings_glass_surface_sidebar
    GlassSurface.PREVIEW -> R.string.settings_glass_surface_preview
    GlassSurface.DIALOGS -> R.string.settings_glass_surface_dialogs
    GlassSurface.TOPBAR -> R.string.settings_glass_surface_topbar
    GlassSurface.CARDS -> R.string.settings_glass_surface_cards
    GlassSurface.MINI_PLAYER -> R.string.settings_glass_surface_miniplayer
    GlassSurface.PLAYER_CONTROLS -> R.string.settings_glass_surface_player_controls
    GlassSurface.TOASTS -> R.string.settings_glass_surface_toasts
}

@Composable
internal fun GlassPreset.labelRes(): Int = when (this) {
    GlassPreset.ULTRA_CLEAR -> R.string.settings_glass_preset_ultra_clear
    GlassPreset.CLEAR -> R.string.settings_glass_preset_clear
    GlassPreset.BALANCED -> R.string.settings_glass_preset_balanced
    GlassPreset.TINTED -> R.string.settings_glass_preset_tinted
    GlassPreset.OPAQUE -> R.string.settings_glass_preset_opaque
    GlassPreset.AURORA -> R.string.settings_glass_preset_aurora
    GlassPreset.CUSTOM -> R.string.settings_glass_preset_custom
}

@Composable
internal fun GlassPreset.descriptionRes(): Int = when (this) {
    GlassPreset.ULTRA_CLEAR -> R.string.settings_glass_preset_ultra_clear_description
    GlassPreset.CLEAR -> R.string.settings_glass_preset_clear_description
    GlassPreset.BALANCED -> R.string.settings_glass_preset_balanced_description
    GlassPreset.TINTED -> R.string.settings_glass_preset_tinted_description
    GlassPreset.OPAQUE -> R.string.settings_glass_preset_opaque_description
    GlassPreset.AURORA -> R.string.settings_glass_preset_aurora_description
    GlassPreset.CUSTOM -> R.string.settings_glass_preset_custom_description
}

/** A card, a line of text and the accent — the three things every setting on this page changes. */
@Composable
private fun AppearancePreview() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall),
    ) {
        Column(Modifier.padding(MobileDimens.GapMedium)) {
            Text(
                text = stringResource(R.string.settings_panel_width_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_font_preview),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                modifier = Modifier.padding(top = MobileDimens.GapSmall),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                )
                Box(
                    Modifier
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp),
                        ),
                )
                Text(
                    text = stringResource(R.string.settings_focus_highlight_sample),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** The ring's colour and thickness. Blank colour means "follow the accent", which is the default. */
@Composable
private fun HighlightSheet(vm: SettingsViewModel, onOpenPicker: () -> Unit, onDismiss: () -> Unit) {
    val isDark = vm.settings.themeMode.pref(ThemeMode.DARK) != ThemeMode.LIGHT
    val current = vm.settings.focusHighlight.pref("")
    val width = vm.settings.focusHighlightWidth.pref(2)
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_selection_highlight),
    ) {
        SettingsSlider(
            title = stringResource(R.string.settings_focus_thickness),
            value = width,
            range = 1..6,
            valueLabel = focusWidthLabel(width),
            onValueChange = { dp -> vm.edit { setFocusHighlightWidth(dp) } },
        )
        SettingRow(
            title = stringResource(R.string.settings_accent),
            checked = current.isBlank(),
            onCheckedChange = { vm.edit { setFocusHighlight("") } },
        )
        AccentColor.entries.forEach { color ->
            val hex = "#%06X".format(color.roles(isDark).primary and 0xFFFFFF)
            SettingRow(
                title = stringResource(color.labelRes),
                checked = current.equals(hex, ignoreCase = true),
                onCheckedChange = { vm.edit { setFocusHighlight(hex) } },
                modifier = Modifier,
            )
        }
        // Any colour at all, for the ring that is not one of the eight above.
        SettingRow(
            title = stringResource(R.string.settings_color_picker),
            value = current.ifBlank { stringResource(R.string.settings_subtitle_default) },
            onClick = onOpenPicker,
        )
    }
}

/** Which colour the picker is being opened for. */
private enum class ColorTarget { ACCENT, HIGHLIGHT }

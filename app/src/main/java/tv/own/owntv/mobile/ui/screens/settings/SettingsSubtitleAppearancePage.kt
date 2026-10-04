package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.settings.SubtitleStyle
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.parseAccentHex
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ColorPickerSheet
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.screens.settings.customize.selectedTick
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** Which appearance picker is open. One at a time, so one nullable holds them all. */
private enum class SubtitleSheet { SIZE, FONT, COLOR, POSITION, BACKGROUND }

/**
 * How subtitles are drawn — size, colour, where they sit and how solid their backing is.
 *
 * Its own screen rather than four rows buried in the player settings, the way the television has it:
 * these are the settings a user comes back to after seeing a subtitle they could not read, and they
 * should be one place, not a scroll.
 */
@Composable
fun SettingsSubtitleAppearancePage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val s = vm.settings
    var sheet by remember { mutableStateOf<SubtitleSheet?>(null) }

    val enabled = s.subtitleStyleEnabled.pref(false)
    val scale = s.subtitleScaleExo.pref(SubtitleStyle.SCALE_DEFAULT)
    val font = s.subtitleFont.pref(null)
    val color = s.subtitleColor.pref(SubtitleStyle.COLOR_DEFAULT)
    val position = s.subtitlePosition.pref(SubtitleStyle.Position.DEFAULT)
    val background = s.subtitleBgOpacity.pref(SubtitleStyle.OPACITY_DEFAULT)

    SettingsPage(modifier) {
        settingsGroup(key = "sub-style") {
            SettingRow(
                title = stringResource(R.string.settings_subtitle_appearance),
                subtitle = stringResource(R.string.settings_subtitle_appearance_description),
                checked = enabled,
                onCheckedChange = { on -> vm.edit { setSubtitleStyleEnabled(on) } },
            )
        }
        if (enabled) {
            settingsGroup(key = "sub-size") {
                SettingRow(
                    title = stringResource(R.string.settings_subtitle_size),
                    subtitle = stringResource(R.string.settings_subtitle_size_description),
                    value = stringResource(subSizeLabelRes(scale)),
                    onClick = { sheet = SubtitleSheet.SIZE },
                )

                // The face a subtitle is drawn in, both by the engine and by this app's own subtitle
                // layer. The row was missing while all six font files were already in the APK, so a
                // choice made on the television and synced here had nothing to act on.
                SettingRow(
                    title = stringResource(R.string.settings_subtitle_font),
                    subtitle = stringResource(R.string.settings_choose_font),
                    value = font?.let { stringResource(it.labelRes()) }
                        ?: stringResource(R.string.settings_subtitle_default),
                    onClick = { sheet = SubtitleSheet.FONT },
                )

                SettingRow(
                    title = stringResource(R.string.settings_subtitle_color_short),
                    subtitle = stringResource(R.string.settings_subtitle_color_description),
                    value = subColorLabel(color),
                    onClick = { sheet = SubtitleSheet.COLOR },
                )

                SettingRow(
                    title = stringResource(R.string.settings_subtitle_position_short),
                    subtitle = stringResource(R.string.settings_subtitle_position_description),
                    value = stringResource(position.labelRes()),
                    onClick = { sheet = SubtitleSheet.POSITION },
                )

                SettingRow(
                    title = stringResource(R.string.settings_subtitle_background_transparency),
                    subtitle = stringResource(R.string.settings_subtitle_background_description),
                    value = subBackgroundLabel(background),
                    onClick = { sheet = SubtitleSheet.BACKGROUND },
                )
            }
            // Puts all four back to Default in one press, so a look that went wrong does not have to
            // be undone option by option.
            settingsGroup(key = "sub-reset") {
                SettingRow(
                    title = stringResource(R.string.settings_subtitle_reset_all),
                    subtitle = stringResource(R.string.settings_subtitle_use_default),
                    onClick = {
                        vm.edit {
                            setSubtitleScaleExo(SubtitleStyle.SCALE_DEFAULT)
                            setSubtitleScaleMpv(SubtitleStyle.SCALE_DEFAULT)
                            setSubtitleFont(null)
                            setSubtitleColor(SubtitleStyle.COLOR_DEFAULT)
                            setSubtitlePosition(SubtitleStyle.Position.DEFAULT)
                            setSubtitleBgOpacity(SubtitleStyle.OPACITY_DEFAULT)
                        }
                    },
                )
            }
        }
    }

    // Opened from the page, not from inside the colour sheet, so the two never stack.
    var picker by remember { mutableStateOf(false) }

    val dismiss = { sheet = null }
    when (sheet) {
        // One size for both engines: a phone has one screen, and the TV app's split exists because
        // ExoPlayer and mpv measure text differently on a ten-foot one, not because a user wants
        // subtitles that change size when the stream falls back to the other engine.
        SubtitleSheet.SIZE -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_subtitle_size),
            choices = SUB_SIZES.map { (value, labelRes) ->
                SettingsChoice(value, stringResource(labelRes))
            },
            selected = nearestSubSize(scale),
            onSelect = { picked -> vm.edit { setSubtitleScaleExo(picked); setSubtitleScaleMpv(picked) } },
            onDismiss = dismiss,
        )
        // Default first, then the six families. Default means "whatever the stream or the engine
        // would have used", which is not the same as picking the system sans face by hand.
        SubtitleSheet.FONT -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_subtitle_font),
            choices = listOf(
                SettingsChoice<AppFontFamily?>(null, stringResource(R.string.settings_subtitle_default)),
            ) + AppFontFamily.entries.map {
                SettingsChoice<AppFontFamily?>(it, stringResource(it.labelRes()))
            },
            selected = font,
            onSelect = { picked -> vm.edit { setSubtitleFont(picked) } },
            onDismiss = dismiss,
        )
        SubtitleSheet.COLOR -> SubtitleColorSheet(
            color = color,
            onColor = { hex -> vm.edit { setSubtitleColor(hex) } },
            onOpenPicker = { sheet = null; picker = true },
            onDismiss = dismiss,
        )
        SubtitleSheet.POSITION -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_subtitle_position_short),
            choices = (listOf(SubtitleStyle.Position.DEFAULT) + SubtitleStyle.Position.ANCHORS).map {
                SettingsChoice(
                    it,
                    stringResource(it.labelRes()),
                    if (it == SubtitleStyle.Position.DEFAULT) {
                        stringResource(R.string.settings_subtitle_position_default_description)
                    } else {
                        null
                    },
                )
            },
            selected = position,
            onSelect = { picked -> vm.edit { setSubtitlePosition(picked) } },
            onDismiss = dismiss,
        )
        SubtitleSheet.BACKGROUND -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_subtitle_background_transparency),
            choices = SUB_BACKGROUND_CHOICES.map { SettingsChoice(it, subBackgroundLabel(it)) },
            selected = background,
            onSelect = { pct -> vm.edit { setSubtitleBgOpacity(pct) } },
            onDismiss = dismiss,
        )
        null -> Unit
    }

    if (picker) {
        ColorPickerSheet(
            title = stringResource(R.string.settings_subtitle_color),
            presets = SUB_COLOR_PRESETS.map { (_, hex) -> hex },
            initial = color,
            onPick = { hex -> vm.edit { setSubtitleColor(hex) } },
            onDismiss = { picker = false },
        )
    }
}

/**
 * The text colour: the presets as a list, then the colour picker, then six hex digits.
 *
 * Three ways in, because a preset list cannot express "the exact grey my other player uses" and
 * typing a code is a poor way to find a colour you have not chosen yet. Nothing is written until the
 * entry parses, and a bad one says so in place.
 */
@Composable
private fun SubtitleColorSheet(
    color: String,
    onColor: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    var hexInput by remember { mutableStateOf(color.removePrefix("#")) }
    var hexError by remember { mutableStateOf(false) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_subtitle_color),
    ) {
        MobileListRow(
            title = stringResource(R.string.settings_subtitle_default),
            subtitle = stringResource(R.string.settings_subtitle_color_default_description),
            trailing = selectedTick(!SubtitleStyle.hasColor(color)),
            selected = !SubtitleStyle.hasColor(color),
            onClick = {
                hexInput = ""
                hexError = false
                onColor(SubtitleStyle.COLOR_DEFAULT)
            },
        )
        SUB_COLOR_PRESETS.forEach { (labelRes, hex) ->
            MobileListRow(
                title = stringResource(labelRes),
                trailing = selectedTick(color.equals(hex, ignoreCase = true)),
                selected = color.equals(hex, ignoreCase = true),
                onClick = {
                    hexInput = hex.removePrefix("#")
                    hexError = false
                    onColor(hex)
                },
            )
        }
        MobileListRow(
            title = stringResource(R.string.settings_color_picker),
            onClick = onOpenPicker,
        )
        MobileTextField(
            value = hexInput,
            onValueChange = { typed -> hexInput = typed.take(6); hexError = false },
            label = stringResource(R.string.settings_subtitle_hex),
            isError = hexError,
            supportingText = if (hexError) {
                stringResource(R.string.settings_subtitle_color_hex_hint)
            } else {
                null
            },
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        MobileButton(
            text = stringResource(R.string.settings_apply),
            onClick = {
                val hex = "#" + hexInput.trim().removePrefix("#").uppercase()
                if (parseAccentHex(hex) != null) {
                    hexInput = hex.removePrefix("#")
                    hexError = false
                    onColor(hex)
                } else {
                    hexError = true
                }
            },
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
    }
}

private val SUB_SIZES = listOf(
    0.8f to R.string.settings_subtitle_small,
    1.0f to R.string.settings_subtitle_normal,
    1.3f to R.string.settings_subtitle_large,
    1.6f to R.string.settings_subtitle_extra_large,
)

private fun nearestSubSize(scale: Float): Float =
    SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) }?.first ?: SubtitleStyle.SCALE_DEFAULT

private fun subSizeLabelRes(scale: Float): Int =
    SUB_SIZES.minByOrNull { kotlin.math.abs(it.first - scale) }?.second
        ?: R.string.settings_subtitle_normal

/** The text-colour presets, as "#RRGGBB" — the quick answers, with the picker and a hex field behind
 *  them for the colour a list of five cannot express. */
private val SUB_COLOR_PRESETS = listOf(
    R.string.settings_subtitle_color_white to "#FFFFFF",
    R.string.settings_subtitle_color_yellow to "#FFEB3B",
    R.string.settings_subtitle_color_cyan to "#4FC3F7",
    R.string.settings_subtitle_color_green to "#8BC34A",
    R.string.settings_subtitle_color_grey to "#BDBDBD",
)

@Composable
private fun subColorLabel(hex: String): String = if (SubtitleStyle.hasColor(hex)) {
    SUB_COLOR_PRESETS.firstOrNull { it.second.equals(hex, ignoreCase = true) }
        ?.let { stringResource(it.first) }
        ?: hex.uppercase()
} else {
    stringResource(R.string.settings_subtitle_default)
}

private val SUB_BACKGROUND_CHOICES = listOf(SubtitleStyle.OPACITY_DEFAULT, 0, 30, 50, 70, 100)

@Composable
private fun subBackgroundLabel(pct: Int): String = when {
    !SubtitleStyle.hasOpacity(pct) -> stringResource(R.string.settings_subtitle_default)
    pct == SubtitleStyle.OPACITY_MIN -> stringResource(R.string.settings_subtitle_background_none)
    pct == SubtitleStyle.OPACITY_MAX -> stringResource(R.string.settings_subtitle_background_solid)
    else -> stringResource(R.string.common_percent, pct)
}

private fun SubtitleStyle.Position.labelRes() = when (this) {
    SubtitleStyle.Position.DEFAULT -> R.string.settings_subtitle_default
    SubtitleStyle.Position.TOP_LEFT -> R.string.player_mini_top_left
    SubtitleStyle.Position.TOP_CENTER -> R.string.player_mini_top_center
    SubtitleStyle.Position.TOP_RIGHT -> R.string.player_mini_top_right
    SubtitleStyle.Position.BOTTOM_LEFT -> R.string.player_mini_bottom_left
    SubtitleStyle.Position.BOTTOM_CENTER -> R.string.player_mini_bottom_center
    SubtitleStyle.Position.BOTTOM_RIGHT -> R.string.player_mini_bottom_right
}

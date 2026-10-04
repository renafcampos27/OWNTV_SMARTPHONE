package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.theme.AppFontFamily
import tv.own.owntv.core.theme.FontCustomization
import tv.own.owntv.core.theme.PopupFontScale
import tv.own.owntv.core.theme.PopupSizeScale
import tv.own.owntv.core.theme.UiFontScale
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The typefaces and the two sizes: the app's own text, and the text inside popups, which is set
 * separately because a sheet is read at a different distance from the same screen.
 *
 * The popup typeface is here for the first time on this app — on the previous page there was nowhere
 * to put it, so the setting existed and could only be reached from the television.
 */
@Composable
fun SettingsFontsPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val fonts = vm.settings.fontCustomization.pref(FontCustomization())

    SettingsPage(modifier) {
        item(key = "preview") {
            Text(
                text = stringResource(R.string.settings_font_preview),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = MobileDimens.ScreenPaddingH,
                        vertical = MobileDimens.GapMedium,
                    ),
            )
        }
        settingsGroup(key = "sizes") {
            SettingsSlider(
                title = stringResource(R.string.settings_font_size),
                value = fonts.sizePercent,
                range = UiFontScale.MIN..UiFontScale.MAX,
                steps = stepsFor(UiFontScale.MIN, UiFontScale.MAX, UiFontScale.STEP),
                onValueChange = { pct ->
                    vm.edit { setFontCustomization(fonts.copy(sizePercent = UiFontScale.clamp(pct))) }
                },
            )
            SettingsSlider(
                title = stringResource(R.string.settings_popup_font_size),
                subtitle = stringResource(R.string.settings_popup_font_size_description),
                value = fonts.popupFontSizePercent,
                range = PopupFontScale.MIN..PopupFontScale.MAX,
                steps = stepsFor(PopupFontScale.MIN, PopupFontScale.MAX, PopupFontScale.STEP),
                onValueChange = { pct ->
                    vm.edit {
                        setFontCustomization(
                            fonts.copy(popupFontSizePercent = PopupFontScale.clamp(pct)),
                        )
                    }
                },
            )
            SettingsSlider(
                title = stringResource(R.string.settings_popup_size),
                subtitle = stringResource(R.string.settings_popup_size_description),
                value = fonts.popupSizePercent,
                range = PopupSizeScale.MIN..PopupSizeScale.MAX,
                steps = stepsFor(PopupSizeScale.MIN, PopupSizeScale.MAX, PopupSizeScale.STEP),
                onValueChange = { pct ->
                    vm.edit {
                        setFontCustomization(
                            fonts.copy(popupSizePercent = PopupSizeScale.clamp(pct)),
                        )
                    }
                },
            )
        }

        settingsSection(R.string.settings_main_interface_font) {
            AppFontFamily.entries.forEach { family ->
                SettingRow(
                    title = stringResource(family.labelRes()),
                    checked = fonts.mainFamily == family,
                    onCheckedChange = {
                        vm.edit { setFontCustomization(fonts.copy(mainFamily = family)) }
                    },
                )
            }
        }

        settingsSection(R.string.settings_popup_font) {
            AppFontFamily.entries.forEach { family ->
                SettingRow(
                    title = stringResource(family.labelRes()),
                    checked = fonts.popupFamily == family,
                    onCheckedChange = {
                        vm.edit { setFontCustomization(fonts.copy(popupFamily = family)) }
                    },
                )
            }
        }
    }
}

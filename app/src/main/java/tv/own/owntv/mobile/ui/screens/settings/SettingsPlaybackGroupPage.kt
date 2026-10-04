package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** The two "one of these" questions in the Mobile section. */
private enum class MobilePlaybackSheet { MINI_STYLE, PIP_SIZE }

/**
 * Two rows into the player's own screens, then the settings a television has no use for.
 *
 * Everything the TV app has lives behind Video player and Subtitle appearance, keyed exactly as it is
 * there — external player included, which sits in the Video player's Engine section the way it does
 * on the television. What is below the "Mobile" heading has no television equivalent at all: a screen
 * that gets locked, a window that floats over another app, a data plan that gets billed, and a finger
 * that is less precise than a remote.
 */
@Composable
fun SettingsPlaybackPage(
    onOpenLeaf: (SettingsLeaf) -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val s = vm.settings
    val gesture = s.gestureSensitivityPct.pref(100)
    // A value stored before "off" was withdrawn reads as docked, so the row never names a choice the
    // sheet below does not offer.
    val miniStyle = s.miniPlayerStyle.pref(SettingsRepository.MiniPlayerStyle.FLOATING)
        .takeIf { it != SettingsRepository.MiniPlayerStyle.OFF }
        ?: SettingsRepository.MiniPlayerStyle.DOCKED

    var sheet by remember { mutableStateOf<MobilePlaybackSheet?>(null) }

    SettingsPage(modifier) {
        settingsLeafRows(SettingsGroup.PLAYBACK, onOpenLeaf)

        settingsSection(R.string.settings_playback_mobile) {
            QuickSwitchRow(
                vm = vm,
                toggle = quickToggle("background_playback"),
                subtitle = stringResource(R.string.settings_background_playback_description),
            )

            SettingRow(
                title = stringResource(R.string.settings_mini_player_style),
                subtitle = stringResource(R.string.settings_mini_player_style_description),
                value = stringResource(miniStyle.labelRes()),
                onClick = { sheet = MobilePlaybackSheet.MINI_STYLE },
            )



            // No "Picture-in-Picture on Back" row: the floating window is entered from the button in
            // the player's tools and from nowhere else, so there is nothing left for the switch to
            // decide. Core's stored preference is left alone rather than migrated away: it costs one
            // unread key, and removing a settings key is a core change made for no user's benefit.





            QuickSwitchRow(
                vm = vm,
                toggle = quickToggle("audio_on_screen_off"),
                subtitle = stringResource(R.string.settings_audio_on_screen_off_description),
            )

            QuickSwitchRow(
                vm = vm,
                toggle = quickToggle("audio_on_mobile_data"),
                subtitle = stringResource(R.string.settings_audio_on_mobile_data_description),
            )

            QuickSwitchRow(
                vm = vm,
                toggle = quickToggle("audio_per_channel"),
                subtitle = stringResource(R.string.settings_audio_per_channel_description),
            )

            QuickSwitchRow(
                vm = vm,
                toggle = quickToggle("data_saver"),
                subtitle = stringResource(R.string.settings_data_saver_description),
            )

            SettingsSlider(
                title = stringResource(R.string.settings_gesture_sensitivity),
                subtitle = stringResource(R.string.settings_gesture_sensitivity_description),
                value = gesture,
                range = 50..200,
                onValueChange = { pct -> vm.edit { setGestureSensitivityPct(pct) } },
            )
        }

        // Two of the television's playback settings are missing here, and this is where somebody would
        // come looking for them. A page that simply lacks a row reads as a bug; a page that says why
        // does not.
        item(key = "tv-only-note") {
            Text(
                text = stringResource(R.string.settings_playback_tv_only_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            )
        }
    }

    when (sheet) {
        MobilePlaybackSheet.MINI_STYLE -> SettingsChoiceSheet(
            title = stringResource(R.string.settings_mini_player_style),
            // Docked or floating, and no third answer: "off" would mean a stream leaving the full
            // screen with nowhere to go, which is either sound from an app showing no sign of playing
            // anything or a stream stopped by a button that did not say so. Back stops playback and
            // says so; this setting only chooses where the picture goes when it does not.
            choices = SettingsRepository.MiniPlayerStyle.entries
                .filter { it != SettingsRepository.MiniPlayerStyle.OFF }
                .map { SettingsChoice(it, stringResource(it.labelRes())) },
            selected = miniStyle,
            onSelect = { style -> vm.edit { setMiniPlayerStyle(style) } },
            onDismiss = { sheet = null },
        )
        MobilePlaybackSheet.PIP_SIZE -> Unit
        null -> Unit
    }
}

private fun SettingsRepository.MiniPlayerStyle.labelRes(): Int = when (this) {
    SettingsRepository.MiniPlayerStyle.FLOATING -> R.string.settings_mini_player_style_floating
    SettingsRepository.MiniPlayerStyle.DOCKED -> R.string.settings_mini_player_style_docked
    SettingsRepository.MiniPlayerStyle.OFF -> R.string.common_off
}

private fun SettingsRepository.PipSize.labelRes(): Int = when (this) {
    SettingsRepository.PipSize.SMALL -> R.string.settings_pip_size_small
    SettingsRepository.PipSize.MEDIUM -> R.string.settings_pip_size_medium
    SettingsRepository.PipSize.LARGE -> R.string.settings_pip_size_large
}

package tv.own.owntv.mobile.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.Flow
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileSwitch

/**
 * A switch that can be lifted to the top of the settings root.
 *
 * The pinned list is core's, shared with the TV app, and core keeps keys it does not recognise — so a
 * phone pinning "Background playback" does not disturb a television that has no such row, and the six
 * the TV app pins by default arrive here with the two remote-control ones simply not drawn.
 */
class QuickToggle(
    val key: String,
    @param:StringRes val titleRes: Int,
    val flow: (SettingsRepository) -> Flow<Boolean>,
    val set: suspend SettingsRepository.(Boolean) -> Unit,
    /** What the switch shows until the stored value arrives — core's own default for the setting. */
    val default: Boolean = false,
)

/** Every switch this app lets the user pin, by the key it is stored under. */
val QUICK_TOGGLES: List<QuickToggle> = listOf(
    QuickToggle(
        "quick_hdr",
        R.string.settings_quick_hdr,
        { it.hdrEnabled },
        { setHdrEnabled(it) },
        default = true,
    ),
    QuickToggle(
        "quick_autoplay",
        R.string.settings_quick_autoplay,
        { it.autoPlayNext },
        { setAutoPlayNext(it) },
        default = true,
    ),
    // The video player's own switches, under the keys the television pins them by, so a pinned list
    // restored from a television lands on the same rows here.
    QuickToggle(
        "vp_hw",
        R.string.settings_hardware_decoding,
        { it.hwDecoding },
        { setHwDecoding(it) },
        default = true,
    ),
    QuickToggle(
        "vp_hdr",
        R.string.settings_quick_hdr,
        { it.hdrEnabled },
        { setHdrEnabled(it) },
        default = true,
    ),
    QuickToggle(
        "vp_channel_numbers",
        R.string.settings_channel_numbers,
        { it.directTune },
        { setDirectTune(it) },
        default = true,
    ),
    QuickToggle(
        "vp_autoplay",
        R.string.settings_autoplay_next,
        { it.autoPlayNext },
        { setAutoPlayNext(it) },
        default = true,
    ),
    QuickToggle(
        "vp_measured_stats",
        R.string.settings_measured_stats,
        { it.measuredStreamStats },
        { setMeasuredStreamStats(it) },
        default = true,
    ),
    QuickToggle(
        "vp_logging",
        R.string.settings_detailed_playback_logging,
        { it.detailedDiagnostics },
        { setDetailedDiagnostics(it) },
    ),
    QuickToggle(
        "background_playback",
        R.string.settings_background_playback,
        { it.backgroundPlayback },
        { setBackgroundPlayback(it) },
    ),
    QuickToggle("pip_enabled", R.string.settings_pip, { it.pipEnabled }, { setPipEnabled(it) }),
    QuickToggle("data_saver", R.string.settings_data_saver, { it.dataSaver }, { setDataSaver(it) }),
    QuickToggle(
        "downloads_wifi_only",
        R.string.settings_downloads_wifi_only,
        { it.downloadsWifiOnly },
        { setDownloadsWifiOnly(it) },
    ),
    // The mobility layer. Same shape as the three above: touch-host only, and a television that
    // restores a pinned list containing them simply has no row to draw.
    QuickToggle("pip_snap", R.string.settings_pip_snap, { it.pipSnap }, { setPipSnap(it) }, default = true),
    QuickToggle(
        "audio_on_screen_off",
        R.string.settings_audio_on_screen_off,
        { it.audioOnScreenOff },
        { setAudioOnScreenOff(it) },
        default = true,
    ),
    QuickToggle(
        "audio_on_mobile_data",
        R.string.settings_audio_on_mobile_data,
        { it.audioOnMobileData },
        { setAudioOnMobileData(it) },
    ),
    QuickToggle(
        "audio_per_channel",
        R.string.settings_audio_per_channel,
        { it.audioPerChannel },
        { setAudioPerChannel(it) },
        default = true,
    ),
    QuickToggle(
        "detailed_diagnostics",
        R.string.settings_diagnostics,
        { it.detailedDiagnostics },
        { setDetailedDiagnostics(it) },
    ),
)

/**
 * A switch row backed directly by its persisted setting.
 *
 * Every pinnable setting is drawn by this one composable, on its own page and again in Quick, so the
 * two can never show different states — there is only one row.
 */
@Composable
fun QuickSwitchRow(
    vm: SettingsViewModel,
    toggle: QuickToggle,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val checked = toggle.flow(vm.settings).pref(toggle.default)
    MobileListRow(
        title = stringResource(toggle.titleRes),
        subtitle = subtitle,
        modifier = modifier,
        onClick = { vm.edit { toggle.set(this, !checked) } },
        trailing = {
            MobileSwitch(checked = checked)
        },
    )


}

/** The pinnable switch stored under [key]. Every key passed here is one of [QUICK_TOGGLES]. */
fun quickToggle(key: String): QuickToggle =
    QUICK_TOGGLES.first { it.key == key }

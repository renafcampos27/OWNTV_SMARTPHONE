package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.settings.GuideRetention
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Where the content comes from: the playlists, the guide feeds that fill the EPG, and the two clock
 * settings that decide whether a programme's times land where the provider meant them to.
 *
 * The two lists are pages of their own — a playlist row carries badges, live import counts and six
 * actions, and neither list fits above the other on a phone.
 */
@Composable
fun SettingsSourcesPage(
    onOpenLeaf: (SettingsLeaf) -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    var epgOffsetSheet by remember { mutableStateOf(false) }
    var guideDaysSheet by remember { mutableStateOf(false) }
    var catchupSheet by remember { mutableStateOf(false) }
    // The per-playlist catch-up zone: the playlist list, then the zone of the one picked.
    var catchupSourcesSheet by remember { mutableStateOf(false) }
    var catchupSource by remember { mutableStateOf<SourceEntity?>(null) }
    val sources by vm.sources.collectAsStateWithLifecycle()

    SettingsPage(modifier) {
        settingsLeafRows(SettingsGroup.SOURCES, onOpenLeaf)

        settingsSection(R.string.content_epg) {
            SettingRow(
                title = stringResource(R.string.content_epg_time_offset),
                subtitle = stringResource(R.string.settings_epg_offset_root_description),
                value = utcOffsetLabel(vm.settings.epgOffsetMinutes.pref(0)),
                onClick = { epgOffsetSheet = true },
            )

            // How far ahead the guide is stored: one horizon every feed is trimmed to, so it sits with
            // the other guide-wide setting rather than inside the feed list.
            val guideDays = vm.settings.guideDaysToKeep.pref(GuideRetention.DEFAULT_DAYS)
            SettingRow(
                title = stringResource(R.string.settings_epg_guide_days),
                value = pluralStringResource(R.plurals.settings_epg_guide_days_value, guideDays, guideDays),
                onClick = { guideDaysSheet = true },
            )

            val tz = vm.settings.catchupTimezone.pref(SettingsRepository.CatchupTimezone.DEVICE)
            SettingRow(
                title = stringResource(R.string.settings_catchup),
                subtitle = stringResource(R.string.settings_catchup_description),
                value = if (tz == SettingsRepository.CatchupTimezone.DEVICE) {
                    stringResource(R.string.settings_catchup_timezone_device)
                } else {
                    utcOffsetLabel(vm.settings.catchupOffsetMinutes.pref(0))
                },
                onClick = { catchupSheet = true },
            )
            if (sources.isNotEmpty()) {
                SettingRow(
                    title = stringResource(R.string.settings_catchup_timezone_per_playlist),
                    subtitle = stringResource(R.string.settings_catchup_timezone_per_playlist_description),
                    value = overrideCountLabel(sources.count { it.catchupTimezone != null }),
                    onClick = { catchupSourcesSheet = true },
                )
            }
        }
    }

    // Re-read from the live list so the second level shows the value just saved.
    val editing = sources.firstOrNull { it.id == catchupSource?.id }
    if (catchupSourcesSheet && editing == null) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_live_preroll_playlist_picker),
            choices = sources.map { src ->
                SettingsChoice<SourceEntity?>(value = src, label = src.name, description = sourceCatchupLabel(src))
            },
            selected = null,
            onSelect = { src -> catchupSource = src },
            // The sheet calls this after every pick too, so it only closes when no playlist was picked.
            onDismiss = { if (catchupSource == null) catchupSourcesSheet = false },
        )
    }
    if (catchupSourcesSheet && editing != null) {
        val manual = SettingsRepository.CatchupTimezone.MANUAL.name
        SettingsChoiceSheet(
            title = editing.name,
            choices = listOf(
                SettingsChoice<Pair<String?, Int?>>(null to null, stringResource(R.string.settings_live_preroll_follow)),
                SettingsChoice<Pair<String?, Int?>>(
                    SettingsRepository.CatchupTimezone.DEVICE.name to null,
                    stringResource(R.string.settings_catchup_timezone_device),
                ),
            ) + vm.settings.catchupOffsetChoicesMinutes.map {
                SettingsChoice<Pair<String?, Int?>>(manual to it, utcOffsetLabel(it))
            },
            selected = editing.catchupTimezone to editing.catchupOffsetMin.takeIf { editing.catchupTimezone == manual },
            // A pick closes the whole picker; Back (no pick) returns to the playlist list. Returning to
            // the list after a pick left that sheet frozen on the phone — stale value, Back ignored.
            onSelect = { (mode, offset) ->
                vm.setSourceCatchupTimezone(editing.id, mode, offset)
                catchupSourcesSheet = false
            },
            // Back goes back one level, to the playlist list, like the other per-playlist pickers.
            onDismiss = { catchupSource = null },
        )
    }

    if (guideDaysSheet) {
        // Preset days rather than a free number: a phone picks from a list far more comfortably than
        // it steps a counter, and the presets cover the whole useful range.
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_epg_guide_days),
            choices = GuideRetention.PRESET_DAYS.map {
                SettingsChoice(it, pluralStringResource(R.plurals.settings_epg_guide_days_value, it, it))
            },
            selected = vm.settings.guideDaysToKeep.pref(GuideRetention.DEFAULT_DAYS),
            onSelect = { days -> vm.edit { setGuideDaysToKeep(days) } },
            onDismiss = { guideDaysSheet = false },
        )
    }

    if (epgOffsetSheet) {
        val offset = vm.settings.epgOffsetMinutes.pref(0)
        MobileBottomSheet(
            onDismissRequest = { epgOffsetSheet = false },
            title = stringResource(R.string.content_epg_time_offset),
        ) {
            Text(
                text = stringResource(R.string.settings_epg_offset_dialog_description),
                modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            )
            OffsetStepper(
                minutes = offset,
                range = -12 * 60..14 * 60,
                step = 60, // the guide shift keeps whole hours; only catch-up went to quarter hours (N20)
                onChange = { vm.edit { setEpgOffsetMinutes(it) } },
            )
        }
    }

    if (catchupSheet) {
        val tz = vm.settings.catchupTimezone.pref(SettingsRepository.CatchupTimezone.DEVICE)
        val offset = vm.settings.catchupOffsetMinutes.pref(0)
        val player = vm.settings.catchupPlayer.pref(SettingsRepository.CatchupPlayer.INTERNAL)
        MobileBottomSheet(
            onDismissRequest = { catchupSheet = false },
            title = stringResource(R.string.settings_catchup),
        ) {
            SettingRow(
                title = stringResource(R.string.settings_catchup_timezone_device),
                checked = tz == SettingsRepository.CatchupTimezone.DEVICE,
                onCheckedChange = { device ->
                    vm.edit {
                        setCatchupTimezone(
                            if (device) SettingsRepository.CatchupTimezone.DEVICE
                            else SettingsRepository.CatchupTimezone.MANUAL,
                        )
                    }
                },
            )
            if (tz == SettingsRepository.CatchupTimezone.MANUAL) {
                OffsetStepper(
                    minutes = offset,
                    range = vm.settings.catchupOffsetRangeMinutes,
                    step = vm.settings.catchupOffsetStepMinutes,
                    onChange = { vm.edit { setCatchupOffsetMinutes(it) } },
                )
            }
            listOf(
                SettingsRepository.CatchupPlayer.ASK to R.string.settings_catchup_player_ask,
                SettingsRepository.CatchupPlayer.INTERNAL to R.string.settings_catchup_player_internal,
                SettingsRepository.CatchupPlayer.EXTERNAL to R.string.settings_catchup_player_external,
            ).forEach { (mode, label) ->
                SettingRow(
                    title = stringResource(label),
                    checked = player == mode,
                    onCheckedChange = { vm.edit { setCatchupPlayer(mode) } },
                )
            }
        }
    }
}

/** A playlist's own catch-up zone: Follow, Device, or its UTC offset. */
@Composable
private fun sourceCatchupLabel(source: SourceEntity): String = when (source.catchupTimezone) {
    null -> stringResource(R.string.settings_live_preroll_follow)
    SettingsRepository.CatchupTimezone.MANUAL.name -> utcOffsetLabel(source.catchupOffsetMin ?: 0)
    else -> stringResource(R.string.settings_catchup_timezone_device)
}

/** A whole hour at a time, the way the TV app's dialog steps it — providers publish hour offsets. */
@Composable
private fun OffsetStepper(minutes: Int, range: IntRange, step: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onChange((minutes - step).coerceIn(range)) },
            enabled = minutes > range.first,
        ) {
            Text(stringResource(R.string.settings_decrease))
        }
        Text(text = utcOffsetLabel(minutes), style = MaterialTheme.typography.titleMedium)
        TextButton(
            onClick = { onChange((minutes + step).coerceIn(range)) },
            enabled = minutes < range.last,
        ) {
            Text(stringResource(R.string.settings_increase))
        }
    }
}

/** "UTC+02:00" — a clock offset, not a translated phrase. */
internal fun utcOffsetLabel(minutes: Int): String {
    if (minutes == 0) return "UTC"
    val sign = if (minutes < 0) "-" else "+"
    val abs = kotlin.math.abs(minutes)
    return "UTC$sign%02d:%02d".format(java.util.Locale.ROOT, abs / 60, abs % 60)
}

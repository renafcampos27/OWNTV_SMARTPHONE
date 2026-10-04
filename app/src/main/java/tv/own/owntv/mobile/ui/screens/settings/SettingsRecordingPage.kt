package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.recording.RecordingSchedule
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.glassDialogWindow
import tv.own.owntv.mobile.ui.screens.recordings.RecordingsViewModel
import tv.own.owntv.mobile.ui.theme.MobileDimens

private enum class RollSheet { NONE, PRE, POST }

/**
 * What recording can be told, on a phone.
 *
 * Four rows where the television has three: a phone can be on mobile data, and a two-hour recording
 * over it is a bill. There is deliberately **no** "what to do when space runs low" — that behaviour
 * is fixed (stop with 500 MB free, keep what was captured, never delete anything), and a setting
 * would imply a choice that does not exist.
 */
@Composable
fun SettingsRecordingPage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
    recordingsVm: RecordingsViewModel = koinViewModel(),
) {
    val reserve = vm.settings.recordingReserveConnection.pref(true)
    val mobileData = vm.settings.recordingOverMobileData.pref(true)
    val preRoll = vm.settings.recordingPreRollMinutes.pref(RecordingSchedule.DEFAULT_PRE_ROLL_MINUTES)
    val postRoll = vm.settings.recordingPostRollMinutes.pref(RecordingSchedule.DEFAULT_POST_ROLL_MINUTES)
    val recordWatching = vm.settings.recordWhatImWatching.pref(false)

    val root = vm.settings.downloadRoot.pref("")
    val internalQuota = vm.settings.internalMediaQuotaGiB.pref(4)
    val externalQuota = vm.settings.externalMediaQuotaGiB.pref(16)
    var destinationSheet by remember { mutableStateOf(false) }
    val destinations by produceState(emptyList<tv.own.owntv.core.storage.StorageAccess.StorageRoot>()) { value = vm.downloadVolumes() }
    var sheet by remember { mutableStateOf(RollSheet.NONE) }
    var warnAboutWatching by remember { mutableStateOf(false) }

    Column(modifier) {
        // Said here as well as on the Recordings screen: this is where a user comes looking for a
        // switch that would fix it, and there isn't one — the app cannot grant itself the permission.
        if (!recordingsVm.timersAreExact) {
            Text(
                text = stringResource(R.string.settings_recording_timers_inexact),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            )
        }
        Text(stringResource(R.string.own_storage_hint), modifier = Modifier.padding(MobileDimens.GapSmall))
        SettingRow(title = stringResource(R.string.own_select_folder), value = if (root.isBlank()) stringResource(R.string.own_internal) else root.substringBefore("/Android/").substringAfterLast('/'), onClick = { destinationSheet = true })
        SettingsSlider(title = stringResource(R.string.own_internal_quota), value = internalQuota, range = 1..32, valueLabel = stringResource(R.string.own_gib_value, internalQuota), onValueChange = { vm.edit { setInternalMediaQuotaGiB(it) } })
        SettingsSlider(title = stringResource(R.string.own_external_quota), value = externalQuota, range = 1..256, valueLabel = stringResource(R.string.own_gib_value, externalQuota), onValueChange = { vm.edit { setExternalMediaQuotaGiB(it) } })
        SettingRow(
            title = stringResource(R.string.settings_recording_reserve),
            subtitle = stringResource(R.string.settings_recording_reserve_description),
            checked = reserve,
            onCheckedChange = { on -> vm.edit { setRecordingReserveConnection(on) } },
        )
        SettingRow(
            title = stringResource(R.string.settings_recording_mobile_data),
            subtitle = stringResource(R.string.settings_recording_mobile_data_description),
            checked = mobileData,
            onCheckedChange = { on -> vm.edit { setRecordingOverMobileData(on) } },
        )
        // D3 — the player's record button does not exist until this is on, and turning it on is
        // where the one-connection trade-off is explained and accepted. Turning it off needs no
        // dialog: nothing is being given up.
        SettingRow(
            title = stringResource(R.string.settings_record_watching),
            subtitle = stringResource(R.string.settings_record_watching_description),
            checked = recordWatching,
            onCheckedChange = { on ->
                if (on) warnAboutWatching = true else vm.edit { setRecordWhatImWatching(false) }
            },
        )
        SettingRow(
            title = stringResource(R.string.settings_recording_pre_roll),
            subtitle = stringResource(R.string.settings_recording_pre_roll_description),
            value = pluralStringResource(R.plurals.recording_minutes, preRoll, preRoll),
            onClick = { sheet = RollSheet.PRE },
        )
        SettingRow(
            title = stringResource(R.string.settings_recording_post_roll),
            subtitle = stringResource(R.string.settings_recording_post_roll_description),
            value = pluralStringResource(R.plurals.recording_minutes, postRoll, postRoll),
            onClick = { sheet = RollSheet.POST },
        )
    }

    if (destinationSheet) SettingsChoiceSheet(title = stringResource(R.string.own_select_folder), choices = listOf(SettingsChoice("", stringResource(R.string.own_internal))) + destinations.filter { it.kind == tv.own.owntv.core.storage.StorageAccess.RootKind.REMOVABLE }.map { SettingsChoice(it.file.absolutePath, it.volumeName ?: it.file.absolutePath) }, selected = root, onSelect = { vm.edit { setDownloadRoot(it) } }, onDismiss = { destinationSheet = false })

    // The trade-off, said before it is switched on (D3). Same shape as every other confirmation on
    // this app: the safer answer is the dismiss button.
    if (warnAboutWatching) {
        AlertDialog(
            modifier = Modifier.glassDialogWindow(),
            onDismissRequest = { warnAboutWatching = false },
            title = { Text(stringResource(R.string.settings_record_watching_warning_title)) },
            text = { Text(stringResource(R.string.settings_record_watching_warning_description)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.edit { setRecordWhatImWatching(true) }
                    warnAboutWatching = false
                }) {
                    Text(stringResource(R.string.settings_record_watching_turn_on))
                }
            },
            dismissButton = {
                TextButton(onClick = { warnAboutWatching = false }) {
                    Text(stringResource(R.string.settings_record_watching_keep_off))
                }
            },
        )
    }

    if (sheet != RollSheet.NONE) {
        val pre = sheet == RollSheet.PRE
        SettingsChoiceSheet(
            title = stringResource(
                if (pre) R.string.settings_recording_pre_roll else R.string.settings_recording_post_roll,
            ),
            description = stringResource(
                if (pre) {
                    R.string.settings_recording_pre_roll_description
                } else {
                    R.string.settings_recording_post_roll_description
                },
            ),
            // A short list of sensible paddings rather than a free number: on a phone this is a
            // two-tap choice, and nobody needs 23 minutes of pre-roll.
            choices = ROLL_CHOICES.map { minutes ->
                SettingsChoice(
                    value = minutes,
                    label = pluralStringResource(R.plurals.recording_minutes, minutes, minutes),
                )
            },
            selected = if (pre) preRoll else postRoll,
            onSelect = { minutes ->
                vm.edit {
                    if (pre) setRecordingPreRollMinutes(minutes) else setRecordingPostRollMinutes(minutes)
                }
            },
            onDismiss = { sheet = RollSheet.NONE },
        )
    }
}

/** The paddings worth offering, all inside core's own 0..30 clamp. */
private val ROLL_CHOICES = listOf(0, 1, 2, 3, 5, 10, 15, 30)

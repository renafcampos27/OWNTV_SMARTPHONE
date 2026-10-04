package tv.own.owntv.mobile.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.profiles.ProfilePinSheet
import tv.own.owntv.mobile.ui.setup.copyPickedFile
import tv.own.owntv.mobile.ui.setup.rememberBackupFilePicker
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** What the picker offers to create. A `.own` is a ZIP, and no MIME type of its own exists for it. */
private const val BACKUP_MIME = "application/octet-stream"
private const val BACKUP_FILE_NAME = "owntv-backup.own"

/**
 * Save everything to a file, or bring it back.
 *
 * Both ends go through the phone's document picker, so the backup lands wherever the user keeps
 * their files — the downloads folder, a cloud drive, an SD card — without the app ever asking for
 * access to storage it does not need. A restore merges: nothing already on the phone is deleted.
 *
 * Every choice on the way is a bottom sheet, like every other choice in this app. Nothing here is a
 * centred dialog: a dialog is its own window, so it cannot frost the wallpaper the rest of the app
 * frosts, and it sits exactly where the keyboard will cover it.
 */
@Composable
fun SettingsBackupPage(
    modifier: Modifier = Modifier,
    vm: BackupViewModel = koinViewModel(),
) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeProfileId.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var exportSheet by remember { mutableStateOf(false) }
    // Held while the document picker is in front: the user has already said what to export, and the
    // picker only decides where it goes.
    var chosen by remember { mutableStateOf<ExportRequest?>(null) }

    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BACKUP_MIME),
    ) { uri ->
        val request = chosen
        chosen = null
        if (uri != null && request != null) {
            vm.export(uri, BACKUP_FILE_NAME, request.sections, request.profileIds, request.password)
        }
    }
    val pickBackup = rememberBackupFilePicker { uri ->
        scope.launch {
            val file = copyPickedFile(context, uri, context.cacheDir)
            if (file != null) vm.beginRestore(file) else vm.reportReadFailure()
        }
    }

    SettingsPage(modifier) {
        settingsSection(R.string.settings_backup_title) {
            // A long export has no popup over it: the page says so and the rows go quiet, which
            // leaves the screen readable instead of blocking it behind a spinner.
            if (vm.busy) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MobileDimens.GapMedium),
                    verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                ) {
                    Text(
                        text = stringResource(R.string.settings_backup_working),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            SettingRow(
                title = stringResource(R.string.settings_backup_export_button),
                subtitle = stringResource(R.string.settings_backup_choose_folder),
                enabled = !vm.busy,
                onClick = { exportSheet = true },
            )
            SettingRow(
                title = stringResource(R.string.settings_backup_restore_button),
                subtitle = stringResource(R.string.settings_backup_pick_file),
                enabled = !vm.busy,
                onClick = { pickBackup() },
            )
        }
    }

    if (exportSheet) {
        ExportSheet(
            profiles = profiles,
            activeProfileId = activeId,
            verifyPin = vm::verifyPin,
            onExport = { request ->
                chosen = request
                exportSheet = false
                createDocument.launch(BACKUP_FILE_NAME)
            },
            onDismiss = { exportSheet = false },
        )
    }

    vm.pending?.let { pending ->
        when {
            // A sealed file tells nobody anything — not even what is in it — until it is opened.
            pending.inspection == null -> SealedPasswordSheet(
                wrong = pending.wrongPassword,
                onSubmit = vm::unseal,
                onDismiss = vm::cancelRestore,
            )
            else -> RestoreSheet(
                inspection = pending.inspection,
                wrongPassword = pending.wrongPassword,
                // A sealed file was already opened with its password; asking for it twice would be
                // asking the same question again.
                askPassword = pending.inspection.encrypted && pending.password == null,
                onRestore = vm::restore,
                onDismiss = vm::cancelRestore,
            )
        }
    }

    vm.outcome?.let { outcome -> OutcomeSheet(outcome, vm::dismissOutcome) }

    // A restore can bring a different icon colour than the launcher shows. Offer the restart once the
    // summary is closed, so the two dialogs never stack.
    val settings: tv.own.owntv.core.settings.SettingsRepository = org.koin.compose.koinInject()
    val chosenIcon = settings.appIcon.pref(null)
    val appliedIcon = tv.own.owntv.mobile.ui.components.rememberAppliedIcon()
    var restoredPending by remember { mutableStateOf(false) }
    LaunchedEffect(vm.outcome) { if (vm.outcome is BackupViewModel.Outcome.Restored) restoredPending = true }
    if (restoredPending && vm.outcome == null) {
        if (chosenIcon != null && chosenIcon != appliedIcon) {
            tv.own.owntv.mobile.ui.components.AppIconRestartDialog(chosenIcon) { restoredPending = false }
        } else {
            LaunchedEffect(Unit) { restoredPending = false }
        }
    }
}

/** Everything the export needs, decided before the picker opens. */
private data class ExportRequest(
    val sections: Set<BackupManager.Section>,
    val profileIds: Set<Long>,
    val password: String?,
)

/**
 * Whose data, which parts of it, and whether the file is encrypted — all three in one sheet,
 * because they are one decision and a phone should not walk anybody through three screens for it.
 *
 * A locked profile that is not the one in use has to be unlocked before it can be ticked: its PIN is
 * what stops the person holding the phone from carrying its history off to another device.
 */
@Composable
private fun ExportSheet(
    profiles: List<ProfileEntity>,
    activeProfileId: Long,
    verifyPin: (ProfileEntity, String) -> Boolean,
    onExport: (ExportRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var profileIds by remember(profiles) { mutableStateOf(setOf(activeProfileId)) }
    var sections by remember { mutableStateOf(BackupManager.Section.entries.toSet()) }
    var password by remember { mutableStateOf("") }
    var pinFor by remember { mutableStateOf<ProfileEntity?>(null) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_backup_export_title),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            Note(stringResource(R.string.settings_backup_selected_profiles))
            Label(stringResource(R.string.settings_backup_which_profiles))
            profiles.forEach { profile ->
                val locked = profile.pinHash != null && profile.id != activeProfileId
                CheckRow(
                    label = if (profile.id == activeProfileId) {
                        stringResource(R.string.settings_backup_profile_current, profile.name)
                    } else {
                        profile.name
                    },
                    description = if (locked) stringResource(R.string.settings_backup_pin_locked) else null,
                    checked = profile.id in profileIds,
                    onToggle = { on ->
                        when {
                            !on -> profileIds = profileIds - profile.id
                            locked -> pinFor = profile
                            else -> profileIds = profileIds + profile.id
                        }
                    },
                )
            }
            Label(stringResource(R.string.settings_backup_what_backup))
            BackupManager.Section.entries.forEach { section ->
                CheckRow(
                    label = stringResource(section.labelRes()),
                    description = stringResource(section.descriptionRes()),
                    checked = section in sections,
                    onToggle = { on -> sections = if (on) sections + section else sections - section },
                )
            }
            Label(stringResource(R.string.settings_backup_encrypt_title))
            Note(stringResource(R.string.settings_backup_encrypt_message))
            MobileTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.settings_backup_password),
                isPassword = true,
                imeAction = ImeAction.Done,
            )
        }
        SheetButtons(
            confirm = stringResource(
                if (password.isBlank()) {
                    R.string.settings_backup_export_unencrypted
                } else {
                    R.string.settings_backup_encrypt_export
                },
            ),
            confirmEnabled = profileIds.isNotEmpty() && sections.isNotEmpty(),
            onConfirm = { onExport(ExportRequest(sections, profileIds, password.takeIf { it.isNotBlank() })) },
            onDismiss = onDismiss,
        )
    }

    pinFor?.let { locked ->
        ProfilePinSheet(
            profileName = locked.name,
            onSubmit = { pin ->
                verifyPin(locked, pin).also {
                    if (it) {
                        profileIds = profileIds + locked.id
                        pinFor = null
                    }
                }
            },
            onDismiss = { pinFor = null },
        )
    }
}

/** The password of a sealed file, which cannot be skipped: without it there is nothing to read. */
@Composable
private fun SealedPasswordSheet(
    wrong: Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_backup_enter_password),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            Note(stringResource(R.string.settings_backup_encrypted_description))
            MobileTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.settings_backup_password),
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeDone = { if (password.isNotBlank()) onSubmit(password) },
                isError = wrong,
                supportingText = if (wrong) {
                    stringResource(R.string.settings_backup_password_encrypted_mismatch)
                } else {
                    null
                },
            )
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_backup_unlock),
            confirmEnabled = password.isNotBlank(),
            // Kept open on purpose: a wrong password comes back to this same sheet with its message.
            onConfirm = { onSubmit(password) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * What to bring back, out of what the file actually holds.
 *
 * [askPassword] is an unsealed but encrypted file: the passwords inside it are locked, everything
 * else is not, so restoring without the password is offered rather than demanded.
 */
@Composable
private fun RestoreSheet(
    inspection: BackupManager.Inspection,
    wrongPassword: Boolean,
    askPassword: Boolean,
    onRestore: (Set<BackupManager.Section>, String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var sections by remember(inspection) { mutableStateOf(inspection.sections) }
    // The other device's hardware settings (engine, decoder, frame rate, HDR, surround): offered only
    // for a file written elsewhere, and unticked — a phone's values are rarely a television's.
    var deviceSettings by remember(inspection) { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_backup_restore_title),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            Label(stringResource(R.string.settings_backup_what_restore))
            inspection.sections.forEach { section ->
                CheckRow(
                    label = stringResource(section.labelRes()),
                    description = stringResource(section.descriptionRes()),
                    checked = section in sections,
                    onToggle = { on -> sections = if (on) sections + section else sections - section },
                )
            }
            if (inspection.fromOtherDevice && BackupManager.Section.SETTINGS in sections) {
                CheckRow(
                    label = stringResource(R.string.settings_backup_device_settings),
                    description = stringResource(R.string.settings_backup_device_settings_desc),
                    checked = deviceSettings,
                    onToggle = { deviceSettings = it },
                )
            }
            if (askPassword) {
                Note(stringResource(R.string.settings_backup_saved_passwords_description))
                MobileTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.settings_backup_password),
                    isPassword = true,
                    isError = wrongPassword,
                    supportingText = if (wrongPassword) {
                        stringResource(R.string.settings_backup_password_mismatch)
                    } else {
                        null
                    },
                )
            }
        }
        SheetButtons(
            confirm = stringResource(
                if (askPassword && password.isBlank()) {
                    R.string.settings_backup_skip_passwords
                } else {
                    R.string.settings_backup_restore_action
                },
            ),
            confirmEnabled = sections.isNotEmpty(),
            onConfirm = { onRestore(sections, password.takeIf { it.isNotBlank() }, deviceSettings) },
            onDismiss = onDismiss,
        )
    }
}

/** What happened, in the same words the television uses for it. */
@Composable
private fun OutcomeSheet(outcome: BackupViewModel.Outcome, onDismiss: () -> Unit) {
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_backup_title),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            when (outcome) {
                is BackupViewModel.Outcome.Exported -> Text(
                    stringResource(
                        if (outcome.passwordsOmitted) {
                            R.string.settings_backup_saved_to_without_passwords
                        } else {
                            R.string.settings_backup_saved_to
                        },
                        outcome.name,
                    ),
                )
                is BackupViewModel.Outcome.Restored -> {
                    Text(
                        pluralStringResource(
                            R.plurals.settings_backup_restored,
                            outcome.summary.items,
                            outcome.summary.items,
                        ),
                    )
                    if (outcome.summary.skippedSources > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.settings_backup_skipped_sources,
                                outcome.summary.skippedSources,
                                outcome.summary.skippedSources,
                            ),
                        )
                    }
                    if (outcome.summary.invalidLocale) {
                        Text(stringResource(R.string.settings_backup_invalid_locale))
                    }
                    Text(stringResource(R.string.settings_backup_restore_resync))
                    Text(stringResource(R.string.settings_backup_restore_password_note))
                }
                is BackupViewModel.Outcome.Failed -> Text(stringResource(outcome.messageRes))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall, Alignment.End),
        ) {
            MobileButton(text = stringResource(R.string.common_ok), onClick = onDismiss)
        }
    }
}

/** A tick, its label and the line that says what it covers — the whole row toggles it. */
@Composable
internal fun CheckRow(
    label: String,
    description: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Cancel and confirm, right-aligned under a sheet's body — the shape these sheets end in. */
@Composable
internal fun SheetButtons(
    confirm: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MobileButton(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss,
            style = MobileButtonStyle.TEXT,
        )
        MobileButton(text = confirm, onClick = onConfirm, enabled = confirmEnabled)
    }
}

@Composable
internal fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = MobileDimens.GapSmall),
    )
}

@Composable
internal fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun BackupManager.Section.labelRes() = when (this) {
    BackupManager.Section.SOURCES -> R.string.settings_backup_section_sources
    BackupManager.Section.CUSTOMIZE -> R.string.settings_backup_section_customize
    BackupManager.Section.FAVORITES -> R.string.settings_backup_section_favorites
    BackupManager.Section.HISTORY -> R.string.settings_backup_section_history
    BackupManager.Section.RESUME -> R.string.settings_backup_section_resume
    BackupManager.Section.MANUAL_REORDER -> R.string.settings_backup_section_reorder
    BackupManager.Section.SETTINGS -> R.string.settings_backup_section_settings
}

internal fun BackupManager.Section.descriptionRes() = when (this) {
    BackupManager.Section.SOURCES -> R.string.settings_backup_section_sources_desc
    BackupManager.Section.CUSTOMIZE -> R.string.settings_backup_section_customize_desc
    BackupManager.Section.FAVORITES -> R.string.settings_backup_section_favorites_desc
    BackupManager.Section.HISTORY -> R.string.settings_backup_section_history_desc
    BackupManager.Section.RESUME -> R.string.settings_backup_section_resume_desc
    BackupManager.Section.MANUAL_REORDER -> R.string.settings_backup_section_reorder_desc
    BackupManager.Section.SETTINGS -> R.string.settings_backup_section_settings_desc
}

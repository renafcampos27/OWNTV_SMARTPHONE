package tv.own.owntv.mobile.ui.setup

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.core.sync.detailText
import tv.own.owntv.core.sync.importProgressDisplay
import tv.own.owntv.core.sync.primaryText
import tv.own.owntv.core.sync.remainderText
import tv.own.owntv.core.sync.summaryText
import tv.own.owntv.core.sync.warningText
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.profiles.ProfileEditorSheet
import tv.own.owntv.mobile.ui.screens.settings.CheckRow
import tv.own.owntv.mobile.ui.screens.settings.Label
import tv.own.owntv.mobile.ui.screens.settings.SheetButtons
import tv.own.owntv.mobile.ui.screens.settings.descriptionRes
import tv.own.owntv.mobile.ui.screens.settings.labelRes
import tv.own.owntv.mobile.ui.screens.settings.SetupLocalSyncStep
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The television's own sequence, name for name. See `SetupWizardSteps.kt` for why the TV's two
 * "chooser" steps have no counterpart here.
 */
private enum class Step {
    WELCOME, DISPLAY_SIZE, DISCLAIMER, CHOICE, SYNC_DEVICE, CREATE_PROFILE, ADD_CONTENT, EXISTING, FORM, IMPORTING, RESTORE
}

/**
 * Getting content onto the phone: type a playlist in, or bring everything back from a backup.
 *
 * **The first run is the television's wizard**, in its order and with its words — Welcome, the
 * disclaimer, start-fresh-or-restore, the profile, then where the content comes from. Opening the
 * same flow later from "Add a playlist" starts at *Add content* instead: the greeting, the disclaimer
 * and the profile step all belong to a first run and would be in the way of the one thing that user
 * came to do.
 */
@Composable
fun SetupFlow(
    onDone: (profileId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
) {
    val vm: SetupViewModel = koinViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    // No way to cancel means there is nothing behind this flow, which is exactly what a first run is.
    val firstRun = onCancel == null
    var step by rememberSaveable(firstRun) {
        mutableStateOf(if (firstRun) Step.WELCOME else Step.ADD_CONTENT)
    }
    // Refreshed on every arrival at Add content, because restoring a backup on the way here can
    // create the very profiles whose playlists this step offers to share.
    var existing by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    LaunchedEffect(step) {
        if (step == Step.ADD_CONTENT) {
            existing = runCatching { vm.availableExistingSources() }.getOrDefault(emptyList())
        }
    }
    // Where "Try again" and "Cancel" return to: the form for a new playlist, the list for a shared one.
    var importOrigin by rememberSaveable { mutableStateOf(Step.FORM) }
    // Where Back from the backup picker returns to — the first-run choice, or Add content.
    var backupOrigin by rememberSaveable { mutableStateOf(Step.ADD_CONTENT) }

    // The picked file, and what the user chose to take out of it. Both `null` until each is
    // answered, which is what drives the sheet below: a file with no choice yet is the question.
    //
    // Setup used to restore everything, full stop — the tick-list existed in Settings → Backup &
    // Restore and in the local-sync step, and only the one screen where a restore is most likely
    // took the whole file without asking. "My playlists but not that device's settings" was not
    // expressible here.
    var restoreFile by remember { mutableStateOf<java.io.File?>(null) }
    var restoreSections by remember { mutableStateOf<Set<BackupManager.Section>?>(null) }
    // "Hardware settings from the other device" — answered with the sections, carried like them.
    var restoreDeviceSettings by remember { mutableStateOf(false) }

    // The name field is optional, and the television has always filled a blank one in rather than
    // storing an empty string. Without this the playlist has no name anywhere it is shown — the top
    // bar's selector renders as a bare pill, and the playlist picker offers a row with no label.
    val defaultProfileName = stringResource(R.string.setup_default_profile)
    val defaultIptvName = stringResource(R.string.setup_default_iptv)
    val defaultPlaylistName = stringResource(R.string.setup_name_default_playlist)
    val defaultPortalName = stringResource(R.string.setup_default_portal)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Picking the file no longer starts the restore — it asks what to take out of it first.
    val pickBackup = rememberBackupFilePicker { uri ->
        scope.launch {
            copyPickedFile(context, uri, context.cacheDir)?.let {
                restoreSections = null
                restoreFile = it
            }
        }
    }

    // Back walks the wizard backwards rather than out of the app. From the very first step there is
    // nowhere to go unless the caller gave us somewhere, and an install with no playlist has nothing
    // behind it at all.
    val atStart = step == (if (firstRun) Step.WELCOME else Step.ADD_CONTENT)
    BackHandler(enabled = !atStart || onCancel != null) {
        when (step) {
            Step.WELCOME -> onCancel?.invoke()
            Step.DISPLAY_SIZE -> step = Step.WELCOME
            Step.DISCLAIMER -> step = Step.DISPLAY_SIZE
            Step.CHOICE -> step = Step.DISCLAIMER
            // The step owns its own Back: it has sheets to dismiss first, and once the data has
            // landed there is nothing to go back to.
            Step.SYNC_DEVICE -> Unit
            Step.CREATE_PROFILE -> step = Step.CHOICE
            // Not `onCancel?.invoke()`: `firstRun` IS `onCancel == null`, so this branch is only
            // reached when there is one, and the compiler knows it.
            Step.ADD_CONTENT -> if (firstRun) step = Step.CREATE_PROFILE else onCancel()
            Step.EXISTING -> step = Step.ADD_CONTENT
            Step.FORM -> { vm.reset(); step = Step.ADD_CONTENT }
            Step.RESTORE -> { vm.reset(); step = backupOrigin }
            Step.IMPORTING -> Unit // the buttons on that screen decide; a stray swipe must not abandon a sync
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (step) {
            Step.WELCOME -> WelcomeStep(onNext = { step = Step.DISPLAY_SIZE })
            // Before the disclaimer, which is the first screen that is mostly words: if the text is
            // too small to read, that is the screen it first hurts on (#179).
            Step.DISPLAY_SIZE -> DisplaySizeStep(
                onNext = { step = Step.DISCLAIMER },
                onBack = { step = Step.WELCOME },
            )
            Step.DISCLAIMER -> DisclaimerStep(
                onAgree = { step = Step.CHOICE },
                onBack = { step = Step.DISPLAY_SIZE },
            )
            // The first decision: start fresh, or bring everything back. Restoring first is why the
            // profile step comes after this one — a restore brings its own profiles, and creating one
            // beforehand would only be something for the restore to sit beside.
            Step.CHOICE -> SetupChoice(
                onCreateProfile = { step = Step.CREATE_PROFILE },
                onRestore = { backupOrigin = Step.CHOICE; step = Step.RESTORE; pickBackup() },
                onSyncDevice = { step = Step.SYNC_DEVICE },
                onCancel = onCancel,
            )
            // A sync brings whole profiles with it, exactly as a restored backup does, so it finishes
            // the same way the RESTORE step does rather than inventing a second ending.
            Step.SYNC_DEVICE -> SetupLocalSyncStep(
                onRestored = { vm.finish(onDone) },
                onBack = { step = Step.CHOICE },
            )
            Step.CREATE_PROFILE -> ProfileEditorSheet(
                initial = null,
                takenNames = emptySet(),
                onConfirm = { name, avatar, kids, pin ->
                    vm.createProfile(name.ifBlank { defaultProfileName }, avatar, kids, pin) {
                        step = Step.ADD_CONTENT
                    }
                },
                onDismiss = { step = Step.CHOICE },
            )
            Step.ADD_CONTENT -> AddContentStep(
                hasExisting = existing.isNotEmpty(),
                onNew = { importOrigin = Step.FORM; step = Step.FORM },
                onExisting = { step = Step.EXISTING },
                onImport = { backupOrigin = Step.ADD_CONTENT; step = Step.RESTORE; pickBackup() },
                onSkip = { vm.finish(onDone) },
                onBack = if (firstRun) ({ step = Step.CREATE_PROFILE }) else onCancel,
            )
            Step.EXISTING -> ExistingSourcesStep(
                sources = existing,
                onAdd = { ids ->
                    vm.linkExisting(ids)
                    importOrigin = Step.EXISTING
                    step = Step.IMPORTING
                },
                onBack = { step = Step.ADD_CONTENT },
            )
            Step.FORM -> AddSourceForm(
                onStartXtream = { name, server, user, pass, ua, referer, refresh, live, movies, series, hls ->
                    vm.startXtream(
                        name.ifBlank { defaultIptvName },
                        server, user, pass, ua, referer, refresh, live, movies, series, hls,
                    )
                    step = Step.IMPORTING
                },
                onStartM3u = { name, url, ua, referer, refresh ->
                    vm.startM3u(name.ifBlank { defaultPlaylistName }, url, ua, referer, refresh)
                    step = Step.IMPORTING
                },
                onStartStalker = { name, portal, mac, serial, dev1, dev2, sig, ua, referer, refresh, live, movies, series ->
                    vm.startStalker(
                        name.ifBlank { defaultPortalName },
                        portal, mac, serial, dev1, dev2, sig, ua, referer, refresh, live, movies, series,
                    )
                    step = Step.IMPORTING
                },
                // Every other step gets its insets from [SetupPage]; the form has its own scrolling
                // column, and without this its first line sat under the status bar's clock.
                modifier = Modifier.statusBarsPadding(),
            )
            Step.IMPORTING -> ImportProgress(
                state = state,
                progressText = progress?.importProgressDisplay(),
                onContinue = { vm.finish(onDone) },
                onRunInBackground = { vm.continueInBackground(onDone) },
                onRetry = { vm.reset(); step = importOrigin },
                onCancel = { vm.cancelImport(); step = importOrigin },
            )
            Step.RESTORE -> {
                RestoreBackup(
                    state = state,
                    // The same choice the sheet took, carried across the password question: a sealed
                    // file is chosen from before it can be opened, so the answer has to outlive it.
                    onPassword = { file, password ->
                        vm.restoreWithPassword(file, password, restoreSections ?: allSections, restoreDeviceSettings)
                    },
                    onContinue = { vm.finish(onDone) },
                    onPickAgain = { vm.reset(); restoreFile = null; restoreSections = null; pickBackup() },
                    onBack = { vm.reset(); restoreFile = null; restoreSections = null; step = backupOrigin },
                )
                // A file is picked and nothing has been asked of it yet — so ask, over the top.
                val picked = restoreFile
                if (picked != null && restoreSections == null) {
                    RestoreSectionsSheet(
                        onConfirm = { sections, deviceSettings ->
                            restoreSections = sections
                            restoreDeviceSettings = deviceSettings
                            vm.importBackup(picked, sections, deviceSettings)
                        },
                        onDismiss = {
                            vm.reset()
                            restoreFile = null
                            step = backupOrigin
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupChoice(
    onCreateProfile: () -> Unit,
    onRestore: () -> Unit,
    onSyncDevice: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    SetupPage {
        Text(
            text = stringResource(R.string.setup_set_up_owntv),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.setup_setup_choice_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(MobileDimens.GapSmall))
        // The television's own label, and it has to be: this row leads to the profile editor, not to
        // a playlist form. Calling it "Add a playlist" described the step after next and left the
        // profile step looking like it had arrived by mistake.
        MobileListRow(
            title = stringResource(R.string.setup_new_profile),
            subtitle = stringResource(R.string.setup_create_profile_add_sources),
            leading = { Icon(MobileIcons.Person, contentDescription = null) },
            onClick = onCreateProfile,
        )
        MobileListRow(
            title = stringResource(R.string.setup_restore_backup),
            subtitle = stringResource(R.string.setup_import_profiles_playlists),
            leading = { Icon(MobileIcons.Restore, contentDescription = null) },
            onClick = onRestore,
        )
        if (onCancel != null) {
            MobileButton(
                text = stringResource(R.string.common_cancel),
                onClick = onCancel,
                style = MobileButtonStyle.TEXT,
            )
        }
    }
}

/** The import, from the first request to "All set!" or the reason it stopped. */
@Composable
private fun ImportProgress(
    state: SourceImporter.ImportState,
    progressText: tv.own.owntv.core.sync.SyncProgressDisplay?,
    onContinue: () -> Unit,
    onRunInBackground: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    val resources = LocalContext.current.resources
    SetupPage {
        when (state) {
            is SourceImporter.ImportState.Success -> {
                Text(
                    text = stringResource(R.string.setup_all_set),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                state.counts?.let { Detail(it.summaryText(resources, includeEpg = true)) }
                state.warnings.warningText(resources)?.let { Detail(it) }
                state.remainder.remainderText(resources)?.let { Detail(it) }
                MobileButton(
                    text = stringResource(R.string.setup_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is SourceImporter.ImportState.Failed -> {
                Text(
                    text = stringResource(R.string.setup_import_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Detail(state.failure.displayText(resources))
                Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                    MobileButton(
                        text = stringResource(R.string.common_back),
                        onClick = onCancel,
                        style = MobileButtonStyle.SECONDARY,
                    )
                    MobileButton(text = stringResource(R.string.setup_try_again_caps), onClick = onRetry)
                }
            }
            else -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.setup_importing_catalog),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = progressText?.primaryText(resources)
                        ?: stringResource(R.string.setup_preparing_catalog),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Detail(
                    progressText?.detailText(resources)
                        ?: stringResource(R.string.setup_preparing_catalog),
                )
                // A full catalog takes minutes, and nobody should have to watch a spinner for them.
                // Leaving is the first-class action here; Cancel is the quiet one, because it throws
                // the import away.
                Detail(stringResource(R.string.setup_watching_during_import))
                MobileButton(
                    text = stringResource(R.string.setup_run_in_background),
                    onClick = onRunInBackground,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = onCancel,
                    style = MobileButtonStyle.TEXT,
                )
            }
        }
    }
}

private val allSections: Set<BackupManager.Section> get() = BackupManager.Section.entries.toSet()

/**
 * What to take out of the backup file, before any of it is applied.
 *
 * Every section is offered rather than only the ones the file holds, which is what
 * Settings → Backup & Restore can do: that screen has already opened the container, and this one
 * has not — a sealed file says nothing about its contents until the password arrives, and asking
 * for the password before the user has said what they want would be the wrong order. Ticking a
 * section the file does not carry simply restores nothing for it.
 */
@Composable
private fun RestoreSectionsSheet(
    onConfirm: (Set<BackupManager.Section>, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var sections by remember { mutableStateOf(allSections) }
    // Offered whatever the file is — it has not been opened yet — and unticked. A backup of this very
    // phone gets its hardware settings back regardless; this is only about another device's.
    var deviceSettings by remember { mutableStateOf(false) }
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
            BackupManager.Section.entries.forEach { section ->
                CheckRow(
                    label = stringResource(section.labelRes()),
                    description = stringResource(section.descriptionRes()),
                    checked = section in sections,
                    onToggle = { on -> sections = if (on) sections + section else sections - section },
                )
            }
            if (BackupManager.Section.SETTINGS in sections) {
                CheckRow(
                    label = stringResource(R.string.settings_backup_device_settings),
                    description = stringResource(R.string.settings_backup_device_settings_desc),
                    checked = deviceSettings,
                    onToggle = { deviceSettings = it },
                )
            }
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_backup_restore_action),
            confirmEnabled = sections.isNotEmpty(),
            onConfirm = { onConfirm(sections, deviceSettings) },
            onDismiss = onDismiss,
        )
    }
}

/** The restore, once a file has been picked: its password if it needs one, then the result. */
@Composable
private fun RestoreBackup(
    state: SourceImporter.ImportState,
    onPassword: (java.io.File, String?) -> Unit,
    onContinue: () -> Unit,
    onPickAgain: () -> Unit,
    onBack: () -> Unit,
) {
    val resources = LocalContext.current.resources
    SetupPage {
        when (state) {
            is SourceImporter.ImportState.NeedPassword -> {
                var password by remember(state.file, state.retry) { mutableStateOf("") }
                Text(
                    text = stringResource(
                        if (state.retry) R.string.setup_wrong_backup_password else R.string.setup_enter_backup_password,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Detail(
                    stringResource(
                        when {
                            state.retry && state.sealed -> R.string.setup_password_mismatch_sealed
                            state.retry -> R.string.setup_password_mismatch
                            state.sealed -> R.string.setup_backup_encrypted_prompt
                            else -> R.string.setup_backup_passwords_encrypted_prompt
                        },
                    ),
                )
                MobileTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.setup_backup_password),
                    isPassword = true,
                    imeAction = ImeAction.Done,
                    onImeDone = { if (password.isNotBlank()) onPassword(state.file, password) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                    MobileButton(
                        text = stringResource(R.string.common_back),
                        onClick = onBack,
                        style = MobileButtonStyle.SECONDARY,
                    )
                    // A sealed container is nothing but ciphertext: skipping the password would
                    // restore an empty backup, so that way out is not offered.
                    if (!state.sealed) {
                        MobileButton(
                            text = stringResource(R.string.setup_skip_no_passwords),
                            onClick = { onPassword(state.file, null) },
                            style = MobileButtonStyle.SECONDARY,
                        )
                    }
                    MobileButton(
                        text = stringResource(R.string.setup_restore),
                        onClick = { onPassword(state.file, password) },
                        enabled = password.isNotBlank(),
                    )
                }
            }
            is SourceImporter.ImportState.Success -> {
                Text(
                    text = stringResource(R.string.setup_all_set),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                state.restoredItems?.let {
                    Detail(pluralStringResource(R.plurals.setup_restored_items, it, it))
                }
                if (state.passwordsOmitted) Detail(stringResource(R.string.setup_passwords_omitted))
                state.skippedSources.takeIf { it > 0 }?.let {
                    Detail(pluralStringResource(R.plurals.setup_skipped_sources, it, it))
                }
                if (state.invalidLocale) Detail(stringResource(R.string.setup_invalid_locale))
                MobileButton(
                    text = stringResource(R.string.setup_continue),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is SourceImporter.ImportState.Failed -> {
                Text(
                    text = stringResource(R.string.setup_restore_failed),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Detail(state.failure.displayText(resources))
                Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                    MobileButton(
                        text = stringResource(R.string.common_back),
                        onClick = onBack,
                        style = MobileButtonStyle.SECONDARY,
                    )
                    MobileButton(text = stringResource(R.string.setup_try_again_caps), onClick = onPickAgain)
                }
            }
            else -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.setup_restoring),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // The picker is a separate activity: cancelling it leaves this screen with nothing
                // happening, so there is always a way back and a way to pick another file.
                Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                    MobileButton(
                        text = stringResource(R.string.common_back),
                        onClick = onBack,
                        style = MobileButtonStyle.SECONDARY,
                    )
                    MobileButton(
                        text = stringResource(R.string.setup_pick_backup_file),
                        onClick = onPickAgain,
                        style = MobileButtonStyle.SECONDARY,
                    )
                }
            }
        }
    }
}

/** One column, centred, scrolling — every step of this flow is short enough to fit but must still
 *  survive a keyboard and a small screen in landscape. */
@Composable
fun SetupPage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // safeDrawing already holds status + navigation room; imePadding holds the keyboard.
            // Do not add navigationBarsPadding on top: it double-counts the gesture bar.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapLarge),
        verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}

@Composable
private fun Detail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

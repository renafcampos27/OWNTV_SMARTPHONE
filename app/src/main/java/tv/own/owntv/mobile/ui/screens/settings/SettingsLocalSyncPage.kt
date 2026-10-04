package tv.own.owntv.mobile.ui.screens.settings

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.sync.local.SyncDirection
import tv.own.owntv.core.sync.local.SyncFailure
import tv.own.owntv.core.sync.local.shortCodes
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.setup.SetupPage
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Sync this phone with the television over the home Wi-Fi — no account, no cloud, nothing leaving
 * the house.
 *
 * The page is a list of the devices already paired plus the two ways to add one: **let this phone be
 * found** (it shows a PIN and a QR code, and the television connects to it) or **find a device**
 * (discovery, a scanned QR, or a typed address). Either device can be either role, which is the
 * point — a sync you can only start from the television is not much use when you are holding a phone.
 *
 * Every direction is named. There is no bare "Sync" button whose meaning the user has to guess.
 */
@Composable
fun SettingsLocalSyncPage(
    modifier: Modifier = Modifier,
    vm: LocalSyncViewModel = koinViewModel(),
) {
    val paired by vm.paired.collectAsStateWithLifecycle()
    val hosting by vm.hosting.collectAsStateWithLifecycle()

    // The listener runs only while this page is on screen. That, and the PIN, is the whole of the
    // security model — so leaving the page has to close it, not merely stop looking at it.
    DisposableEffect(Unit) { onDispose { vm.stopHosting() } }

    SettingsPage(modifier) {
        val listening = hosting as? CompanionServerState.Listening
        item(key = "local-sync-pill") { SyncModePill(on = listening != null) }

        settingsSection(R.string.local_sync_title) {
            // One state, one switch. Sync mode is what makes this device reachable at all — every
            // other action on this screen, in either direction, needs the FAR device to have it on
            // too, which is why it is the first thing on the page and why the failure message names
            // exactly where to find it.
            SettingRow(
                title = stringResource(R.string.local_sync_mode),
                subtitle = stringResource(R.string.local_sync_mode_description),
                checked = listening != null,
                enabled = !vm.busy,
                onCheckedChange = { on -> if (on) vm.startHosting() else vm.stopHosting() },
            )
            SettingRow(
                title = stringResource(R.string.local_sync_connect),
                subtitle = stringResource(R.string.local_sync_connect_description),
                enabled = !vm.busy,
                onClick = vm::beginPairing,
            )
            listening?.let { HostingCard(it, vm.deviceName) }
        }

        if (paired.isNotEmpty()) {
            // Only the devices whose names collide get a code, so a normal household never sees one.
            val codes = shortCodes(paired)
            settingsSection(R.string.local_sync_paired_devices) {
                paired.forEach { device ->
                    SettingRow(
                        title = codes[device.id]
                            ?.let { stringResource(R.string.local_sync_device_with_code, device.name, it) }
                            ?: device.name,
                        subtitle = lastSyncedText(device.lastSyncAt),
                        showChevron = true,
                        enabled = !vm.busy,
                        onClick = { vm.chooseDevice(device) },
                    )
                }
            }
        }

        if (vm.busy) {
            settingsGroup(key = "local-sync-busy") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(MobileDimens.GapMedium),
                    verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                ) {
                    Text(stringResource(R.string.local_sync_working), style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }

    when (val step = vm.step) {
        null -> Unit
        is LocalSyncViewModel.Step.FindDevice -> FindDeviceSheet(vm)
        is LocalSyncViewModel.Step.EnterPin -> PinSheet(onSubmit = vm::submitPin, onDismiss = vm::cancel)
        is LocalSyncViewModel.Step.ChooseDirection -> DirectionSheet(
            deviceName = step.device.name,
            onChoose = vm::chooseDirection,
            onUnpair = { vm.unpair(step.device); vm.cancel() },
            onDismiss = vm::cancel,
        )
        is LocalSyncViewModel.Step.ChooseSections -> SectionsSheet(
            direction = step.direction,
            onStart = vm::start,
            onDismiss = vm::cancel,
        )
        is LocalSyncViewModel.Step.Confirm -> ConfirmSheet(
            preview = step.preview,
            direction = step.direction,
            onConfirm = vm::confirm,
            onDismiss = vm::cancel,
        )
        is LocalSyncViewModel.Step.Result -> ResultSheet(step, vm::cancel)
    }

    vm.error?.let { ErrorSheet(it, vm::dismissError) }
}

/**
 * The same feature offered during first-run setup, as the third way of furnishing a new phone: copy
 * the device you already have over the Wi-Fi, instead of typing a playlist in or finding a backup.
 *
 * It lives beside [SettingsLocalSyncPage] so it can reuse that page's sheets unchanged — they are the
 * substance of it, and a second copy would be a second thing to keep right.
 *
 * Two things differ from the settings page, and only two:
 *  - **it never hosts.** A phone still being set up has nothing worth serving, and announcing an
 *    empty container on the network would only be something for the other device to find by mistake.
 *  - **the direction is not a question.** A device at this point in its life can only receive, so
 *    [LocalSyncViewModel.Step.ChooseDirection] is answered rather than shown. Which *sections* to
 *    take is still asked: someone moving to a new phone may want the playlists without the old
 *    device's settings.
 *
 * The television's counterpart is `SetupLocalSyncScreen`, and the sequence is deliberately identical.
 */
@Composable
fun SetupLocalSyncStep(
    onRestored: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: LocalSyncViewModel = koinViewModel(),
) {
    // Straight into discovery: arriving here IS the decision to look for the other device, so asking
    // the user to press "Find a device" as well would be asking twice.
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        vm.beginPairing()
        started = true
    }

    val step = vm.step
    LaunchedEffect(step, started) {
        when {
            !started -> Unit
            // Dismissing any sheet clears the step, and here that means leaving — there is no device
            // list underneath to fall back to.
            step == null -> onBack()
            step is LocalSyncViewModel.Step.ChooseDirection -> vm.chooseDirection(SyncDirection.RECEIVE)
            else -> Unit
        }
    }

    // Once the data has landed there is nothing left to cancel, so Back means the same as Done.
    BackHandler { if (step is LocalSyncViewModel.Step.Result) onRestored() else vm.cancel() }

    Box(modifier) {
        // Title only. Every sheet below already says what to do, and the find sheet in particular
        // carries this step's instruction verbatim — printing it here too showed it twice at once.
        SetupPage {
            Text(
                text = stringResource(R.string.setup_sync_device),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        when (step) {
            null -> Unit
            is LocalSyncViewModel.Step.FindDevice -> FindDeviceSheet(vm)
            is LocalSyncViewModel.Step.EnterPin -> PinSheet(onSubmit = vm::submitPin, onDismiss = vm::cancel)
            // Answered above; a sheet for it would flash a choice the user never made.
            is LocalSyncViewModel.Step.ChooseDirection -> Unit
            is LocalSyncViewModel.Step.ChooseSections -> SectionsSheet(
                direction = step.direction,
                onStart = vm::start,
                onDismiss = vm::cancel,
            )
            is LocalSyncViewModel.Step.Confirm -> ConfirmSheet(
                preview = step.preview,
                direction = step.direction,
                onConfirm = vm::confirm,
                onDismiss = vm::cancel,
            )
            // Not [ResultSheet]: its Close returns to the device list, and here the only thing left
            // to do is finish onboarding.
            is LocalSyncViewModel.Step.Result -> SetupResultSheet(step, onDone = onRestored)
        }

        vm.error?.let { ErrorSheet(it, vm::dismissError) }
    }
}

/** [ResultSheet] with the one difference setup needs: the button leaves the wizard, not the sheet. */
@Composable
private fun SetupResultSheet(result: LocalSyncViewModel.Step.Result, onDone: () -> Unit) {
    MobileBottomSheet(onDismissRequest = onDone, title = stringResource(R.string.local_sync_done)) {
        Column(
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            result.received?.let { Note(stringResource(R.string.local_sync_received_items, it.items)) }
        }
        SheetButtons(
            confirm = stringResource(R.string.common_done),
            confirmEnabled = true,
            onConfirm = onDone,
            onDismiss = onDone,
        )
    }
}

/**
 * The badge that says this device is reachable right now.
 *
 * Sync mode is the one piece of state on this screen with a consequence off the screen — a listening
 * port and an announcement on the network — so it is worth saying plainly rather than leaving it to
 * be inferred from a switch further down.
 */
@Composable
private fun SyncModePill(on: Boolean) {
    if (!on) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = MobileDimens.GapSmall),
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = stringResource(R.string.local_sync_mode_pill),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = MobileDimens.GapSmall, vertical = MobileDimens.GapTiny),
        )
    }
}

/** "Last synced: 2 hours ago", or the plain "Not synced yet" the EPG rows already use. */
@Composable
private fun lastSyncedText(at: Long): String = if (at <= 0) {
    stringResource(R.string.settings_epg_sources_not_synced)
} else {
    stringResource(
        R.string.local_sync_last_synced,
        DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
    )
}

/**
 * What the television has to be told: the PIN, and where to find this phone. The QR carries the
 * address only — never the PIN, so a photographed code on its own opens nothing.
 */
@Composable
private fun HostingCard(state: CompanionServerState.Listening, deviceName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MobileDimens.GapMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
    ) {
        Text(deviceName, style = MaterialTheme.typography.titleMedium)
        state.qr?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.local_sync_qr_description),
                modifier = Modifier.size(QR_SIZE),
            )
        }
        Text(
            text = stringResource(R.string.local_sync_pin_is, state.pin),
            style = MaterialTheme.typography.headlineSmall,
        )
        state.urls.firstOrNull()?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Note(stringResource(R.string.local_sync_host_hint))
    }
}

/** Discovery, a scanned code, or an address typed by hand — all three, because mDNS is not reliable. */
@Composable
private fun FindDeviceSheet(vm: LocalSyncViewModel) {
    var manual by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }

    if (scanning) {
        QrScanSheet(
            onScanned = { address, port ->
                scanning = false
                vm.chooseAddress(address, port)
            },
            onDismiss = { scanning = false },
        )
        return
    }

    MobileBottomSheet(onDismissRequest = vm::cancel, title = stringResource(R.string.local_sync_add_device)) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            Note(stringResource(R.string.local_sync_find_hint))
            if (vm.found.isEmpty()) {
                Label(stringResource(R.string.local_sync_searching))
            } else {
                Label(stringResource(R.string.local_sync_found))
                vm.found.forEach { device ->
                    // A device already paired says so instead of showing an address the user has no
                    // use for, and opens its actions rather than asking for a PIN it does not need.
                    val known = vm.pairedMatch(device)
                    SettingRow(
                        title = device.name,
                        subtitle = if (known != null) {
                            stringResource(R.string.local_sync_already_paired)
                        } else {
                            device.address
                        },
                        onClick = { vm.choose(device) },
                    )
                }
            }
            Label(stringResource(R.string.local_sync_scan_qr))
            SettingRow(
                title = stringResource(R.string.local_sync_scan_qr),
                subtitle = stringResource(R.string.local_sync_scan_qr_description),
                onClick = { scanning = true },
            )
            Label(stringResource(R.string.local_sync_manual_address))
            MobileTextField(
                value = manual,
                onValueChange = { manual = it },
                label = stringResource(R.string.local_sync_manual_address_label),
                imeAction = ImeAction.Done,
            )
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_backup_continue),
            confirmEnabled = manual.isNotBlank(),
            onConfirm = { vm.chooseAddress(manual.trim(), portOf(manual)) },
            onDismiss = vm::cancel,
        )
    }
}

@Composable
private fun PinSheet(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.local_sync_enter_pin)) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            Note(stringResource(R.string.local_sync_enter_pin_description))
            MobileTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(PIN_LENGTH) },
                label = stringResource(R.string.local_sync_pin_label),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
                onImeDone = { if (pin.length == PIN_LENGTH) onSubmit(pin) },
            )
        }
        SheetButtons(
            confirm = stringResource(R.string.local_sync_pair),
            confirmEnabled = pin.length == PIN_LENGTH,
            onConfirm = { onSubmit(pin) },
            onDismiss = onDismiss,
        )
    }
}

/** Three named directions. Nothing here is called "sync" on its own. */
@Composable
private fun DirectionSheet(
    deviceName: String,
    onChoose: (SyncDirection) -> Unit,
    onUnpair: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = deviceName) {
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
            SettingRow(
                title = stringResource(R.string.local_sync_send_to, deviceName),
                subtitle = stringResource(R.string.local_sync_send_description),
                onClick = { onChoose(SyncDirection.SEND) },
            )
            SettingRow(
                title = stringResource(R.string.local_sync_receive_from, deviceName),
                subtitle = stringResource(R.string.local_sync_receive_description),
                onClick = { onChoose(SyncDirection.RECEIVE) },
            )
            SettingRow(
                title = stringResource(R.string.local_sync_merge_with, deviceName),
                subtitle = stringResource(R.string.local_sync_merge_description),
                onClick = { onChoose(SyncDirection.MERGE) },
            )
            SettingRow(
                title = stringResource(R.string.local_sync_unpair),
                subtitle = stringResource(R.string.local_sync_unpair_description),
                onClick = onUnpair,
            )
        }
    }
}

/** The same tick-list Backup & Restore uses, because it is the same set of things. */
@Composable
private fun SectionsSheet(
    direction: SyncDirection,
    onStart: (Set<BackupManager.Section>) -> Unit,
    onDismiss: () -> Unit,
) {
    var sections by remember { mutableStateOf(BackupManager.Section.entries.toSet()) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(
            when (direction) {
                SyncDirection.SEND -> R.string.local_sync_what_to_send
                SyncDirection.RECEIVE -> R.string.local_sync_what_to_receive
                SyncDirection.MERGE -> R.string.local_sync_what_to_merge
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            Note(stringResource(R.string.local_sync_sections_hint))
            CheckRow(
                label = stringResource(R.string.local_sync_everything),
                description = null,
                checked = sections.size == BackupManager.Section.entries.size,
                onToggle = { on -> sections = if (on) BackupManager.Section.entries.toSet() else emptySet() },
            )
            BackupManager.Section.entries.forEach { section ->
                CheckRow(
                    label = stringResource(section.labelRes()),
                    description = stringResource(section.descriptionRes()),
                    checked = section in sections,
                    onToggle = { on -> sections = if (on) sections + section else sections - section },
                )
            }
            // No password field, deliberately. The two devices already share a secret from pairing
            // and core seals the payload with it, so the playlist logins travel without anyone being
            // asked to invent a passphrase in the middle of a sync.
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_backup_continue),
            confirmEnabled = sections.isNotEmpty(),
            onConfirm = { onStart(sections) },
            onDismiss = onDismiss,
        )
    }
}

/** The dry run. Nothing has been written when this is on screen. */
@Composable
private fun ConfirmSheet(
    preview: BackupManager.Preview,
    direction: SyncDirection,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // The other device's hardware settings and engine pins: offered only when it sent some, unticked.
    var deviceSettings by remember(preview) { mutableStateOf(false) }
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.local_sync_confirm_title)) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny),
        ) {
            if (preview.isEmpty) {
                Note(stringResource(R.string.local_sync_nothing_to_change))
            } else {
                Note(stringResource(R.string.local_sync_confirm_hint))
                PreviewLine(R.string.local_sync_change_profiles, preview.newProfiles)
                PreviewLine(R.string.local_sync_change_sources, preview.newSources)
                PreviewLine(R.string.local_sync_change_favorites, preview.newFavorites)
                PreviewLine(R.string.local_sync_change_history, preview.newHistory)
                PreviewLine(R.string.local_sync_change_resume, preview.newResume)
                PreviewLine(R.string.local_sync_change_reorder, preview.newReorder)
                PreviewLine(R.string.local_sync_change_settings, preview.changedSettings)
                PreviewLine(R.string.local_sync_change_deletions, preview.deletions)
                if (preview.hasCustomizations) Note(stringResource(R.string.local_sync_change_customize))
            }
            if (preview.hasDeviceSettings) {
                CheckRow(
                    label = stringResource(R.string.settings_backup_device_settings),
                    description = stringResource(R.string.settings_backup_device_settings_desc),
                    checked = deviceSettings,
                    onToggle = { deviceSettings = it },
                )
            }
            if (direction == SyncDirection.MERGE) Note(stringResource(R.string.local_sync_merge_note))
        }
        SheetButtons(
            confirm = stringResource(R.string.local_sync_apply),
            confirmEnabled = !preview.isEmpty,
            onConfirm = { onConfirm(deviceSettings) },
            onDismiss = onDismiss,
        )
    }
}

/** One counted change. Zeroes are left out — a list of noughts says nothing worth reading. */
@Composable
private fun PreviewLine(labelRes: Int, count: Int) {
    if (count <= 0) return
    Text(
        text = stringResource(labelRes, count),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ResultSheet(result: LocalSyncViewModel.Step.Result, onDismiss: () -> Unit) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.local_sync_done)) {
        Column(
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            result.received?.let { Note(stringResource(R.string.local_sync_received_items, it.items)) }
            if (result.sent) Note(stringResource(R.string.local_sync_sent))
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_close),
            confirmEnabled = true,
            onConfirm = onDismiss,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun ErrorSheet(failure: SyncFailure, onDismiss: () -> Unit) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.local_sync_failed)) {
        Column(Modifier.padding(horizontal = MobileDimens.ScreenPaddingH)) {
            Note(
                stringResource(
                    when (failure) {
                        SyncFailure.Unreachable -> R.string.local_sync_error_unreachable
                        SyncFailure.NotAuthorized -> R.string.local_sync_error_unauthorized
                        SyncFailure.BadPayload -> R.string.local_sync_error_bad_payload
                        SyncFailure.Unknown -> R.string.local_sync_error_unknown
                    },
                ),
            )
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_close),
            confirmEnabled = true,
            onConfirm = onDismiss,
            onDismiss = onDismiss,
        )
    }
}

/** `192.168.1.5:8089` or a whole URL both carry a port; a bare address means the usual one. */
private fun portOf(address: String): Int =
    address.removePrefix("http://").substringAfter(':', "").substringBefore('/')
        .toIntOrNull() ?: tv.own.owntv.core.companion.CompanionLink.DEFAULT_PORT

private const val PIN_LENGTH = 6
private val QR_SIZE = 200.dp

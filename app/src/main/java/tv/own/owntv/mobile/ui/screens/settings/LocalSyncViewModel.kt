package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.companion.CompanionServerState
import tv.own.owntv.core.sync.local.DiscoveredDevice
import tv.own.owntv.core.sync.local.LocalSyncManager
import tv.own.owntv.core.sync.local.PairedDevice
import tv.own.owntv.core.sync.local.SyncDirection
import tv.own.owntv.core.sync.local.SyncFailure

/**
 * Local sync, from the phone's side.
 *
 * Two roles, and the user picks which one this device is playing. **Hosting** puts a PIN and a QR
 * code on screen and waits — that is what the television scans or types. **Connecting** goes the
 * other way: the phone finds the television, is paired once, and from then on can send to it,
 * receive from it, or do both.
 *
 * Nothing is ever applied without being shown first. A received file sits in the cache until the
 * user has seen what it would change and said yes, because the one outcome worth engineering
 * against is somebody tapping the wrong direction and losing a household's watch history.
 */
class LocalSyncViewModel(
    private val sync: LocalSyncManager,
) : ViewModel() {

    val paired: StateFlow<List<PairedDevice>> = sync.pairedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val hosting: StateFlow<CompanionServerState> = sync.hostState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CompanionServerState.Idle)

    val deviceName: String get() = sync.deviceName

    /** Devices answering on the network right now, while the pairing sheet is open. */
    var found by mutableStateOf<List<DiscoveredDevice>>(emptyList())
        private set

    /** The step the screen is on. Everything the user is deciding lives here, not in the composable. */
    var step by mutableStateOf<Step?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    var error by mutableStateOf<SyncFailure?>(null)
        private set

    sealed interface Step {
        /** Choosing which device to pair with — discovery, a scanned QR, or a typed address. */
        data object FindDevice : Step

        /** The address is known; the PIN on the other device's screen is what is missing. */
        data class EnterPin(val address: String, val port: Int) : Step

        /** Paired. Which way should the data go? */
        data class ChooseDirection(val device: PairedDevice) : Step

        /** Which parts to merge. No password is asked for — the two devices agree a key themselves. */
        data class ChooseSections(val device: PairedDevice, val direction: SyncDirection) : Step

        /**
         * The dry run: what this device would gain and lose. Nothing has been written yet — [file] is
         * sitting in the cache and is only applied when the user confirms.
         */
        data class Confirm(
            val device: PairedDevice?,
            val direction: SyncDirection,
            val file: File,
            val preview: BackupManager.Preview,
            val sections: Set<BackupManager.Section>,
            val password: String?,
        ) : Step

        /** What happened, once it has. */
        data class Result(val received: BackupManager.ImportSummary?, val sent: Boolean) : Step
    }

    private var discovery: Job? = null
    private var incomingWatcher: Job? = null

    // --- hosting ---------------------------------------------------------------------------------

    /**
     * Starts listening so the television can reach this phone, and watches for a container arriving.
     * Nothing that lands is applied — it becomes a dry run the user confirms, exactly as a pull does.
     */
    fun startHosting() {
        busy = true
        viewModelScope.launch {
            sync.startHosting()
                .onFailure { error = SyncFailure.Unknown }
            busy = false
        }
        // Guarded: hosting can be switched off and on again, and a second collector would preview the
        // same arriving container twice.
        if (incomingWatcher?.isActive != true) incomingWatcher = viewModelScope.launch {
            sync.incoming.collect { file ->
                val payload = sync.previewIncoming(file).getOrNull() ?: return@collect
                step = Step.Confirm(
                    device = null,
                    direction = SyncDirection.RECEIVE,
                    file = payload.file,
                    preview = payload.preview,
                    sections = BackupManager.Section.entries.toSet(),
                    password = payload.password,
                )
            }
        }
    }

    fun stopHosting() = sync.stopHosting()

    // --- pairing ---------------------------------------------------------------------------------

    fun beginPairing() {
        step = Step.FindDevice
        found = emptyList()
        discovery?.cancel()
        discovery = viewModelScope.launch {
            sync.discover().collect { device ->
                if (found.none { it.address == device.address }) found = found + device
            }
        }
    }

    /** From the discovery list, a scanned QR code, or a typed address — all three land here. */
    fun chooseAddress(address: String, port: Int) {
        discovery?.cancel()
        step = Step.EnterPin(address, port)
    }

    /**
     * The pairing this discovered device already has here, or null.
     *
     * Its announced id first, because that is the thing that does not change; the address only as a
     * fallback for a device too old to announce one, where a router handing out a new lease would
     * make a known device look new.
     */
    fun pairedMatch(device: DiscoveredDevice): PairedDevice? {
        val known = paired.value
        return device.deviceId.takeIf { it.isNotBlank() }?.let { id -> known.firstOrNull { it.id == id } }
            ?: known.firstOrNull { it.address == device.address }
    }

    /**
     * Picking a device out of the found list. One that is already paired goes straight to its actions:
     * asking for a PIN again would be asking the user to prove something this device already knows.
     */
    fun choose(device: DiscoveredDevice) {
        val existing = pairedMatch(device)
        if (existing == null) {
            chooseAddress(device.address, device.port)
        } else {
            discovery?.cancel()
            step = Step.ChooseDirection(existing)
        }
    }

    fun submitPin(pin: String) {
        val current = step as? Step.EnterPin ?: return
        busy = true
        viewModelScope.launch {
            sync.pair(current.address, current.port, pin)
                .onSuccess { step = Step.ChooseDirection(it) }
                .onFailure { error = SyncFailure.NotAuthorized }
            busy = false
        }
    }

    fun unpair(device: PairedDevice) {
        viewModelScope.launch { sync.unpair(device.id) }
    }

    // --- syncing ---------------------------------------------------------------------------------

    fun chooseDevice(device: PairedDevice) {
        step = Step.ChooseDirection(device)
    }

    fun chooseDirection(direction: SyncDirection) {
        val device = (step as? Step.ChooseDirection)?.device ?: return
        step = Step.ChooseSections(device, direction)
    }

    /**
     * Does the half of the job that changes nothing: a send goes straight out (the other device is
     * the one merging, and a merge cannot destroy anything), while anything that would change THIS
     * device is fetched and previewed first.
     */
    fun start(sections: Set<BackupManager.Section>) {
        val current = step as? Step.ChooseSections ?: return
        busy = true
        viewModelScope.launch {
            when (current.direction) {
                SyncDirection.SEND -> sync.send(current.device, sections)
                    .onSuccess { step = Step.Result(received = null, sent = true) }
                    .onFailure { error = failure(it) }
                SyncDirection.RECEIVE, SyncDirection.MERGE -> sync.fetch(current.device, sections)
                    .onSuccess { payload ->
                        step = Step.Confirm(
                            current.device, current.direction, payload.file, payload.preview, sections, payload.password,
                        )
                    }
                    .onFailure { error = failure(it) }
            }
            busy = false
        }
    }

    /** The user has seen what will change and said yes. */
    fun confirm(deviceSettings: Boolean = false) {
        val current = step as? Step.Confirm ?: return
        busy = true
        viewModelScope.launch {
            // [deviceSettings]: the other device's hardware settings were ticked on the confirm sheet.
            sync.apply(current.file, current.sections, current.password)
                .onSuccess { summary ->
                    // A merge sends this device's own data back once the incoming half has landed,
                    // so both ends finish holding the same thing rather than one being a round behind.
                    val sent = current.device != null && current.direction == SyncDirection.MERGE &&
                        sync.send(current.device, current.sections).isSuccess
                    step = Step.Result(received = summary, sent = sent)
                }
                .onFailure { error = failure(it) }
            busy = false
        }
    }

    fun cancel() {
        (step as? Step.Confirm)?.file?.delete()
        discovery?.cancel()
        step = null
        sync.clearProgress()
    }

    fun dismissError() {
        error = null
    }

    override fun onCleared() {
        discovery?.cancel()
        sync.stopHosting()
        super.onCleared()
    }

    /**
     * Always logs. The reason used to be logged only when the manager had *not* already classified
     * the failure — but the manager classifies every one of them before returning, so the elvis was
     * never reached and a failed sync left no trail at all. A user reporting "it just says something
     * went wrong" could be answered with nothing.
     */
    private fun failure(t: Throwable): SyncFailure {
        val reason = (sync.progress.value as? tv.own.owntv.core.sync.local.SyncProgress.Failed)?.reason
            ?: SyncFailure.Unknown
        android.util.Log.w("LocalSyncViewModel", "Local sync failed: $reason", t)
        return reason
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

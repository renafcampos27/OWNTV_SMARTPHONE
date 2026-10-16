package tv.own.owntv.mobile.ui.screens.recordings

import androidx.lifecycle.SavedStateHandle
import tv.own.owntv.mobile.backup.DocumentExportState
import tv.own.owntv.mobile.backup.DocumentExportTicket
import tv.own.owntv.core.database.dao.RecordingDao
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.RecordingEntity
import tv.own.owntv.core.recording.RecordingIntegrity
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import tv.own.owntv.core.recording.RecordingManager
import tv.own.owntv.core.recording.RecordingStorageInfo
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.storage.MediaAvailability
import tv.own.owntv.core.storage.MediaAvailabilityProbe
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.mobile.ui.screens.library.VodTuner

/**
 * The phone's view of the same `recordings` table the television reads. Core does all of it; this
 * observes and offers the four things a recording can be told.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecordingsViewModel(
    private val context: android.content.Context,
    private val settings: SettingsRepository,
    private val recordings: RecordingManager,
    private val vodTuner: VodTuner,
    private val recordingDao: RecordingDao,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val exportState = DocumentExportState({ savedState[EXPORT_STATE] }, { savedState[EXPORT_STATE] = it })
    val pendingDocumentExport = savedState.getStateFlow<String?>(EXPORT_STATE, null)
    fun cancelExport() = exportState.clear()

    internal fun prepareExport(recording: RecordingEntity, multipart: Boolean): Boolean {
        return exportState.begin(DocumentExportTicket(
            if (multipart) DocumentExportTicket.Kind.RECORDING_PARTS else DocumentExportTicket.Kind.RECORDING,
            setOf(recording.profileId), recordingId = recording.id,
        ))
    }

    internal fun exportDestination(target: android.net.Uri?, kind: DocumentExportTicket.Kind) {
        val ticket = exportState.take(kind)
        if (target == null) return
        if (ticket == null) {
            _error.value = context.getString(tv.own.owntv.mobile.R.string.own_export_request_lost)
            return
        }
        export(ticket.recordingId!!, ticket.profileIds.single(), kind == DocumentExportTicket.Kind.RECORDING_PARTS, target)
    }

    /** Hand the user the location itself: no file manager browses the app's own storage since Android 11. */
    fun copyPath(path: String) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(path, path))
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun dismissError() { _error.value = null }
    private val playMutex = Mutex()
    private var playJob: kotlinx.coroutines.Job? = null
    private val exportMutex = Mutex()

    fun isParts(recording: RecordingEntity): Boolean = recordings.isParts(recording)

    /** Single-file exports may move; multipart exports copy the complete portable package. */
    private fun export(recordingId: Long, profileId: Long, expectedParts: Boolean, target: android.net.Uri) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            exportMutex.withLock {
                var destinationOpened = false
                var finished = false
                try {
                    val row = recordingDao.getById(recordingId) ?: error("Recording missing")
                    check(row.profileId == profileId && row.profileId == settings.activeProfileId.first() && RecordingIntegrity.canPlay(row))
                    val source = requireNotNull(MediaTarget.of(context, row.filePath))
                    check(source.stored != target.toString()) { "Destination is the source" }
                    val multipart = recordings.isParts(row)
                    check(multipart == expectedParts)
                    context.contentResolver.openOutputStream(target, "wt")?.use { out ->
                        destinationOpened = true
                        if (multipart) recordings.exportParts(row, out)
                        else source.openInput().use { input ->
                            val expected = source.length()
                            check(expected > 0)
                            var written = 0L
                            val buffer = ByteArray(128 * 1024)
                            while (true) {
                                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                val count = input.read(buffer)
                                if (count < 0) break
                                written += count
                                check(written <= expected)
                                out.write(buffer, 0, count)
                            }
                            check(written == expected && source.length() == expected)
                        }
                        out.flush()
                    } ?: error("Destination unavailable")
                    finished = true
                    if (!multipart && StorageAccess.persistAccess(context, target)) {
                        if (!recordings.relocate(row, target.toString())) {
                            _error.value = context.getString(tv.own.owntv.mobile.R.string.own_recording_export_copy)
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    _error.value = context.getString(tv.own.owntv.mobile.R.string.own_recording_export_error)
                } finally {
                    // A cancelled/failed ZIP must not be mistaken for a complete recording.
                    if (destinationOpened && !finished) runCatching {
                        android.provider.DocumentsContract.deleteDocument(context.contentResolver, target)
                    }
                }
            }
        }
    }

    val rows: StateFlow<List<RecordingEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else recordings.observe(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val listActive = MutableStateFlow(false)
    val unavailableFiles = recordingAvailability(
        rows,
        listActive,
        recordings::unavailableFiles,
    ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyMap())

    fun setListActive(active: Boolean) {
        listActive.value = active
        if (!active) playJob?.cancel()
    }

    /** Keyed on the download root as well as on the list — see the Downloads screen's own note. */
    val storage: StateFlow<RecordingStorageInfo?> =
        combine(rows, settings.downloadRoot) { _, _ -> Unit }
            .mapLatest { recordings.storageInfo() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** False when Android will not let the app set exact alarms, so a recording may begin late. */
    val timersAreExact: Boolean = recordings.timersAreExact()

    private val _playing = MutableStateFlow(false)

    /** True once a recording has actually started playing, so the screen knows to open the player. */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun play(recording: RecordingEntity) {
        if (playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            // Ignore duplicate taps instead of queueing several player starts.
            if (!playMutex.tryLock()) return@launch
            try {
                val row = withContext(kotlinx.coroutines.Dispatchers.IO) { recordings.current(recording) }
                    ?: error("Recording missing")
                check(row.profileId == settings.activeProfileId.first() && RecordingIntegrity.canPlay(row))
                val available = recordings.availability(row, verifyReadable = true)
                if (available != MediaAvailability.AVAILABLE) {
                    _error.value = MediaAvailabilityProbe.text(available, context.resources)
                    return@launch
                }
                val multipart = withContext(kotlinx.coroutines.Dispatchers.IO) { recordings.isParts(row) }
                // Load optional metadata before acquiring the local playback server.
                val chapters = recordings.chapters(row)
                val playback = if (multipart) recordings.openParts(row) ?: error("Recording parts unavailable") else null
                val path = playback?.url ?: requireNotNull(row.filePath)
                // From here the tuner closes an untransferred multipart lease, also on cancellation.
                _playing.value = vodTuner.playRecording(path, row.title, row.channelIconUrl, playback, chapters, expectedProfileId = row.profileId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _error.value = context.getString(tv.own.owntv.mobile.R.string.own_recording_play_error)
            } finally {
                playMutex.unlock()
            }
        }
    }

    fun playerOpened() {
        _playing.value = false
    }

    fun stop(recording: RecordingEntity) = recordings.stop(recording)
    fun cancel(recording: RecordingEntity) = recordings.cancel(recording)
    fun delete(recording: RecordingEntity) {
        viewModelScope.launch {
            if (recording.profileId == settings.activeProfileId.first()) recordings.delete(recording)
        }
    }

    fun retry(recording: RecordingEntity) {
        viewModelScope.launch { recordings.retryRecording(recording) }
    }
    private companion object { const val EXPORT_STATE = "recording_document_export" }

}

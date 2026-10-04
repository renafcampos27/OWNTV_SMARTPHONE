package tv.own.owntv.mobile.ui.screens.recordings

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
import tv.own.owntv.core.model.RecordingStatus
import tv.own.owntv.core.recording.RecordingManager
import tv.own.owntv.core.recording.RecordingStorageInfo
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.MediaTarget
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
) : ViewModel() {

    /** Hand the user the location itself: no file manager browses the app's own storage since Android 11. */
    fun copyPath(path: String) {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText(path, path))
    }

    /**
     * **Move** a finished recording wherever the user says, through the system's own save dialog.
     *
     * Recordings land in the app's own folder unless the user has chosen another, and that folder is
     * invisible to every file manager since Android 11 — so this is the way out. It used to be a
     * copy, which left two of everything and a row still pointing at the hidden one; now the row
     * follows the file.
     *
     * Copy, take a lasting grant, re-point the row, then delete the original — in that order. A
     * refused grant abandons the move as a copy rather than leaving the row pointing at a file that
     * would stop opening the next time the app starts.
     */
    fun export(recording: RecordingEntity, target: android.net.Uri) {
        val source = MediaTarget.of(context, recording.filePath) ?: return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val copied = runCatching {
                context.contentResolver.openOutputStream(target)?.use { out ->
                    source.openInput().use { it.copyTo(out) }
                } != null
            }.getOrDefault(false)
            if (!copied) return@launch
            if (!StorageAccess.persistAccess(context, target)) return@launch
            recordings.relocate(recording, target.toString())
        }
    }

    val rows: StateFlow<List<RecordingEntity>> = settings.activeProfileId
        .flatMapLatest { pid -> if (pid < 0) flowOf(emptyList()) else recordings.observe(pid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
        if (recording.status != RecordingStatus.COMPLETED) return
        val path = recording.filePath ?: return
        viewModelScope.launch {
            _playing.value = vodTuner.playRecording(path, recording.title, recording.channelIconUrl)
        }
    }

    fun playerOpened() {
        _playing.value = false
    }

    fun stop(recording: RecordingEntity) = recordings.stop(recording)
    fun cancel(recording: RecordingEntity) = recordings.cancel(recording)
    fun delete(recording: RecordingEntity) = recordings.delete(recording)

    fun retry(recording: RecordingEntity) {
        viewModelScope.launch { recordings.retryRecording(recording) }
    }
}

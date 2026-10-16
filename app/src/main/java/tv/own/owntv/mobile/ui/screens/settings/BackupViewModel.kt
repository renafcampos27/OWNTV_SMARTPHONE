package tv.own.owntv.mobile.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.profile.ProfileManager
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.R
import tv.own.owntv.mobile.backup.BackupTransferFiles
import tv.own.owntv.mobile.backup.DocumentExportState
import tv.own.owntv.mobile.backup.DocumentExportTicket
import tv.own.owntv.mobile.backup.copyBackupBytes
import tv.own.owntv.mobile.backup.runBackupOperation
import java.io.File

/**
 * Writing a backup out to a file the user picked, and reading one back in.
 *
 * The backup itself is core's, shared with the television — what is this app's own is the document
 * picker at each end. Core reads and writes real files, so a picked document is copied into the
 * cache before it is opened, and an export is written to the cache and then poured into the URI the
 * picker handed back. That is the price of never asking a phone for all-files access.
 */
class BackupViewModel(
    private val backup: BackupManager,
    private val profileManager: ProfileManager,
    private val context: Context,
    profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val restoredSources: tv.own.owntv.core.sync.RestoredSourceRefresh,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val files = BackupTransferFiles(File(context.cacheDir, "backup-transfers"))
    private val exportState = DocumentExportState({ savedState[EXPORT_STATE] }, { savedState[EXPORT_STATE] = it })
    val pendingDocumentExport = savedState.getStateFlow<String?>(EXPORT_STATE, null)
    private var exportSecret: Pair<String, String>? = null
    var exportPasswordRequired by mutableStateOf(exportState.peek()?.let { it.target != null && it.encrypted } == true)
        private set

    init {
        viewModelScope.launch {
            try { files.prepare() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { /* Report on the next operation. */ }
        }
    }

    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val lastBackup = settings.lastBackup.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val activeProfileId: StateFlow<Long> = settings.activeProfileId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), -1L)

    /** A backup of a large catalog takes a while; nothing else may be started on top of it. */
    var busy by mutableStateOf(false)
        private set

    /** What the finished action has to tell the user, shown until they dismiss it. */
    var outcome by mutableStateOf<Outcome?>(null)
        private set

    /** The file being restored, as far as it has been understood so far. */
    var pending by mutableStateOf<PendingRestore?>(null)
        private set

    sealed interface Outcome {
        data class Exported(val name: String, val passwordsOmitted: Boolean) : Outcome
        data class Restored(val summary: BackupManager.ImportSummary) : Outcome
        data class Failed(@param:StringRes val messageRes: Int) : Outcome
    }

    /**
     * [inspection] is null while a sealed file is still locked — a sealed container says nothing at
     * all about itself, not even which sections it holds, until the password opens it.
     */
    data class PendingRestore(
        val file: File,
        val inspection: BackupManager.Inspection? = null,
        val password: String? = null,
        val sealed: Boolean = false,
        val wrongPassword: Boolean = false,
    )

    fun verifyPin(profile: ProfileEntity, pin: String): Boolean = profileManager.verifyPin(profile, pin)

    /** The picked document could not even be copied out of its provider — nothing to inspect. */
    fun reportReadFailure() {
        outcome = Outcome.Failed(R.string.settings_backup_read_error)
    }

    fun dismissOutcome() {
        outcome = null
    }

    fun cancelRestore() {
        if (busy) return
        val directory = pending?.file?.parentFile
        pending = null
        cleanup(directory)
    }

    /** Persist choices before launching SAF. The passphrase stays in this ViewModel only. */
    fun prepareExport(
        sections: Set<BackupManager.Section>,
        profileIds: Set<Long>,
        password: String?,
    ): Boolean {
        if (busy || sections.isEmpty() || profileIds.isEmpty()) return false
        val ticket = DocumentExportTicket(DocumentExportTicket.Kind.BACKUP, profileIds, sections.map { it.name }.toSet(), encrypted = !password.isNullOrBlank())
        if (!exportState.begin(ticket)) return false
        exportSecret = password?.takeIf { it.isNotBlank() }?.let { ticket.nonce to it }
        return true
    }

    fun exportDestination(target: Uri?) {
        if (target == null) { cancelExport(); return }
        val ticket = exportState.target(target.toString())
        if (ticket == null) {
            outcome = Outcome.Failed(tv.own.owntv.mobile.R.string.own_export_request_lost)
            return
        }
        if (ticket.encrypted && exportSecret?.first != ticket.nonce) exportPasswordRequired = true
        else finishExport(exportSecret?.second)
    }

    fun resumeExport(password: String) {
        if (password.isBlank() || busy) return
        finishExport(password)
    }

    fun cancelExport() {
        val abandoned = exportState.peek()?.target?.let(Uri::parse)
        exportState.clear()
        exportSecret = null
        exportPasswordRequired = false
        if (abandoned != null) viewModelScope.launch(NonCancellable + Dispatchers.IO) {
            runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, abandoned) }
        }
    }

    private fun finishExport(password: String?) {
        if (busy) return
        val ticket = exportState.peek() ?: return
        val target = ticket.target?.let(Uri::parse) ?: return
        if (ticket.encrypted && password.isNullOrBlank()) return
        exportState.take(DocumentExportTicket.Kind.BACKUP) ?: return
        exportSecret = null
        exportPasswordRequired = false
        operation(R.string.settings_backup_export_error) {
            files.use { staged ->
                val sections = ticket.sections.map { BackupManager.Section.valueOf(it) }.toSet()
                val path = backup.export(staged, sections, password, ticket.profileIds, recordCompletion = false).getOrThrow()
                val source = File(path)
                var opened = false
                var completed = false
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(target, "wt")?.use { output ->
                            opened = true
                            source.inputStream().use { input -> copyBackupBytes(input, output) }
                        } ?: error("backup target unavailable")
                    }
                    completed = true
                    // The final URI, not a soon-to-be-deleted cache file, is the completed backup.
                    val bytes = withContext(Dispatchers.IO) { source.length() }
                    settings.recordBackup(System.currentTimeMillis(), bytes, ticket.encrypted, target.toString())
                    outcome = Outcome.Exported("owntv-backup.own", passwordsOmitted = !ticket.encrypted)
                } finally {
                    if (opened && !completed) withContext(NonCancellable + Dispatchers.IO) {
                        runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver, target) }
                    }
                }
            }
        }
    }

    /** The ViewModel owns copying and inspection, so leaving the composable cannot orphan a file. */
    fun beginRestore(uri: Uri) {
        operation(R.string.settings_backup_read_error) {
            val old = pending?.file?.parentFile
            pending = null
            old?.let { files.delete(it) }
            val staged = files.create()
            try {
                val file = File(staged, "import.own")
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output -> copyBackupBytes(input, output) }
                    } ?: error("backup input unavailable")
                }
                pending = if (backup.isSealed(file)) PendingRestore(file = file, sealed = true)
                else PendingRestore(file = file, inspection = backup.sectionsIn(file).getOrThrow())
            } finally {
                if (pending?.file?.parentFile != staged) files.delete(staged)
            }
        }
    }

    /** The password for a sealed file, which is the only thing that can reveal what is inside it. */
    fun unseal(password: String) {
        val current = pending ?: return
        operation(R.string.settings_backup_read_error) {
            try {
                pending = current.copy(inspection = backup.sectionsIn(current.file, password).getOrThrow(), password = password, wrongPassword = false)
            } catch (wrong: BackupManager.WrongPasswordException) {
                pending = current.copy(wrongPassword = true)
            } catch (cancelled: CancellationException) {
                pending = null
                files.delete(current.file.parentFile!!)
                throw cancelled
            } catch (failure: Exception) {
                pending = null
                files.delete(current.file.parentFile!!)
                throw failure
            }
        }
    }

    /**
     * Restore [sections]. [password] may be null even for an encrypted file: everything but the
     * saved passwords comes back, which is a fair trade for a forgotten passphrase.
     */
    fun restore(sections: Set<BackupManager.Section>, password: String?, deviceSettings: Boolean = false) {
        val current = pending ?: return
        operation(R.string.settings_backup_import_error) {
            var keepForPassword = false
            try {
                val summary = backup.import(current.file, sections, password ?: current.password, deviceSettings = deviceSettings).getOrThrow()
                if (BackupManager.Section.SOURCES in sections) restoredSources.enqueueMissingCatalogs()
                outcome = Outcome.Restored(summary)
            } catch (wrong: BackupManager.WrongPasswordException) {
                keepForPassword = true
                pending = current.copy(wrongPassword = true)
            } finally {
                if (!keepForPassword) {
                    pending = null
                    files.delete(current.file.parentFile!!)
                }
            }
        }
    }

    private fun operation(@StringRes errorRes: Int, block: suspend () -> Unit) {
        if (busy) return
        busy = true // Reserve synchronously: two rapid taps cannot launch overlapping operations.
        viewModelScope.launch {
            runBackupOperation(onFailure = { outcome = Outcome.Failed(errorRes) }, onFinished = { busy = false }, block = block)
        }
    }

    private fun cleanup(directory: File?) {
        if (directory != null) viewModelScope.launch(NonCancellable + Dispatchers.IO) { runCatching { files.delete(directory) } }
    }

    override fun onCleared() {
        exportSecret = null
        cleanup(pending?.file?.parentFile)
        pending = null
        super.onCleared()
    }

    private companion object {
        /** Long enough that rotating the phone does not restart the profile query. */
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPORT_STATE = "backup_document_export"
    }
}

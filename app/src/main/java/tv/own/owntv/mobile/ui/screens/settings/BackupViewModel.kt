package tv.own.owntv.mobile.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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
import tv.own.owntv.mobile.R
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
    settings: SettingsRepository,
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

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
        pending?.file?.delete()
        pending = null
    }

    /** Writes the backup into the cache, then into [target]; the cached copy never survives the call. */
    fun export(
        target: Uri,
        name: String,
        sections: Set<BackupManager.Section>,
        profileIds: Set<Long>,
        password: String?,
    ) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val staged = File(context.cacheDir, EXPORT_DIR)
            val result = backup.export(staged, sections, password, profileIds)
                .mapCatching { path -> copyOut(File(path), target) }
            staged.deleteRecursively()
            outcome = result.fold(
                onSuccess = { Outcome.Exported(name, passwordsOmitted = password.isNullOrBlank()) },
                onFailure = { Outcome.Failed(R.string.settings_backup_export_error) },
            )
            busy = false
        }
    }

    /** Step one of a restore: what is in this file, and does it need a password before anyone can look. */
    fun beginRestore(file: File) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            val sealed = backup.isSealed(file)
            pending = if (sealed) {
                PendingRestore(file = file, sealed = true)
            } else {
                backup.sectionsIn(file).fold(
                    onSuccess = { PendingRestore(file = file, inspection = it) },
                    onFailure = {
                        outcome = Outcome.Failed(R.string.settings_backup_read_error)
                        file.delete()
                        null
                    },
                )
            }
            busy = false
        }
    }

    /** The password for a sealed file, which is the only thing that can reveal what is inside it. */
    fun unseal(password: String) {
        val current = pending ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            backup.sectionsIn(current.file, password).fold(
                onSuccess = { pending = current.copy(inspection = it, password = password, wrongPassword = false) },
                onFailure = { pending = current.copy(wrongPassword = true) },
            )
            busy = false
        }
    }

    /**
     * Restore [sections]. [password] may be null even for an encrypted file: everything but the
     * saved passwords comes back, which is a fair trade for a forgotten passphrase.
     */
    fun restore(sections: Set<BackupManager.Section>, password: String?, deviceSettings: Boolean = false) {
        val current = pending ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            backup.import(current.file, sections, password ?: current.password, deviceSettings = deviceSettings).fold(
                onSuccess = {
                    outcome = Outcome.Restored(it)
                    current.file.delete()
                    pending = null
                },
                onFailure = { failure ->
                    if (failure is BackupManager.WrongPasswordException) {
                        pending = current.copy(wrongPassword = true)
                    } else {
                        outcome = Outcome.Failed(R.string.settings_backup_import_error)
                        current.file.delete()
                        pending = null
                    }
                },
            )
            busy = false
        }
    }

    private suspend fun copyOut(source: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("backup target unavailable")
    }

    private companion object {
        /** Long enough that rotating the phone does not restart the profile query. */
        const val STOP_TIMEOUT_MS = 5_000L
        const val EXPORT_DIR = "backup-export"
    }
}

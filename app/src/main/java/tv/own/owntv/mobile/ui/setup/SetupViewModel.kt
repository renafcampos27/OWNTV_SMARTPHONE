package tv.own.owntv.mobile.ui.setup

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.own.owntv.core.backup.BackupManager
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.setup.SourceImporter
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.mobile.R
import java.io.File

/**
 * Adding a playlist, or restoring a backup, on the phone.
 *
 * The sequence itself — add, sync, finalize, and undo it all when it fails — is core's
 * [SourceImporter], shared with the television. What is this app's own is the job the import runs in
 * and the profile it attaches to: on a phone that is already set up there is an active profile to
 * add to, and only a first run creates one.
 *
 * The import runs in [appScope], not in this ViewModel's own scope. "Run in background" leaves the
 * wizard, and leaving it here means popping a navigation entry — which clears the ViewModel that
 * entry owns and would cancel the very sync the button promised to keep running. The job is still
 * cancelled explicitly by Cancel; it is only the screen going away that no longer stops it.
 */
class SetupViewModel(
    private val importer: SourceImporter,
    private val profileDao: ProfileDao,
    private val settings: SettingsRepository,
    private val appScope: CoroutineScope,
    private val context: Context,
) : ViewModel() {

    val state = importer.state
    val progress = importer.progress

    private var importJob: Job? = null

    fun startXtream(
        name: String,
        server: String,
        username: String,
        password: String,
        userAgent: String,
        httpReferer: String,
        autoRefresh: PlaylistRefresh,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
        preferHls: Boolean,
    ) = runImport {
        importer.xtream(
            name = name, server = server, username = username, password = password,
            userAgent = userAgent, httpReferer = httpReferer, autoRefresh = autoRefresh,
            live = live, movies = movies, series = series, preferHls = preferHls,
        )
    }

    fun startM3u(name: String, url: String, userAgent: String, httpReferer: String, autoRefresh: PlaylistRefresh) =
        runImport {
            importer.m3u(name = name, url = url, userAgent = userAgent, httpReferer = httpReferer, autoRefresh = autoRefresh)
        }

    fun startStalker(
        name: String,
        portalUrl: String,
        mac: String,
        serialNumber: String,
        deviceId: String,
        deviceId2: String,
        signature: String,
        userAgent: String,
        httpReferer: String,
        autoRefresh: PlaylistRefresh,
        live: SyncScopeChoice,
        movies: SyncScopeChoice,
        series: SyncScopeChoice,
    ) = runImport {
        importer.stalker(
            name = name, portalUrl = portalUrl, mac = mac, serialNumber = serialNumber,
            deviceId = deviceId, deviceId2 = deviceId2, signature = signature, userAgent = userAgent,
            httpReferer = httpReferer, autoRefresh = autoRefresh, live = live, movies = movies, series = series,
        )
    }

    /**
     * Restore [sections] from a backup file; an encrypted one asks for its password first.
     *
     * [sections] defaults to all of it, which is what this did unconditionally before the wizard
     * gained a tick-list of its own.
     */
    fun importBackup(
        file: File,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        /** Take another device's hardware settings too — see [BackupManager.import]. */
        deviceSettings: Boolean = false,
    ) {
        importJob?.cancel()
        importJob = appScope.launch { importer.importBackup(file, sections, deviceSettings) }
    }

    fun restoreWithPassword(
        file: File,
        password: String?,
        sections: Set<BackupManager.Section> = BackupManager.Section.entries.toSet(),
        deviceSettings: Boolean = false,
    ) {
        importJob?.cancel()
        importJob = appScope.launch { importer.restoreWithPassword(file, password, sections, deviceSettings) }
    }

    /**
     * "Run in background": enter the app now and let the import finish on its own. It deliberately
     * does not cancel — cancelling would undo the playlist the user just added — and the sync pill
     * on the shell is what tells them it is still going.
     */
    fun continueInBackground(onDone: (Long?) -> Unit) {
        importer.backgroundHandoff = true
        finish(onDone)
    }

    /**
     * Ends the flow: the profile the content landed on becomes the active one, and its id is handed
     * back so the shell can admit it without asking who is watching — the television's `onDone`
     * carries the same id for the same reason.
     */
    fun finish(onDone: (Long?) -> Unit) {
        appScope.launch {
            val profileId = importer.finish()
            onMain { onDone(profileId) }
        }
    }

    /**
     * Hands a wizard callback back to the main thread.
     *
     * [appScope] is a background scope — it has to be, so "Run in background" survives the screen
     * going away — but every one of these callbacks ends up moving the user somewhere, and
     * navigation must be touched on the main thread or `setCurrentState` throws and takes the whole
     * process with it. That is what closed the app on "OK" after an import.
     */
    private suspend fun onMain(block: () -> Unit) = withContext(Dispatchers.Main) { block() }

    /**
     * The profile the wizard's own step creates, before any content exists to hang on it.
     *
     * [attachToProfile] makes one silently for anyone who reached the form without a profile; this is
     * the deliberate one, with the name and avatar the user chose. Both go through core's importer, so
     * whichever ran first is the profile the content lands on.
     */
    fun createProfile(name: String, avatarId: Int, isKids: Boolean, pin: String?, onCreated: () -> Unit) {
        appScope.launch {
            importer.createProfile(name, avatarId, isKids, pin)
            onMain(onCreated)
        }
    }

    /** Playlists another profile already has, which this one can share rather than re-import. */
    suspend fun availableExistingSources(): List<SourceEntity> = importer.availableExistingSources()

    /** Link the chosen existing playlists to this profile, then re-sync each one. */
    fun linkExisting(sourceIds: Set<Long>) = runImport { importer.linkExisting(sourceIds) }

    fun reset() = importer.reset()

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importer.reset()
    }

    private fun runImport(block: suspend () -> Unit) {
        importJob?.cancel()
        val job = appScope.launch {
            attachToProfile()
            block()
        }
        importJob = job
        job.invokeOnCompletion { if (importJob == job) importJob = null }
    }

    /**
     * Which profile the new playlist belongs to. An app that has been used before already has one,
     * and adding a second playlist must not quietly create a second profile to hang it on; only a
     * genuinely empty install makes one, named the way the wizard would name it.
     */
    private suspend fun attachToProfile() {
        val existing = settings.activeProfileIdNow().takeIf { it >= 0 }
            ?: profileDao.getAllOnce().firstOrNull()?.id
        if (existing != null) {
            importer.useProfile(existing)
        } else {
            importer.createProfile(
                name = context.getString(R.string.setup_default_profile),
                avatarId = 0,
                isKids = false,
                pin = null,
            )
        }
    }
}

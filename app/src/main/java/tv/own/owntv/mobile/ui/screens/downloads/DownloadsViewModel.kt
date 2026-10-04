package tv.own.owntv.mobile.ui.screens.downloads

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.customize.CustomizationStore
import tv.own.owntv.core.customize.CustomizeKeys
import tv.own.owntv.core.customize.SectionCustomizations
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.DownloadDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.ProfileDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.download.DownloadManager
import tv.own.owntv.core.download.DownloadStorageInfo
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.storage.MediaRoot
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.mobile.ui.screens.library.VodTuner
import java.io.File

/**
 * What has been saved for offline watching, and how the queue is getting on.
 *
 * The queue itself is core's — the same worker that feeds the television — so this only reads it,
 * filters out what the profile is not allowed to see, and offers the four buttons a download can
 * need. The one thing computed here is the transfer rate: core records bytes, not speed, so the
 * screen measures it from two readings a second apart.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModel(
    private val context: Context,
    private val downloadDao: DownloadDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val profileDao: ProfileDao,
    private val customize: CustomizationStore,
    private val settings: SettingsRepository,
    private val downloadManager: DownloadManager,
    private val vodTuner: VodTuner,
) : ViewModel() {

    /**
     * The profile's downloads, minus rows whose film or show the user has hidden — hidden is hidden
     * everywhere, Downloads included. The file stays on disk and the row comes back on unhide.
     */
    val downloads: StateFlow<List<DownloadEntity>> = settings.activeProfileId
        .flatMapLatest { pid ->
            if (pid < 0) {
                flowOf(emptyList())
            } else {
                combine(
                    downloadDao.observeForProfile(pid),
                    customize.observe(pid, MediaType.MOVIE),
                    customize.observe(pid, MediaType.SERIES),
                    profileDao.observeById(pid),
                ) { list, custMovie, custSeries, profile ->
                    if (custMovie.hiddenItems.isEmpty() && custSeries.hiddenItems.isEmpty() &&
                        profile?.isKids != true
                    ) {
                        list
                    } else {
                        list.filterNot { isHidden(it, custMovie, custSeries, profile?.isKids == true) }
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun isHidden(
        d: DownloadEntity,
        custMovie: SectionCustomizations,
        custSeries: SectionCustomizations,
        isKidsProfile: Boolean,
    ): Boolean = when (d.mediaType) {
        MediaType.MOVIE -> movieDao.getById(d.itemId)?.let { movie ->
            CustomizeKeys.movie(movie) in custMovie.hiddenItems ||
                (isKidsProfile && AdultCategoryClassifier.isAdult(movie.categoryId?.let { categoryDao.getById(it)?.name }))
        } ?: isKidsProfile
        MediaType.EPISODE -> seriesDao.getEpisodeById(d.itemId)
            ?.let { ep -> seriesDao.getSeriesById(ep.seriesId) }
            ?.let { series ->
                CustomizeKeys.series(series) in custSeries.hiddenItems ||
                    (isKidsProfile && AdultCategoryClassifier.isAdult(series.categoryId?.let { categoryDao.getById(it)?.name }))
            } ?: isKidsProfile
        else -> false
    }

    /**
     * Free and total space on whichever volume the downloads are being written to.
     *
     * Keyed on the download **root** as well as on the list. Keyed on the list alone, changing the
     * folder left the bar showing the old volume until the app was restarted — and on a screen with
     * nothing downloaded yet the list never changes, so it never refreshed at all.
     */
    val storage: StateFlow<DownloadStorageInfo?> =
        combine(downloads, settings.downloadRoot) { _, _ -> Unit }
            .mapLatest { downloadManager.storageInfo() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * How fast the queue is moving, in megabits per second, from the growth of the running rows.
     * Zero whenever nothing is running, and after a pause, so a stale figure never lingers.
     */
    val speedMbps: StateFlow<Double> = flow {
        var lastBytes = -1L
        var lastAt = 0L
        while (true) {
            val running = downloads.value.filter { it.status == DownloadStatus.RUNNING }
            val bytes = running.sumOf { it.downloadedBytes }
            val now = System.currentTimeMillis()
            emit(
                if (running.isEmpty() || lastBytes < 0 || now <= lastAt) 0.0
                else (bytes - lastBytes).coerceAtLeast(0L) * 8.0 / (now - lastAt) / 1_000.0,
            )
            lastBytes = if (running.isEmpty()) -1L else bytes
            lastAt = now
            delay(SPEED_SAMPLE_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    /**
     * The volumes downloads can go to without asking for anything: this app's own folder on internal
     * storage, and one on every memory card or stick that is plugged in.
     *
     * On a phone with no card this is a list of one, which is why it is no longer the whole story —
     * "Choose another folder" opens the system picker beside it. A television browses real
     * directories instead, because it holds all-files access; a phone bound for Google Play cannot,
     * so the Storage Access Framework is the only way it reaches a folder of the user's own choosing.
     */
    val volumes: List<StorageAccess.StorageRoot> = StorageAccess.appRoots(context)

    val downloadRoot: StateFlow<String> = settings.downloadRoot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * The chosen folder as something to read — `Films/OwnTV` — or null when downloads are going to
     * one of the volumes above and the row for it is already ticked.
     */
    val chosenFolder: StateFlow<String?> = settings.downloadRoot
        .map { root -> root.takeIf { MediaTarget.isDocument(it) }?.let(StorageAccess::folderLabel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * True when downloads are pointed at a folder the app can no longer write to: the user withdrew
     * the permission in system settings, or the storage it was on came out.
     *
     * Worth saying out loud rather than leaving to the first failed download, because from the
     * outside the two look identical and only one of them is fixable by pressing Retry.
     */
    val folderLost: StateFlow<Boolean> = settings.downloadRoot
        .map { root -> MediaTarget.isDocument(root) && !StorageAccess.hasTree(context, root) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setDownloadRoot(path: String) {
        viewModelScope.launch {
            releasePreviousTree(path)
            settings.setDownloadRoot(path)
        }
    }

    /**
     * Remember the folder the user just picked in the system picker.
     *
     * The grant has to be made **persistable** first: a tree URI is granted to the activity that
     * asked and lasts only as long as the process, while downloads outlive both — a transfer runs in
     * a foreground service and a recording can start days later. If the system refuses to persist
     * it, the folder is not adopted at all rather than adopted and silently unusable tomorrow.
     */
    fun setDownloadTree(tree: Uri) {
        viewModelScope.launch {
            if (!StorageAccess.persistAccess(context, tree)) return@launch
            val stored = tree.toString()
            releasePreviousTree(stored)
            settings.setDownloadRoot(stored)
            withContext(Dispatchers.IO) { MediaRoot.of(context, stored).ensureFolders() }
        }
    }

    /** Hand back the grant on a folder that is no longer the download folder. */
    private suspend fun releasePreviousTree(replacement: String) {
        val previous = settings.downloadRoot.first()
        if (previous.isNotBlank() && previous != replacement) {
            StorageAccess.releaseTree(context, previous)
        }
    }

    /**
     * Whether the queue waits for Wi-Fi. It sits with the folder because the two are the only
     * choices this screen has, and a user who opens them is asking one question: where these go and
     * what they cost.
     */
    val wifiOnly: StateFlow<Boolean> = settings.downloadsWifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setWifiOnly(on: Boolean) {
        viewModelScope.launch { settings.setDownloadsWifiOnly(on) }
    }

    /** Put a saved file's full path on the clipboard — see the Location row in the download menu. */
    fun copyPath(path: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(path, path))
    }

    private val _playing = MutableStateFlow(false)
    /** True once a download has actually started playing, so the screen knows to open the player. */
    val playing: StateFlow<Boolean> = _playing.asStateFlow()

    fun play(download: DownloadEntity) {
        val path = download.filePath ?: return
        viewModelScope.launch {
            _playing.value = vodTuner.playDownload(
                mediaType = download.mediaType,
                itemId = download.itemId,
                filePath = path,
                title = download.title,
                posterUrl = download.posterUrl,
            )
        }
    }

    fun playerOpened() {
        _playing.value = false
    }

    /**
     * **Move** a finished download to wherever the user picked in the system's own save dialog. The
     * app's own folder is invisible to a file manager, so this is how a saved film leaves the app.
     *
     * It used to be a copy, and a copy left the user with two of everything: the row still pointed
     * at the app's hidden copy, so deleting the download in OwnTV freed nothing the user could see
     * and left the exported file behind with nothing tracking it. Now the row **follows the file** —
     * the list, the location row and playback all point at the copy the user chose to keep.
     *
     * The order matters and is deliberate: copy, then take a lasting grant, then re-point the row,
     * and only then delete the original. If the grant is refused the move is **abandoned as a copy**
     * — the row keeps pointing at a file that definitely plays, rather than at one that would stop
     * working the next time the app starts.
     */
    fun export(download: DownloadEntity, target: Uri) {
        val source = MediaTarget.of(context, download.filePath) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val copied = runCatching {
                context.contentResolver.openOutputStream(target)?.use { out ->
                    source.openInput().use { it.copyTo(out) }
                } != null
            }.getOrDefault(false)
            if (!copied) return@launch
            if (!StorageAccess.persistAccess(context, target)) return@launch
            downloadDao.upsert(
                download.copy(filePath = target.toString(), updatedAt = System.currentTimeMillis()),
            )
            source.delete()
        }
    }

    fun retry(download: DownloadEntity) = downloadManager.retry(download)
    fun pause(download: DownloadEntity) = downloadManager.pause(download)
    fun resume(download: DownloadEntity) = downloadManager.resume(download)
    fun delete(download: DownloadEntity) = downloadManager.delete(download)

    private companion object {
        const val SPEED_SAMPLE_MS = 1_000L
    }
}

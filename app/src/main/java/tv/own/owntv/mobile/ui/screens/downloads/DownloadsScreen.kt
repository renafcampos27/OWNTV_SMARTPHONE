package tv.own.owntv.mobile.ui.screens.downloads

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import androidx.compose.ui.text.style.TextOverflow
import tv.own.owntv.core.database.entity.DownloadEntity
import tv.own.owntv.core.model.DownloadStatus
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.storage.MediaFolders
import tv.own.owntv.mobile.ui.components.ExportDocument
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.storage.StorageAccess
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SectionHeader
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.screens.recordings.RecordingsScreen
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.text.NumberFormat

/**
 * Films and episodes saved for watching with the network off.
 *
 * Three chips instead of the television's four headings — a phone list is scrolled, not scanned, and
 * a user opening this screen is looking for one of three things: what is coming down now, what is
 * ready, or what went wrong. Anything still queued counts as coming down.
 */
@Composable
fun DownloadsScreen(
    onPlayerOpened: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DownloadsViewModel = koinViewModel(),
) {
    val downloads by vm.downloads.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val speed by vm.speedMbps.collectAsStateWithLifecycle()
    val root by vm.downloadRoot.collectAsStateWithLifecycle()
    val playing by vm.playing.collectAsStateWithLifecycle()

    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val chosenFolder by vm.chosenFolder.collectAsStateWithLifecycle()
    val folderLost by vm.folderLost.collectAsStateWithLifecycle()

    // The system folder picker. A phone bound for Google Play cannot hold all-files access, so this
    // is the only way it writes to a folder the user chose rather than one Android handed it.
    val chooseFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { tree -> if (tree != null) vm.setDownloadTree(tree) }

    var tab by remember { mutableStateOf(DownloadsTab.LIVE) }
    var menuFor by remember { mutableStateOf<DownloadEntity?>(null) }
    var optionsSheet by remember { mutableStateOf(false) }
    var savingCopyOf by remember { mutableStateOf<DownloadEntity?>(null) }

    val saveCopy = rememberLauncherForActivityResult(
        ExportDocument("video/*"),
    ) { uri ->
        val download = savingCopyOf
        savingCopyOf = null
        if (uri != null && download != null) vm.export(download, uri)
    }

    LaunchedEffect(playing) {
        if (playing) {
            vm.playerOpened()
            onPlayerOpened()
        }
    }

    val shown = downloads.filter { tab.holds(it.mediaType) }
    // The statuses, now headings rather than chips, in the order the television lists them.
    val sections = DownloadSection.entries
        .map { section -> section to shown.filter { section.holds(it.status) } }
        .filter { it.second.isNotEmpty() }

    Column(modifier.fillMaxSize()) {
        StorageHeader(
            storage = storage,
            speedMbps = speed,
            root = root,
            onOpenOptions = { optionsSheet = true },
        )
        FilterChipRow(
            labels = DownloadsTab.entries.map { stringResource(it.labelRes) },
            selectedIndex = DownloadsTab.entries.indexOf(tab),
            onSelect = { tab = DownloadsTab.entries[it] },
        )

        if (tab == DownloadsTab.LIVE) {
            RecordingsScreen(
                onPlayerOpened = onPlayerOpened,
                embedded = true,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (shown.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(MobileDimens.GapLarge),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.content_downloads_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                sections.forEach { (section, items) ->
                    item(key = "header_${section.name}") {
                        SectionHeader(title = stringResource(section.labelRes))
                    }
                    items(items, key = { it.id }) { download ->
                        DownloadRow(
                            download = download,
                            onClick = {
                                if (download.status == DownloadStatus.COMPLETED) vm.play(download)
                                else menuFor = download
                            },
                            onLongClick = { menuFor = download },
                        )
                    }
                }
            }
        }
    }

    menuFor?.let { download ->
        DownloadMenu(
            download = download,
            vm = vm,
            onSaveCopy = {
                savingCopyOf = download
                saveCopy.launch(download.filePath?.substringAfterLast('/') ?: download.title)
            },
            onDismiss = { menuFor = null },
        )
    }

    if (optionsSheet) {
        DownloadOptionsSheet(
            volumes = vm.volumes,
            current = root,
            wifiOnly = wifiOnly,
            chosenFolder = chosenFolder,
            folderLost = folderLost,
            onPick = vm::setDownloadRoot,
            onChooseFolder = {
                // Opens where downloads currently go, when that is somewhere the picker can show.
                chooseFolder.launch(root.takeIf { MediaTarget.isDocument(it) }?.let(Uri::parse))
            },
            onWifiOnly = vm::setWifiOnly,
            onDismiss = { optionsSheet = false },
        )
    }
}

/**
 * The three things this screen keeps, in the order Favourites and History use — and the same three
 * the television shows.
 *
 * They used to be Active / Completed / Failed. A status is not a *kind* of thing: it changes on its
 * own while you watch, so the chip you were looking at empties and the item you were following moves
 * to another one. The statuses are still there, as headings down the list, where they can all be
 * seen at once. Live TV holds recordings: a recording is a download of a live channel.
 */
private enum class DownloadSection(val labelRes: Int) {
    ACTIVE(R.string.content_downloads_active),
    COMPLETED(R.string.content_downloads_completed_group),
    FAILED(R.string.content_downloads_failed_group),
    ;

    /** Queued counts as active: from the user's side it is a download that has not arrived yet. */
    fun holds(status: DownloadStatus): Boolean = when (this) {
        ACTIVE -> status == DownloadStatus.RUNNING || status == DownloadStatus.PAUSED ||
            status == DownloadStatus.QUEUED
        COMPLETED -> status == DownloadStatus.COMPLETED
        FAILED -> status == DownloadStatus.FAILED
    }
}

private enum class DownloadsTab(val labelRes: Int) {
    LIVE(R.string.common_nav_live_tv),
    MOVIES(R.string.common_nav_movies),
    SERIES(R.string.common_nav_series),
    ;

    /**
     * The rule is core's, not this screen's — an EPISODE belongs under Series, and writing that out
     * here is what hid every episode download from this list.
     */
    fun holds(type: MediaType): Boolean = when (this) {
        MOVIES -> MediaFolders.folderFor(type) == MediaFolders.MOVIES
        SERIES -> MediaFolders.folderFor(type) == MediaFolders.SERIES
        LIVE -> false // recordings come from their own screen
    }
}

@Composable
private fun StorageHeader(
    storage: tv.own.owntv.core.download.DownloadStorageInfo?,
    speedMbps: Double,
    root: String,
    onOpenOptions: () -> Unit,
) {
    val unknown = stringResource(R.string.content_downloads_unknown_size)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(
                start = MobileDimens.ScreenPaddingH,
                end = MobileDimens.GapSmall,
                top = MobileDimens.GapSmall,
            ),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.content_downloads_storage),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = storage?.let {
                    stringResource(
                        R.string.content_downloads_storage_free,
                        gigabytes(it.freeBytes, unknown),
                        gigabytes(it.totalBytes, unknown),
                    )
                } ?: unknown,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = MobileDimens.GapSmall),
            )
            if (speedMbps > 0.0) {
                Text(
                    text = stringResource(R.string.player_stream_mbps, decimal(speedMbps)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The screen's only settings control, and it lives here rather than in the top bar:
            // there used to be a gear up there *and* a folder glyph down here, two buttons for two
            // halves of one question. Not a folder glyph either — what it opens is the folder list
            // itself, and a folder that opens folders says nothing.
            IconButton(onClick = onOpenOptions) {
                Icon(
                    imageVector = MobileIcons.Tune,
                    contentDescription = stringResource(R.string.content_downloads_options),
                    // Without a tint this inherits a content colour meant for a filled button and
                    // comes out black on the dark card.
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        storage?.let {
            LinearProgressIndicator(
                progress = { it.usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = MobileDimens.GapSmall, bottom = MobileDimens.GapSmall),
            )
        }
        // The folder everything lands in, read at a glance. It is a fact about the whole screen
        // rather than a control, so it sits with the free-space line instead of behind the button
        // that changes it.
        // A folder the user picked is stored as its `content://…%2F…` URI, which is unreadable and
        // means nothing to anyone looking for their files. Shown decoded, as `OwnTV/Films`.
        root.takeIf { it.isNotBlank() }?.let { StorageAccess.folderLabel(it) ?: it }?.let { path ->
            Text(
                text = path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = MobileDimens.GapSmall, bottom = MobileDimens.GapSmall),
            )
        }
    }
}

@Composable
private fun DownloadRow(download: DownloadEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    val unknown = stringResource(R.string.content_downloads_unknown_size)
    val fraction = if (download.totalBytes > 0) {
        (download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }
    // Two lines under the title, not one: how far it has got, and *where it went*. A row that said
    // only "1.2 GB downloaded" left the user with no idea which folder to look in, which is the one
    // thing a saved file is for. Matches the television's three-line row.
    val location = MediaFolders.crumb(
        download.filePath,
        stringResource(R.string.content_downloads_folder_separator),
    )
    Column {
        MobileListRow(
            title = download.title,
            subtitle = listOfNotNull(statusLine(download, unknown), location).joinToString("\n"),
            subtitleMaxLines = 2,
            leading = {
                Icon(
                    imageVector = MobileIcons.Movie,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailing = {
                if (download.status == DownloadStatus.COMPLETED) {
                    Icon(
                        imageVector = MobileIcons.PlayArrow,
                        contentDescription = stringResource(R.string.content_downloads_play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            onClick = onClick,
            onLongClick = onLongClick,
        )
        if (download.status == DownloadStatus.RUNNING || download.status == DownloadStatus.PAUSED) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MobileDimens.ScreenPaddingH),
            )
        }
    }
}

/** The second line of a row: where it got to, or why it stopped. */
@Composable
private fun statusLine(download: DownloadEntity, unknown: String): String = when (download.status) {
    DownloadStatus.COMPLETED -> stringResource(
        R.string.content_downloads_completed,
        megabytes(download.totalBytes, unknown),
    )
    DownloadStatus.FAILED -> stringResource(R.string.content_downloads_failure_message)
    DownloadStatus.QUEUED -> stringResource(R.string.content_downloads_queued)
    else -> if (download.totalBytes > 0) {
        stringResource(
            R.string.content_downloads_progress,
            ((download.downloadedBytes.toFloat() / download.totalBytes).coerceIn(0f, 1f) * 100).toInt(),
            megabytes(download.downloadedBytes, unknown),
            megabytes(download.totalBytes, unknown),
        )
    } else {
        stringResource(R.string.content_downloads_progress_unknown, megabytes(download.downloadedBytes, unknown))
    }
}

@Composable
private fun DownloadMenu(
    download: DownloadEntity,
    vm: DownloadsViewModel,
    onSaveCopy: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = download.title) {
        when (download.status) {
            DownloadStatus.COMPLETED -> {
                MenuRow(R.string.content_downloads_play, MobileIcons.PlayArrow) {
                    vm.play(download); onDismiss()
                }
                MenuRow(R.string.settings_export, MobileIcons.Save) { onSaveCopy(); onDismiss() }
            }
            DownloadStatus.FAILED -> MenuRow(R.string.common_retry, MobileIcons.Refresh) {
                vm.retry(download); onDismiss()
            }
            DownloadStatus.PAUSED -> MenuRow(R.string.common_resume, MobileIcons.PlayArrow) {
                vm.resume(download); onDismiss()
            }
            else -> MenuRow(R.string.content_downloads_pause, MobileIcons.Pause) {
                vm.pause(download); onDismiss()
            }
        }
        // Where the file actually is, spelled out in full and copied on a tap. When it is in the
        // app's own storage a phone will not open that folder for you — no file manager has browsed
        // it since Android 11 — so the useful act is handing the user the location itself. A folder
        // the user picked IS browsable, and is shown decoded (`Films/OwnTV/x.mkv`) rather than as the
        // `content://…%2F…` URI it is stored as, which is unreadable and useless in a file manager.
        download.filePath?.let { stored ->
            val path = StorageAccess.folderLabel(stored) ?: stored
            HorizontalDivider()
            MobileListRow(
                title = stringResource(R.string.settings_backup_location),
                subtitle = path,
                subtitleMaxLines = 3,
                leading = {
                    Icon(
                        imageVector = MobileIcons.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = { vm.copyPath(path); onDismiss() },
            )
        }
        HorizontalDivider()
        MenuRow(R.string.common_delete, MobileIcons.Delete, destructive = true) {
            vm.delete(download); onDismiss()
        }
    }
}

/**
 * Everything this screen can be told: which volume downloads are written to, and whether they wait
 * for Wi-Fi.
 *
 * One sheet, flat. The folder used to be a second sheet opened from inside the first, which is why
 * picking one appeared to do nothing — two modal sheets swapping in the same frame, over a choice
 * that is usually between a single entry and itself. The volumes are listed here directly, ticked,
 * with the path each one writes to.
 *
 * The list is only the roots the app can write to without asking a permission: a phone has a
 * document picker for everywhere else, and demanding all-files access to save a film is not a trade
 * a user should have to make.
 */
@Composable
private fun DownloadOptionsSheet(
    volumes: List<StorageAccess.StorageRoot>,
    current: String,
    wifiOnly: Boolean,
    /** The folder picked through the system picker, ready to read, or null when none is. */
    chosenFolder: String?,
    /** True when that folder can no longer be written to — revoked permission, or storage removed. */
    folderLost: Boolean,
    onPick: (String) -> Unit,
    onChooseFolder: () -> Unit,
    onWifiOnly: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.content_downloads_options),
    ) {
        // Said before the list rather than after it: the folder being unreachable is the reason the
        // user is most likely here, and a failed download looks identical to a failed network.
        if (folderLost) {
            Text(
                text = stringResource(R.string.content_storage_folder_lost),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    horizontal = MobileDimens.GapLarge,
                    vertical = MobileDimens.GapSmall,
                ),
            )
        }
        volumes.forEach { volume ->
            val path = volume.file.absolutePath
            MobileListRow(
                title = stringResource(
                    if (volume.kind == StorageAccess.RootKind.REMOVABLE) R.string.content_storage_removable
                    else R.string.content_storage_internal,
                ),
                // The path, always — the folder the files land in is the thing being chosen, and a
                // volume label on its own ("sdcard1") does not say where to go and look.
                subtitle = path,
                subtitleMaxLines = 2,
                selected = if (current.isBlank()) volume == volumes.firstOrNull() else current == path,
                leading = {
                    Icon(
                        imageVector = if (volume.kind == StorageAccess.RootKind.REMOVABLE) {
                            MobileIcons.SdStorage
                        } else {
                            MobileIcons.Folder
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = {
                    // An unset folder means the default, which is the first volume in the list.
                    // An unset folder means the default, which is the first volume in the list —
                    // but only while the user has not picked one of their own, which is neither
                    // blank nor equal to any volume's path.
                    val chosen = if (current.isBlank()) volume == volumes.firstOrNull() else current == path
                    if (chosen) {
                        Icon(
                            imageVector = MobileIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = { onPick(path) },
            )
        }
        // Anywhere else on the device. A phone cannot be given all-files access without failing Play
        // review, so the system picker is how it reaches a folder of the user's own choosing — and
        // once picked, that folder is a row of its own so it can be ticked like the volumes above.
        if (chosenFolder != null) {
            MobileListRow(
                title = stringResource(R.string.content_storage_chosen_folder),
                subtitle = chosenFolder,
                subtitleMaxLines = 2,
                selected = !folderLost,
                leading = {
                    Icon(
                        imageVector = MobileIcons.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailing = {
                    if (!folderLost) {
                        Icon(
                            imageVector = MobileIcons.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = onChooseFolder,
            )
        }
        MobileListRow(
            title = stringResource(R.string.content_storage_choose_folder),
            leading = {
                Icon(
                    imageVector = MobileIcons.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onChooseFolder,
        )
        HorizontalDivider()
        SettingRow(
            title = stringResource(R.string.settings_downloads_wifi_only),
            subtitle = stringResource(R.string.settings_downloads_wifi_only_description),
            checked = wifiOnly,
            onCheckedChange = onWifiOnly,
        )
    }
}

@Composable
private fun MenuRow(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    MobileListRow(
        title = stringResource(labelRes),
        leading = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        },
        onClick = onClick,
    )
}

private fun gigabytes(bytes: Long, unknown: String): String =
    if (bytes <= 0) unknown else decimal(bytes / 1_073_741_824.0)

/**
 * A download's size, with its unit. `common_size_mb` carries the unit because a number on its own is
 * not a size — the completed row read "233,8 downloaded", which says nothing at all.
 */
@Composable
private fun megabytes(bytes: Long, unknown: String): String =
    if (bytes <= 0) unknown else stringResource(R.string.common_size_mb, decimal(bytes / 1_048_576.0))

private fun decimal(value: Double): String = NumberFormat.getNumberInstance().apply {
    minimumFractionDigits = 1
    maximumFractionDigits = 1
}.format(value)

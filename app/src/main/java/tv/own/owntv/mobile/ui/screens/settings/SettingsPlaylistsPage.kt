package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.repository.SourceTestResult
import tv.own.owntv.core.setup.detailLines
import tv.own.owntv.core.setup.headline
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.SyncContentTypes
import tv.own.owntv.core.sync.SyncCounts
import tv.own.owntv.core.sync.SyncProgressCounts
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.core.sync.breakdownText
import tv.own.owntv.core.sync.displayText
import tv.own.owntv.core.sync.resyncProgressPercent
import tv.own.owntv.core.sync.work.CatalogSyncState
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.setup.AddSourceForm
import tv.own.owntv.mobile.ui.setup.SourceFormValues
import tv.own.owntv.mobile.ui.setup.SourceKind
import tv.own.owntv.mobile.ui.setup.labelRes
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/**
 * The playlists, one row each, with everything the television's Manage sources screen can do: which
 * one the app is filtered to, when the account runs out, how often it refreshes itself, and what a
 * running import has fetched so far.
 *
 * The television lays five buttons out along each row because a remote can reach them. A thumb
 * cannot, so a row opens the sheet instead and the sheet holds the actions.
 */
@Composable
fun SettingsPlaylistsPage(
    onAddSource: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val defaultId by vm.defaultSourceId.collectAsStateWithLifecycle()
    val expiry by vm.sourceExpiry.collectAsStateWithLifecycle()
    val deleting by vm.deletingSourceIds.collectAsStateWithLifecycle()
    val test by vm.sourceTest.collectAsStateWithLifecycle()
    val playlistRefresh = vm.settings.playlistAutoRefresh.pref(emptyMap())

    var menuSource by remember { mutableStateOf<SourceEntity?>(null) }
    var editSource by remember { mutableStateOf<SourceEntity?>(null) }
    var refreshFor by remember { mutableStateOf<SourceEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<SourceEntity?>(null) }
    // Set while the "this will stop playback and take a while" confirmation is on screen.
    var confirmRetest by remember { mutableStateOf<SourceEntity?>(null) }

    SettingsPage(modifier) {
        settingsNote(R.string.settings_sources_description)
        if (sources.isEmpty()) {
            settingsNote(R.string.settings_sources_empty)
        }
        settingsGroup(key = "add-source") {
            sources.forEach { source ->
                PlaylistRow(
                    source = source,
                    refresh = playlistRefresh[source.id] ?: PlaylistRefresh(),
                    isDefault = source.id == defaultId,
                    expiry = expiry[source.id],
                    isDeleting = source.id in deleting,
                    counts = vm.contentCounts(source.id).collectAsStateWithLifecycle(null).value,
                    syncState = vm.syncState(source.id)
                        .collectAsStateWithLifecycle(CatalogSyncState.Idle).value,
                    onClick = { if (source.id !in deleting) menuSource = source },
                )
            }
            MobileListRow(
                title = stringResource(R.string.settings_sources_add),
                leading = { Icon(MobileIcons.Add, contentDescription = null) },
                onClick = onAddSource,
            )
        }
    }

    menuSource?.let { source ->
        val syncing = vm.syncState(source.id).collectAsStateWithLifecycle(CatalogSyncState.Idle).value.isActive
        MobileBottomSheet(onDismissRequest = { menuSource = null }, title = source.name) {
            SettingRow(
                title = stringResource(R.string.setup_default_playlist),
                subtitle = stringResource(R.string.setup_default_playlist_description),
                checked = source.id == defaultId,
                onCheckedChange = { on -> vm.setDefaultSource(if (on) source.id else -1L) },
            )
            MobileListRow(
                title = stringResource(R.string.settings_sources_edit),
                onClick = { editSource = source; menuSource = null },
            )
            // "Info", not "Test": the expensive measurement now lives behind Re-test inside the
            // sheet, and this answers the question it always really answered — is it alive?
            MobileListRow(
                title = stringResource(R.string.settings_sources_info),
                onClick = { vm.testSource(source); menuSource = null },
            )
            if (syncing) {
                MobileListRow(
                    title = stringResource(R.string.settings_sources_cancel),
                    onClick = { vm.cancelResync(source); menuSource = null },
                )
            } else {
                MobileListRow(
                    title = stringResource(R.string.settings_sources_resync_now_full),
                    subtitle = stringResource(R.string.settings_sources_resync_description),
                    onClick = { vm.resync(source); menuSource = null },
                )
                MobileListRow(
                    title = stringResource(R.string.settings_sources_resync_remove_full),
                    onClick = { vm.resync(source, removeMissing = true); menuSource = null },
                )
            }
            MobileListRow(
                title = stringResource(R.string.setup_auto_refresh),
                subtitle = playlistRefreshLabel(playlistRefresh[source.id] ?: PlaylistRefresh()),
                onClick = { refreshFor = source; menuSource = null },
            )
            MobileListRow(
                title = stringResource(R.string.settings_sources_delete),
                onClick = { confirmDelete = source; menuSource = null },
            )
        }
    }

    editSource?.let { source ->
        EditPlaylistDialog(
            source = source,
            onDismiss = { editSource = null },
            onSave = { v ->
                vm.updateSource(
                    id = source.id,
                    name = v.name,
                    urlOrServer = v.urlOrServer,
                    user = v.username,
                    pass = v.password,
                    userAgent = v.userAgent,
                    httpReferer = v.httpReferer,
                    autoRefresh = v.autoRefresh,
                    mac = v.mac,
                    stalkerSerialNumber = v.serialNumber,
                    stalkerDeviceId = v.deviceId,
                    stalkerDeviceId2 = v.deviceId2,
                    stalkerSignature = v.signature,
                    syncLive = v.live != SyncScopeChoice.Off,
                    syncMovies = v.movies != SyncScopeChoice.Off,
                    syncSeries = v.series != SyncScopeChoice.Off,
                    preferHls = v.preferHls,
                )
                editSource = null
            },
        )
    }

    refreshFor?.let { source ->
        val current = playlistRefresh[source.id] ?: PlaylistRefresh()
        MobileBottomSheet(
            onDismissRequest = { refreshFor = null },
            title = stringResource(R.string.setup_auto_refresh_title),
        ) {
            Text(
                text = stringResource(R.string.setup_auto_refresh_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            )
            PlaylistAutoRefresh.entries.forEach { mode ->
                MobileListRow(
                    title = stringResource(mode.labelRes()),
                    trailing = { RadioButton(selected = mode == current.mode, onClick = null) },
                    onClick = {
                        vm.edit { setPlaylistAutoRefresh(source.id, current.copy(mode = mode)) }
                        refreshFor = null
                    },
                )
            }
        }
    }

    confirmDelete?.let { source ->
        AlertDialog(
            modifier = Modifier.glassDialogWindow(),
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.settings_sources_delete_title, source.name)) },
            text = { Text(stringResource(R.string.settings_sources_delete_message)) },
            confirmButton = {
                TextButton(onClick = { vm.deleteSource(source); confirmDelete = null }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    test?.let { state ->
        MobileBottomSheet(
            onDismissRequest = vm::dismissSourceTest,
            title = stringResource(R.string.settings_sources_test_title),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
                verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                Text(state.sourceName, style = MaterialTheme.typography.titleSmall)
                when (state) {
                    is SettingsViewModel.SourceTestState.Running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.setup_testing))
                    }
                    // The measurement is slow by nature, so it says which stream it is on rather
                    // than spinning silently for two minutes and looking like a hang.
                    is SettingsViewModel.SourceTestState.Measuring -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            stringResource(
                                R.string.settings_sources_probe_running,
                                state.progress.stream,
                                state.progress.maxStreams,
                            ),
                        )
                    }
                    is SettingsViewModel.SourceTestState.Done -> TestReport(state.result, state.limit)
                }
                // While measuring the only honest action is one that abandons it; afterwards, the
                // one that repeats it.
                if (state is SettingsViewModel.SourceTestState.Measuring) {
                    TextButton(onClick = vm::skipConnectionMeasurement) {
                        Text(stringResource(R.string.settings_sources_probe_skip))
                    }
                } else if (state is SettingsViewModel.SourceTestState.Done) {
                    sources.firstOrNull { it.name == state.sourceName }?.let { src ->
                        TextButton(onClick = { confirmRetest = src }) {
                            Text(stringResource(R.string.settings_sources_retest))
                        }
                    }
                }
            }
        }
    }

    // Measuring opens real streams, so the user is told plainly that playback stops and that it is
    // slow, and gets to say no.
    confirmRetest?.let { src ->
        AlertDialog(
            onDismissRequest = { confirmRetest = null },
            title = { Text(stringResource(R.string.settings_sources_probe_title)) },
            text = { Text(stringResource(R.string.settings_sources_probe_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmRetest = null; vm.retestSource(src) }) {
                    Text(stringResource(R.string.settings_sources_retest))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRetest = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/** The test's own verdict and its lines of detail — both sentences are core's, shared with the TV. */
@Composable
private fun TestReport(result: SourceTestResult, limit: tv.own.owntv.core.live.ConnectionLimit?) {
    val res = LocalContext.current.resources
    Text(
        text = result.headline(res),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
    result.detailLines(res, limit).forEach { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One playlist: its name with the badges the television shows beside it, and a details line that
 * turns into live counts while an import is running.
 */
@Composable
private fun PlaylistRow(
    source: SourceEntity,
    refresh: PlaylistRefresh,
    isDefault: Boolean,
    expiry: String?,
    isDeleting: Boolean,
    counts: SyncCounts?,
    syncState: CatalogSyncState,
    onClick: () -> Unit,
) {
    val res = LocalContext.current.resources
    val active = syncState as? CatalogSyncState.Syncing
    val activeCounts = active?.progressCounts(source.type, counts)
    val visibleCounts =
        if (active == null) counts?.breakdownText(res) else activeCounts?.displayText(res)
    // One fact per line. Squeezed onto one, the channel and film counts — the reason to look at this
    // screen at all — fall off the right-hand edge behind the URL.
    val details = buildList {
        add(stringResource(source.type.labelRes(), source.url))
        if (!visibleCounts.isNullOrBlank()) add(visibleCounts)
        else if (active != null) add(stringResource(R.string.settings_sources_preparing_detail))
        if (!expiry.isNullOrBlank()) add(stringResource(R.string.settings_sources_expiry, expiry))
        if (refresh.mode != PlaylistAutoRefresh.OFF) {
            add(stringResource(R.string.settings_sources_auto_refresh, playlistRefreshLabel(refresh)))
        }
    }
    MobileListRow(
        title = source.name,
        subtitle = details.joinToString("\n"),
        subtitleMaxLines = details.size,
        trailing = {
            val badge = when {
                isDeleting -> stringResource(R.string.settings_sources_deleting)
                active != null -> resyncProgressPercent(active.baseItemCount, active.totalProcessed)
                    ?.let { stringResource(R.string.sync_progress_percent, it) }
                    ?: stringResource(R.string.sync_progress_syncing)
                isDefault -> stringResource(R.string.settings_sources_default)
                else -> null
            }
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        },
        onClick = onClick,
        onLongClick = onClick,
    )
}

/** The setup form again, full screen, with the playlist's own answers already in it. */
@Composable
private fun EditPlaylistDialog(
    source: SourceEntity,
    onDismiss: () -> Unit,
    onSave: (SourceFormValues) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = MobileDimens.ScreenPaddingH),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(MobileIcons.Close, contentDescription = stringResource(R.string.settings_close))
                    }
                }
                AddSourceForm(
                    onStartXtream = { _, _, _, _, _, _, _, _, _, _, _ -> },
                    onStartM3u = { _, _, _, _, _ -> },
                    onStartStalker = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
                    initial = source.toFormValues(),
                    onSave = onSave,
                )
            }
        }
    }
}

private fun SourceEntity.toFormValues(): SourceFormValues {
    fun scope(on: Boolean) = if (on) SyncScopeChoice.Now else SyncScopeChoice.Off
    return SourceFormValues(
        kind = when (type) {
            SourceType.XTREAM -> SourceKind.XTREAM
            SourceType.STALKER -> SourceKind.STALKER
            else -> SourceKind.M3U
        },
        name = name,
        urlOrServer = url,
        username = username.orEmpty(),
        mac = mac.orEmpty(),
        serialNumber = stalkerSerialNumber.orEmpty(),
        deviceId = stalkerDeviceId.orEmpty(),
        deviceId2 = stalkerDeviceId2.orEmpty(),
        signature = stalkerSignature.orEmpty(),
        userAgent = userAgent.orEmpty(),
        httpReferer = httpReferer.orEmpty(),
        live = scope(syncLive),
        movies = scope(syncMovies),
        series = scope(syncSeries),
        preferHls = preferHls,
    )
}

/**
 * What a run has fetched so far. A section that is not being fetched shows what is already stored,
 * so switching from "syncing films" to "syncing series" does not blank the film count.
 */
private fun CatalogSyncState.Syncing.progressCounts(
    type: SourceType,
    stored: SyncCounts?,
): SyncProgressCounts? {
    if (type == SourceType.LOCAL_BACKUP) return null
    fun visible(active: Boolean, processed: Int, storedCount: Int) = if (active) processed else storedCount
    val live = visible(liveActive, liveProcessed, stored?.channels ?: 0)
    val movies = visible(moviesActive, moviesProcessed, stored?.movies ?: 0)
    val series = visible(seriesActive, seriesProcessed, stored?.series ?: 0)
    // An M3U is one flat list; splitting it into three counts would invent sections it does not have.
    val counts = if (type == SourceType.M3U) {
        SyncProgressCounts(live, 0, 0, liveActive = true, moviesActive = false, seriesActive = false)
    } else {
        SyncProgressCounts(
            live = live,
            movies = movies,
            series = series,
            liveActive = liveActive || live > 0,
            moviesActive = moviesActive || movies > 0,
            seriesActive = seriesActive || series > 0,
        )
    }
    return counts.takeIf { it.hasItems }
}

@Composable
internal fun playlistRefreshLabel(refresh: PlaylistRefresh): String =
    if (refresh.mode == PlaylistAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        stringResource(refresh.mode.labelRes())
    }

internal fun SourceType.labelRes(): Int = when (this) {
    SourceType.M3U -> R.string.settings_sources_type_m3u
    SourceType.XTREAM -> R.string.settings_sources_type_xtream
    SourceType.STALKER -> R.string.settings_sources_type_stalker
    SourceType.LOCAL_BACKUP -> R.string.settings_sources_backup
}

package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.epg.EpgSource
import tv.own.owntv.core.settings.EpgAutoRefresh
import tv.own.owntv.core.settings.EpgRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.sync.work.EpgSyncState
import tv.own.owntv.core.util.classifySyncFailure
import tv.own.owntv.core.setup.displayText
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/**
 * The XMLTV feeds that fill the guide, each with what it actually holds: how many channels, how many
 * programmes, and how many of your own channels can be replayed from the archive.
 *
 * A feed can also lend the guide its own channel logos, which is usually the better picture — a
 * playlist's logo is whatever the provider happened to attach.
 */
@Composable
fun SettingsEpgSourcesPage(
    modifier: Modifier = Modifier,
    vm: EpgSourcesViewModel = koinViewModel(),
) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val autoRefresh by vm.autoRefresh.collectAsStateWithLifecycle()
    val useLogos by vm.useLogos.collectAsStateWithLifecycle()
    val deleting by vm.deletingIds.collectAsStateWithLifecycle()

    var menuSource by remember { mutableStateOf<EpgSource?>(null) }
    var editSource by remember { mutableStateOf<EpgSource?>(null) }
    var addSource by remember { mutableStateOf(false) }
    var refreshFor by remember { mutableStateOf<EpgSource?>(null) }
    var manualDaysFor by remember { mutableStateOf<EpgSource?>(null) }
    var confirmDelete by remember { mutableStateOf<EpgSource?>(null) }

    SettingsPage(modifier) {
        settingsNote(R.string.settings_epg_sources_description)
        if (sources.isEmpty()) {
            settingsNote(R.string.settings_epg_sources_empty)
        }
        settingsGroup(key = "add-epg") {
            sources.forEach { source ->
                EpgRow(
                    source = source,
                    autoRefresh = autoRefresh[source.id] ?: EpgRefresh.OFF,
                    counts = { vm.counts(source.id) },
                    syncState = vm.observeSync(source.id)
                        .collectAsStateWithLifecycle(EpgSyncState.Idle).value,
                    isDeleting = source.id in deleting,
                    onClick = { if (source.id !in deleting) menuSource = source },
                )
            }
            MobileListRow(
                title = stringResource(R.string.settings_epg_sources_add),
                leading = { Icon(MobileIcons.Add, contentDescription = null) },
                onClick = { addSource = true },
            )
        }
    }

    menuSource?.let { source ->
        val syncing = vm.observeSync(source.id).collectAsStateWithLifecycle(EpgSyncState.Idle).value.isActive
        MobileBottomSheet(onDismissRequest = { menuSource = null }, title = source.name) {
            MobileListRow(
                title = stringResource(
                    if (syncing) R.string.settings_sources_cancel else R.string.settings_sync_now,
                ),
                onClick = {
                    if (syncing) vm.cancelSync(source) else vm.resync(source)
                    menuSource = null
                },
            )
            MobileListRow(
                title = stringResource(R.string.settings_sources_edit),
                onClick = { editSource = source; menuSource = null },
            )
            SettingRow(
                title = stringResource(R.string.settings_epg_sources_use_logos),
                subtitle = stringResource(R.string.settings_epg_sources_logos_description),
                checked = source.id in useLogos,
                onCheckedChange = { vm.setUseLogos(source, it) },
            )
            MobileListRow(
                title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
                subtitle = epgRefreshLabel(autoRefresh[source.id] ?: EpgRefresh.OFF),
                onClick = { refreshFor = source; menuSource = null },
            )
            MobileListRow(
                title = stringResource(R.string.settings_sources_delete),
                onClick = { confirmDelete = source; menuSource = null },
            )
        }
    }

    refreshFor?.let { source ->
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
            choices = EpgAutoRefresh.entries.map { SettingsChoice(it, epgAutoRefreshLabel(it)) },
            selected = (autoRefresh[source.id] ?: EpgRefresh.OFF).mode,
            onSelect = { mode ->
                // "Every N days" needs the N, so it opens a second sheet rather than guessing one.
                if (mode == EpgAutoRefresh.MANUAL) manualDaysFor = source else vm.setAutoRefresh(source, EpgRefresh(mode))
            },
            onDismiss = { refreshFor = null },
        )
    }

    manualDaysFor?.let { source ->
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_sources_refresh_days_title),
            choices = PlaylistRefresh.PRESET_MANUAL_DAYS.map {
                SettingsChoice(it, pluralStringResource(R.plurals.settings_sources_refresh_days, it, it))
            },
            selected = (autoRefresh[source.id] ?: EpgRefresh.OFF).manualDays,
            onSelect = { vm.setAutoRefresh(source, EpgRefresh(EpgAutoRefresh.MANUAL, it)) },
            onDismiss = { manualDaysFor = null },
        )
    }

    confirmDelete?.let { source ->
        AlertDialog(
            modifier = Modifier.glassDialogWindow(),
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.settings_epg_sources_delete_title, source.name)) },
            text = { Text(stringResource(R.string.settings_epg_sources_delete_message)) },
            confirmButton = {
                TextButton(onClick = { vm.delete(source); confirmDelete = null }) {
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

    if (addSource) {
        EpgSourceSheet(
            initial = null,
            initialAutoRefresh = EpgRefresh.OFF,
            initialUseLogos = false,
            playlistOptions = { vm.playlistEpgOptions() },
            onDismiss = { addSource = false },
            onSave = { name, url, agent, refresh, logos ->
                vm.add(name, url, agent, refresh, logos)
                addSource = false
            },
        )
    }

    editSource?.let { source ->
        EpgSourceSheet(
            initial = source,
            initialAutoRefresh = autoRefresh[source.id] ?: EpgRefresh.OFF,
            initialUseLogos = source.id in useLogos,
            playlistOptions = { vm.playlistEpgOptions() },
            onDismiss = { editSource = null },
            onSave = { name, url, agent, refresh, logos ->
                vm.update(source, name, url, agent)
                vm.setAutoRefresh(source, refresh)
                vm.setUseLogos(source, logos)
                editSource = null
            },
        )
    }
}

/**
 * One feed's row. The status line is the whole point of it: a feed that says "synced" but holds no
 * programmes is broken, and only the counts show that.
 */
@Composable
private fun EpgRow(
    source: EpgSource,
    autoRefresh: EpgRefresh,
    counts: suspend () -> Triple<Int, Int, Int>,
    syncState: EpgSyncState,
    isDeleting: Boolean,
    onClick: () -> Unit,
) {
    val count by produceState<Triple<Int, Int, Int>?>(null, source.id, source.lastSyncAt, source.lastError) {
        value = runCatching { counts() }.getOrNull()
    }
    val res = LocalContext.current.resources
    val active = syncState as? EpgSyncState.Syncing
    val catchupNote = count?.third?.takeIf { it > 0 }?.let {
        pluralStringResource(R.plurals.settings_epg_sources_catchup, it, it)
    }
    val status = when {
        active != null -> when {
            active.programmes > 0 -> stringResource(
                R.string.settings_epg_sources_status_count,
                pluralStringResource(
                    R.plurals.settings_epg_sources_status_count_channels, active.channels, active.channels,
                ),
                pluralStringResource(
                    R.plurals.settings_epg_sources_status_count_programmes, active.programmes, active.programmes,
                ),
            )
            active.channels > 0 -> pluralStringResource(
                R.plurals.settings_epg_sources_status_count_channels, active.channels, active.channels,
            )
            else -> stringResource(R.string.settings_epg_sources_connecting)
        }
        source.lastError != null -> stringResource(
            R.string.settings_epg_sources_error,
            classifySyncFailure(source.lastError, online = true).displayText(res),
        )
        count != null && count!!.second > 0 -> {
            val line = stringResource(
                R.string.settings_epg_sources_status_count,
                pluralStringResource(
                    R.plurals.settings_epg_sources_status_count_channels, count!!.first, count!!.first,
                ),
                pluralStringResource(
                    R.plurals.settings_epg_sources_status_count_programmes, count!!.second, count!!.second,
                ),
            )
            if (catchupNote != null) {
                stringResource(R.string.settings_epg_sources_status_count_with_catchup, line, catchupNote)
            } else {
                line
            }
        }
        source.lastSyncAt != null -> catchupNote?.let {
            stringResource(R.string.settings_epg_sources_status_synced_with_catchup, it)
        } ?: stringResource(R.string.settings_epg_sources_status_synced)
        else -> stringResource(R.string.settings_epg_sources_not_synced)
    }
    val percent = active?.let {
        if (it.baseProgrammes > 0 && it.programmes > 0) {
            ((it.programmes.toLong() * 100) / it.baseProgrammes).toInt().coerceAtMost(99)
        } else {
            null
        }
    }
    // A Stalker portal's own guide has no address to show — it comes through the portal session
    // rather than being downloaded — so the row says what it is instead of printing the internal
    // marker that stands in for its URL.
    val address = if (tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(source.url) != null) {
        stringResource(R.string.settings_epg_sources_portal_guide)
    } else {
        source.url
    }
    MobileListRow(
        title = source.name,
        // The address and the counts each get a line; on one, the counts fall off the edge.
        subtitle = address + "\n" + status,
        subtitleMaxLines = 3,
        trailing = {
            val badge = when {
                isDeleting -> stringResource(R.string.settings_epg_sources_deleting)
                active != null -> percent
                    ?.let { stringResource(R.string.settings_epg_sources_syncing_percent, it) }
                    ?: stringResource(R.string.settings_epg_sources_syncing_label)
                autoRefresh.mode != EpgAutoRefresh.OFF -> stringResource(
                    R.string.settings_sources_auto_refresh, epgRefreshLabel(autoRefresh),
                )
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

/** Add or edit a feed: the same four questions the television asks, on one sheet. */
@Composable
private fun EpgSourceSheet(
    initial: EpgSource?,
    initialAutoRefresh: EpgRefresh,
    initialUseLogos: Boolean,
    playlistOptions: suspend () -> List<EpgSourcesViewModel.PlaylistEpg>,
    onDismiss: () -> Unit,
    onSave: (String, String, String?, EpgRefresh, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var url by remember { mutableStateOf(initial?.url.orEmpty()) }
    var agent by remember { mutableStateOf(initial?.userAgent.orEmpty()) }
    var refresh by remember { mutableStateOf(initialAutoRefresh) }
    var logos by remember { mutableStateOf(initialUseLogos) }
    var showRefresh by remember { mutableStateOf(false) }
    var showManualDays by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(
            if (initial == null) R.string.settings_epg_sources_add else R.string.settings_epg_sources_edit,
        ),
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = sheetListHeight())
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            MobileTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.settings_epg_sources_name),
                placeholder = stringResource(R.string.settings_epg_sources_name_hint),
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            MobileTextField(
                value = url,
                onValueChange = { url = it },
                label = stringResource(R.string.settings_epg_sources_url),
                placeholder = stringResource(R.string.settings_epg_sources_url_hint),
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            MobileButton(
                text = stringResource(R.string.settings_epg_sources_fill_playlist),
                onClick = { showPlaylists = true },
                style = MobileButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
            MobileTextField(
                value = agent,
                onValueChange = { agent = it },
                label = stringResource(R.string.settings_epg_sources_user_agent),
                placeholder = stringResource(R.string.settings_epg_sources_user_agent_hint),
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
            MobileListRow(
                title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
                subtitle = epgRefreshLabel(refresh),
                onClick = { showRefresh = true },
            )
            SettingRow(
                title = stringResource(R.string.settings_epg_sources_use_logos),
                subtitle = stringResource(R.string.settings_epg_sources_logos_description),
                checked = logos,
                onCheckedChange = { logos = it },
            )
            MobileButton(
                text = stringResource(
                    if (initial == null) R.string.settings_epg_sources_add_sync
                    else R.string.settings_epg_sources_save_sync,
                ),
                onClick = { onSave(name.trim(), url.trim(), agent.trim().ifBlank { null }, refresh, logos) },
                enabled = url.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showRefresh) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_epg_sources_auto_refresh_title),
            choices = EpgAutoRefresh.entries.map { SettingsChoice(it, epgAutoRefreshLabel(it)) },
            selected = refresh.mode,
            onSelect = { mode ->
                if (mode == EpgAutoRefresh.MANUAL) showManualDays = true else refresh = EpgRefresh(mode)
            },
            onDismiss = { showRefresh = false },
        )
    }

    if (showManualDays) {
        SettingsChoiceSheet(
            title = stringResource(R.string.settings_sources_refresh_days_title),
            choices = PlaylistRefresh.PRESET_MANUAL_DAYS.map {
                SettingsChoice(it, pluralStringResource(R.plurals.settings_sources_refresh_days, it, it))
            },
            selected = refresh.manualDays,
            onSelect = { refresh = EpgRefresh(EpgAutoRefresh.MANUAL, it) },
            onDismiss = { showManualDays = false },
        )
    }

    if (showPlaylists) {
        val options by produceState<List<EpgSourcesViewModel.PlaylistEpg>?>(null) {
            value = runCatching { playlistOptions() }.getOrDefault(emptyList())
        }
        MobileBottomSheet(
            onDismissRequest = { showPlaylists = false },
            title = stringResource(R.string.settings_epg_sources_fill_playlist),
        ) {
            val list = options
            if (list != null && list.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_epg_sources_none_playlist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
                )
            }
            list.orEmpty().forEach { option ->
                // A portal guide has no address to show, and nothing to fill in — say what it is, and
                // add it straight away rather than putting an internal marker in a URL box.
                val portal = tv.own.owntv.core.repository.EpgRepository.stalkerSourceIdOf(option.url) != null
                MobileListRow(
                    title = option.name,
                    subtitle = if (portal) stringResource(R.string.settings_epg_sources_portal_guide) else option.url,
                    trailing = { RadioButton(selected = option.url == url, onClick = null) },
                    onClick = {
                        showPlaylists = false
                        if (portal) {
                            // Nothing to type: save it as it stands and close the sheet.
                            onSave(option.name, option.url, agent.trim().takeIf { it.isNotBlank() }, refresh, logos)
                            onDismiss()
                        } else {
                            url = option.url
                            if (name.isBlank()) name = option.name
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun epgRefreshLabel(refresh: EpgRefresh): String =
    if (refresh.mode == EpgAutoRefresh.MANUAL) {
        pluralStringResource(R.plurals.settings_sources_refresh_days, refresh.manualDays, refresh.manualDays)
    } else {
        epgAutoRefreshLabel(refresh.mode)
    }

@Composable
private fun epgAutoRefreshLabel(mode: EpgAutoRefresh): String = stringResource(
    when (mode) {
        EpgAutoRefresh.OFF -> R.string.settings_sources_refresh_off
        EpgAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
        EpgAutoRefresh.HOURS_1 -> R.string.settings_epg_refresh_1h
        EpgAutoRefresh.HOURS_3 -> R.string.settings_epg_refresh_3h
        EpgAutoRefresh.HOURS_6 -> R.string.settings_epg_refresh_6h
        EpgAutoRefresh.HOURS_12 -> R.string.settings_epg_refresh_12h
        EpgAutoRefresh.HOURS_24 -> R.string.settings_epg_refresh_24h
        EpgAutoRefresh.HOURS_48 -> R.string.settings_epg_refresh_48h
        EpgAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
    },
)

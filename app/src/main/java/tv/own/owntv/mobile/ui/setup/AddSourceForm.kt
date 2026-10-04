package tv.own.owntv.mobile.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import tv.own.owntv.core.settings.PlaylistAutoRefresh
import tv.own.owntv.core.settings.PlaylistRefresh
import tv.own.owntv.core.setup.MAG_USER_AGENTS
import tv.own.owntv.core.stalker.StalkerClient
import tv.own.owntv.core.sync.SyncScopeChoice
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileSwitch
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.theme.MobileDimens

/** Which kind of provider the form is asking about. */
enum class SourceKind { XTREAM, M3U, STALKER }

/**
 * Everything the form holds, so editing an existing playlist can fill it and take it back whole.
 * Only the edit path uses it; adding still calls the three start callbacks, one per kind.
 */
data class SourceFormValues(
    val kind: SourceKind,
    val name: String = "",
    val urlOrServer: String = "",
    val username: String = "",
    val password: String = "",
    val mac: String = "",
    val serialNumber: String = "",
    val deviceId: String = "",
    val deviceId2: String = "",
    val signature: String = "",
    val userAgent: String = "",
    val httpReferer: String = "",
    val autoRefresh: PlaylistRefresh = PlaylistRefresh(),
    val live: SyncScopeChoice = SyncScopeChoice.Now,
    val movies: SyncScopeChoice = SyncScopeChoice.Off,
    val series: SyncScopeChoice = SyncScopeChoice.Off,
    val preferHls: Boolean = true,
)

/**
 * One playlist's details, as one scrolling page.
 *
 * The television asks the same questions across several popups because a remote control cannot
 * scroll a long form comfortably. A phone is the opposite: a popup would put the keyboard over the
 * field being typed into, so everything — including the optional half — is on this page, and the
 * page scrolls under the keyboard.
 *
 * Passing [initial] and [onSave] turns the same page into the edit form for a playlist that already
 * exists: the kind chips go away, because a playlist cannot change what kind of provider it is, and
 * a blank password means "keep the stored one" rather than "no password".
 */
@Composable
fun AddSourceForm(
    onStartXtream: (
        name: String, server: String, username: String, password: String, userAgent: String,
        httpReferer: String, autoRefresh: PlaylistRefresh, live: SyncScopeChoice, movies: SyncScopeChoice,
        series: SyncScopeChoice, preferHls: Boolean,
    ) -> Unit,
    onStartM3u: (name: String, url: String, userAgent: String, httpReferer: String, autoRefresh: PlaylistRefresh) -> Unit,
    onStartStalker: (
        name: String, portalUrl: String, mac: String, serialNumber: String, deviceId: String,
        deviceId2: String, signature: String, userAgent: String, httpReferer: String, autoRefresh: PlaylistRefresh,
        live: SyncScopeChoice, movies: SyncScopeChoice, series: SyncScopeChoice,
    ) -> Unit,
    modifier: Modifier = Modifier,
    initial: SourceFormValues? = null,
    onSave: ((SourceFormValues) -> Unit)? = null,
) {
    val editing = onSave != null
    var kind by rememberSaveable { mutableStateOf(initial?.kind ?: SourceKind.XTREAM) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var server by rememberSaveable {
        mutableStateOf(if (initial?.kind == SourceKind.XTREAM) initial.urlOrServer else "")
    }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf("") }
    var m3uUrl by rememberSaveable {
        mutableStateOf(if (initial?.kind == SourceKind.M3U) initial.urlOrServer else "")
    }
    var portalUrl by rememberSaveable {
        mutableStateOf(if (initial?.kind == SourceKind.STALKER) initial.urlOrServer else "")
    }
    var mac by rememberSaveable { mutableStateOf(initial?.mac.orEmpty()) }
    var serialNumber by rememberSaveable { mutableStateOf(initial?.serialNumber.orEmpty()) }
    var deviceId by rememberSaveable { mutableStateOf(initial?.deviceId.orEmpty()) }
    var deviceId2 by rememberSaveable { mutableStateOf(initial?.deviceId2.orEmpty()) }
    var signature by rememberSaveable { mutableStateOf(initial?.signature.orEmpty()) }
    var userAgent by rememberSaveable { mutableStateOf(initial?.userAgent.orEmpty()) }
    var httpReferer by rememberSaveable { mutableStateOf(initial?.httpReferer.orEmpty()) }
    var preferHls by rememberSaveable { mutableStateOf(initial?.preferHls ?: true) }
    var refreshMode by rememberSaveable { mutableStateOf(initial?.autoRefresh?.mode ?: PlaylistAutoRefresh.OFF) }
    var manualDays by rememberSaveable {
        mutableStateOf((initial?.autoRefresh ?: PlaylistRefresh()).manualDays.toString())
    }
    // This variant imports Live TV. Existing catalogue rows are not deleted by hiding their UI.
    var syncLive by rememberSaveable { mutableStateOf(initial?.live ?: SyncScopeChoice.Now) }
    val syncMovies = SyncScopeChoice.Off
    val syncSeries = SyncScopeChoice.Off
    var showRefreshSheet by remember { mutableStateOf(false) }
    var showDeviceSheet by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickPlaylist = rememberPlaylistFilePicker { uri ->
        scope.launch {
            copyPickedFile(context, uri, java.io.File(context.filesDir, PLAYLIST_DIR))?.let { file ->
                m3uUrl = file.absolutePath
                if (name.isBlank()) name = file.nameWithoutExtension
            }
        }
    }

    val macValid = StalkerClient.canonicalizeMac(mac) != null
    val autoRefresh = PlaylistRefresh(refreshMode, manualDays.toIntOrNull() ?: PlaylistRefresh().manualDays)
    val hasAnySection = syncLive != SyncScopeChoice.Off ||
        syncMovies != SyncScopeChoice.Off ||
        syncSeries != SyncScopeChoice.Off
    val canStart = when (kind) {
        SourceKind.XTREAM ->
            // On an edit the password field starts empty and left empty keeps the stored one.
            server.isNotBlank() && username.isNotBlank() && (password.isNotBlank() || editing) && hasAnySection
        SourceKind.M3U -> m3uUrl.isNotBlank()
        SourceKind.STALKER -> StalkerClient.isValidPortalUrl(portalUrl) && macValid && hasAnySection
    }
    fun values() = SourceFormValues(
        kind = kind,
        name = name,
        urlOrServer = when (kind) {
            SourceKind.XTREAM -> server
            SourceKind.M3U -> m3uUrl
            SourceKind.STALKER -> portalUrl
        },
        username = username,
        password = password,
        mac = mac,
        serialNumber = serialNumber,
        deviceId = deviceId,
        deviceId2 = deviceId2,
        signature = signature,
        userAgent = userAgent,
        httpReferer = httpReferer,
        autoRefresh = autoRefresh,
        live = syncLive,
        movies = syncMovies,
        series = syncSeries,
        preferHls = preferHls,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.ScreenPaddingV),
        verticalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium),
    ) {
        if (!editing) {
            Text(
                text = stringResource(R.string.setup_byo_source_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilterChipRow(
                labels = listOf(
                    stringResource(R.string.setup_xtream),
                    stringResource(R.string.setup_m3u),
                    stringResource(R.string.setup_stalker_mac),
                ),
                selectedIndex = kind.ordinal,
                onSelect = { index ->
                    kind = SourceKind.entries[index]
                },
            )
        }
        MobileTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.setup_source_name_optional),
            placeholder = stringResource(R.string.setup_default_iptv),
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth(),
        )

        when (kind) {
            SourceKind.XTREAM -> {
                MobileTextField(
                    value = server,
                    onValueChange = { server = it },
                    label = stringResource(R.string.setup_server_url),
                    placeholder = stringResource(R.string.setup_server_example),
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = stringResource(R.string.setup_username),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(
                        if (editing) R.string.setup_password_keep else R.string.setup_password,
                    ),
                    isPassword = true,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(
                    label = stringResource(R.string.setup_prefer_hls_live_tv),
                    description = stringResource(R.string.setup_prefer_hls_description),
                    checked = preferHls,
                    onCheckedChange = { preferHls = it },
                )
            }
            SourceKind.M3U -> {
                MobileTextField(
                    value = m3uUrl,
                    onValueChange = { m3uUrl = it },
                    label = stringResource(R.string.setup_playlist_url_local_file),
                    placeholder = stringResource(R.string.setup_playlist_example),
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileButton(
                    text = stringResource(R.string.setup_local_file_choose),
                    onClick = { pickPlaylist() },
                    style = MobileButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SourceKind.STALKER -> {
                MobileTextField(
                    value = portalUrl,
                    onValueChange = { portalUrl = it },
                    label = stringResource(R.string.setup_portal_url),
                    placeholder = stringResource(R.string.setup_portal_example),
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileTextField(
                    value = mac,
                    onValueChange = { mac = it },
                    label = stringResource(R.string.setup_mac_address),
                    placeholder = stringResource(R.string.setup_mac_example),
                    imeAction = ImeAction.Next,
                    isError = mac.isNotBlank() && !macValid,
                    supportingText = stringResource(R.string.setup_mac_invalid).takeIf { mac.isNotBlank() && !macValid },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.setup_stalker_advanced_identity),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MobileTextField(
                    value = serialNumber,
                    onValueChange = { serialNumber = it },
                    label = stringResource(R.string.setup_stalker_serial_number_optional),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = stringResource(R.string.setup_stalker_device_id_optional),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileTextField(
                    value = deviceId2,
                    onValueChange = { deviceId2 = it },
                    label = stringResource(R.string.setup_stalker_device_id2_optional),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileTextField(
                    value = signature,
                    onValueChange = { signature = it },
                    label = stringResource(R.string.setup_stalker_signature_optional),
                    imeAction = ImeAction.Next,
                    modifier = Modifier.fillMaxWidth(),
                )
                MobileListRow(
                    title = stringResource(R.string.setup_device_model_preset),
                    subtitle = stringResource(
                        MAG_USER_AGENTS.firstOrNull { it.userAgent == userAgent }?.labelRes
                            ?: MAG_USER_AGENTS.first().labelRes,
                    ),
                    onClick = { showDeviceSheet = true },
                )
            }
        }

        MobileTextField(
            value = userAgent,
            onValueChange = { userAgent = it },
            label = stringResource(R.string.setup_user_agent_optional),
            placeholder = stringResource(R.string.setup_user_agent_example),
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth(),
        )
        MobileTextField(
            value = httpReferer,
            onValueChange = { httpReferer = it },
            label = stringResource(R.string.setup_referer_optional),
            placeholder = stringResource(R.string.setup_referer_example),
            modifier = Modifier.fillMaxWidth(),
        )
        MobileListRow(
            title = stringResource(R.string.setup_auto_refresh),
            subtitle = stringResource(refreshMode.labelRes()),
            onClick = { showRefreshSheet = true },
        )
        if (refreshMode == PlaylistAutoRefresh.MANUAL) {
            MobileTextField(
                value = manualDays,
                onValueChange = { manualDays = it.filter(Char::isDigit).take(3) },
                label = stringResource(R.string.settings_sources_refresh_days_title),
                supportingText = stringResource(R.string.settings_sources_refresh_days_hint),
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (kind != SourceKind.M3U) {
            Text(
                text = stringResource(R.string.setup_what_to_sync),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.setup_sync_choices),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ScopeRow(
                label = stringResource(R.string.setup_live_tv),
                description = stringResource(R.string.setup_channels_categories),
                value = syncLive,
                onChange = { syncLive = it },
            )
        }

        MobileButton(
            text = stringResource(if (editing) R.string.common_save else R.string.setup_start_import),
            onClick = {
                onSave?.let { save -> save(values()); return@MobileButton }
                when (kind) {
                    SourceKind.XTREAM -> onStartXtream(
                        name, server, username, password, userAgent, httpReferer, autoRefresh,
                        syncLive, syncMovies, syncSeries, preferHls,
                    )
                    SourceKind.M3U -> onStartM3u(name, m3uUrl, userAgent, httpReferer, autoRefresh)
                    SourceKind.STALKER -> onStartStalker(
                        name, portalUrl, mac, serialNumber, deviceId, deviceId2, signature,
                        userAgent, httpReferer, autoRefresh, syncLive, syncMovies, syncSeries,
                    )
                }
            },
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showRefreshSheet) {
        MobileBottomSheet(
            onDismissRequest = { showRefreshSheet = false },
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
                    trailing = { RadioButton(selected = mode == refreshMode, onClick = null) },
                    onClick = { refreshMode = mode; showRefreshSheet = false },
                )
            }
        }
    }
    if (showDeviceSheet) {
        MobileBottomSheet(
            onDismissRequest = { showDeviceSheet = false },
            title = stringResource(R.string.setup_device_model_preset_title),
        ) {
            MAG_USER_AGENTS.forEach { preset ->
                MobileListRow(
                    title = stringResource(preset.labelRes),
                    trailing = { RadioButton(selected = preset.userAgent == userAgent, onClick = null) },
                    onClick = { userAgent = preset.userAgent; showDeviceSheet = false },
                )
            }
        }
    }
}

/** Now, Later or not at all — one row per catalogue, cycled by tapping it. */
@Composable
private fun ScopeRow(
    label: String,
    description: String,
    value: SyncScopeChoice,
    onChange: (SyncScopeChoice) -> Unit,
) {
    val options = listOf(SyncScopeChoice.Now, SyncScopeChoice.Later, SyncScopeChoice.Off)
    val valueLabel = stringResource(
        when (value) {
            SyncScopeChoice.Now -> R.string.setup_now
            SyncScopeChoice.Later -> R.string.setup_later
            SyncScopeChoice.Off -> R.string.setup_off
        },
    )
    MobileListRow(
        title = label,
        subtitle = description,
        // No "◀ value ▶" arrows: those tell a remote control which way to press, and a thumb just
        // taps the row.
        trailing = {
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = { onChange(options[(options.indexOf(value) + 1) % options.size]) },
    )
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MobileSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

internal fun PlaylistAutoRefresh.labelRes(): Int = when (this) {
    PlaylistAutoRefresh.OFF -> R.string.settings_sources_refresh_off
    PlaylistAutoRefresh.STARTUP -> R.string.settings_sources_refresh_startup
    PlaylistAutoRefresh.HOURS_6 -> R.string.settings_sources_refresh_6h
    PlaylistAutoRefresh.HOURS_12 -> R.string.settings_sources_refresh_12h
    PlaylistAutoRefresh.MANUAL -> R.string.settings_sources_refresh_manual
}

/** Where a playlist picked from the phone's storage is kept. */
private const val PLAYLIST_DIR = "playlists"

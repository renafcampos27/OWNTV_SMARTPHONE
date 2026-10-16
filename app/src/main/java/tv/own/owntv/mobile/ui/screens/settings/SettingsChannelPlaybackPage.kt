package tv.own.owntv.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.settings.*
import tv.own.owntv.core.R as CoreR
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet

@Composable
fun SettingsChannelPlaybackPage(vm: SettingsViewModel = koinViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val storedConfigs by vm.channelPlaybackConfigs.collectAsStateWithLifecycle()
    val configs = storedConfigs.orEmpty()
    var sourceId by rememberSaveable { mutableStateOf<Long?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var customizedOnly by rememberSaveable { mutableStateOf(false) }
    var channelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val channel by produceState<ChannelEntity?>(null, channelId) {
        value = channelId?.let { vm.playbackChannelById(it) }
        if (channelId != null && value == null) channelId = null
    }
    var refresh by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val selected = channel
    BackHandler(sourceId != null && selected == null) { if (channelId != null) channelId = null else sourceId = null }
    if (storedConfigs == null || (channelId != null && selected == null)) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        return
    }
    if (selected != null) {
        ChannelOptionsEditor(selected, configs.firstOrNull { it.channel.matches(selected) }?.options ?: ChannelPlaybackOptions(), vm,
            saving = saving, saveFailed = saveFailed,
            onSave = { if (!saving) { saving = true; saveFailed = false; vm.saveChannelPlayback(selected, it) { success ->
                saving = false; saveFailed = !success; if (success) channelId = null
            } } }, onCancel = { if (!saving) { channelId = null; saveFailed = false } })
        return
    }
    val candidates by produceState<List<ChannelEntity>?>(null, sourceId, query, refresh, sources, customizedOnly, configs) {
        value = null
        failed = false
        sourceId?.let {
            delay(200)
            try { value = vm.searchPlaybackChannels(it, query, customizedOnly) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true; value = emptyList() }
        }
    }
    val rows = remember(candidates, configs, customizedOnly) {
        candidates?.filter { row -> !customizedOnly || configs.any { it.channel.matches(row) } }
    }
    SettingsPage {
        item { Text(stringResource(R.string.own_channel_hint)) }
        if (sourceId == null) {
            if (sources.none { it.syncLive }) item { Text(stringResource(R.string.own_no_playlists)) }
            sources.filter { it.syncLive }.forEach { source ->
                item(key = source.id) { SettingRow(title = source.name, onClick = { sourceId = source.id; query = "" }) }
            }
        } else {
            item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.own_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { SettingRow(title = stringResource(R.string.own_customized_only), checked = customizedOnly, onCheckedChange = { customizedOnly = it }) }
            when {
                rows == null -> item { Column { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.own_loading_channels)) } }
                failed -> item { Column { Text(stringResource(R.string.own_channel_load_error)); TextButton(onClick = { refresh++ }) { Text(stringResource(R.string.own_retry)) } } }
                rows.isEmpty() -> item { Text(stringResource(R.string.own_empty_channels)) }
                else -> rows.forEach { row -> item(key = row.id) { SettingRow(title = row.name,
                    subtitle = if (configs.any { it.channel.matches(row) }) stringResource(R.string.own_customized_badge) else null,
                    onClick = { channelId = row.id; saveFailed = false }) } }
            }
            item { SettingRow(title = stringResource(R.string.own_choose_playlist), onClick = { sourceId = null }) }
        }
    }
}

@Composable
private fun ChannelOptionsEditor(channel: ChannelEntity, initial: ChannelPlaybackOptions, vm: SettingsViewModel,
    saving: Boolean, saveFailed: Boolean, onSave: (ChannelPlaybackOptions) -> Unit, onCancel: () -> Unit) {
    val saver = remember(channel.id) { Saver<ChannelPlaybackOptions, String>(
        save = { ChannelPlaybackConfig.encode(listOf(ChannelPlaybackConfig(ManualTsChannel.of(channel), it))) },
        restore = { ChannelPlaybackConfig.decode(it).firstOrNull()?.options ?: ChannelPlaybackOptions() }) }
    var draft by rememberSaveable(channel.id, stateSaver = saver) { mutableStateOf(initial) }
    var discard by remember { mutableStateOf(false) }
    var engineSheet by remember { mutableStateOf(false) }
    var formatSheet by remember { mutableStateOf(false) }
    val leave = { if (!saving) { if (draft != initial) discard = true else onCancel() } }
    BackHandler { leave() }
    val s = vm.settings
    val sources by vm.sources.collectAsStateWithLifecycle()
    val source = sources.firstOrNull { it.id == channel.sourceId }
    val globalEngine = s.liveEnginePreference.pref(EnginePreference.EXO_FIRST)
    val strict = s.liveHlsOnly.pref(true)
    val globalReserve = s.liveReserveConfiguration.pref(LiveReserveConfiguration(null, 2))
    val globalLatency = s.liveBufferSeconds.pref(null)
    val globalPreroll = s.livePrerollSecs.pref(0)
    val globalSoftware = s.softwareAudio.pref(false)
    val globalAudioDelay = s.audioDelayMs.pref(0)
    val profileId = s.activeProfileId.pref(-1L)
    val rememberedDelay by produceState<Int?>(null, channel.id, profileId) {
        value = vm.rememberedPlaybackAudioDelay(channel)
    }
    val inherit = stringResource(R.string.own_inherit)
    val general = stringResource(R.string.own_global_origin)
    val playlist = stringResource(R.string.own_playlist_origin)
    val automatic = stringResource(R.string.own_auto)
    val defaults = stringResource(R.string.own_engine_default)
    val exo = stringResource(R.string.own_engine_exo)
    val mpv = stringResource(R.string.own_engine_mpv)
    val hls = stringResource(R.string.own_format_hls)
    val ts = stringResource(R.string.own_format_ts)
    fun engineLabel(engine: EnginePreference) = when (engine) {
        EnginePreference.EXO_ONLY -> exo
        EnginePreference.MPV_ONLY -> mpv
        else -> automatic
    }
    val inheritedEngine = draft.copy(engine = null).effectiveEngine(
        source?.liveEnginePreference?.let { name -> EnginePreference.entries.firstOrNull { it.name == name } } ?: globalEngine,
        draft.hlsOnly(strict), draft.directTs())
    val reserve = if (source?.liveReserveMode != null) LiveBuffer.effectiveSeconds(LiveLatency.fromName(source.liveReserveMode), source.liveReserveCustomSecs) else globalReserve.seconds
    val latency = if (source?.liveLatencyMode != null) LiveBuffer.effectiveSeconds(LiveLatency.fromName(source.liveLatencyMode), source.liveLatencyCustomSecs) else globalLatency
    fun origin(hasOverride: Boolean) = if (hasOverride) playlist else general
    SettingsPage {
        item { Text(channel.name) }
        if (saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (saveFailed) item { Text(stringResource(R.string.own_channel_save_error)) }
        settingsGroup("channel-engine") {
            SettingRow(title = stringResource(CoreR.string.settings_live_tv_player), value = draft.engine?.let(::engineLabel)
                ?: stringResource(R.string.own_inherited_value, engineLabel(inheritedEngine), origin(source?.liveEnginePreference != null)), onClick = { engineSheet = true })
            SettingRow(title = stringResource(R.string.own_format), value = when (draft.format) {
                ChannelStreamFormat.HLS -> hls; ChannelStreamFormat.TS -> ts; ChannelStreamFormat.AUTO -> automatic
                null -> stringResource(R.string.own_inherited_value, if (strict) hls else automatic, general)
            }, onClick = { formatSheet = true })
            OptionalNumber(R.string.own_reserve, draft.reserveSecs, 1..60, inherited = stringResource(CoreR.string.settings_live_buffer_seconds, reserve ?: LiveBuffer.BALANCED_SECS), origin = origin(source?.liveReserveMode != null)) { draft = draft.copy(reserveSecs = it) }
            OptionalNumber(R.string.own_extra, draft.extraSecs, 0..10, inherited = stringResource(CoreR.string.settings_live_buffer_seconds, source?.liveReserveExtraSecs?.takeIf { it >= 0 } ?: globalReserve.extraSecs), origin = origin(source?.liveReserveExtraSecs?.let { it >= 0 } == true)) { draft = draft.copy(extraSecs = it) }
            OptionalNumber(R.string.own_preroll, draft.prerollSecs, 0..10, inherited = stringResource(CoreR.string.settings_live_buffer_seconds, source?.livePrerollSecs?.takeIf { it >= 0 } ?: globalPreroll), origin = origin(source?.livePrerollSecs?.let { it >= 0 } == true)) { draft = draft.copy(prerollSecs = it) }
            OptionalNumber(R.string.own_latency, draft.latencySecs, 1..60, inherited = latency?.let { stringResource(CoreR.string.settings_live_buffer_seconds, it) } ?: defaults, origin = origin(source?.liveLatencyMode != null)) { draft = draft.copy(latencySecs = it) }
            OptionalNumber(R.string.own_audio_delay, draft.audioDelayMs, -5000..5000, 50, stringResource(R.string.own_unit_ms), stringResource(R.string.own_number_unit, rememberedDelay ?: globalAudioDelay, stringResource(R.string.own_unit_ms)), if (rememberedDelay != null) stringResource(R.string.own_remembered_origin) else general) { draft = draft.copy(audioDelayMs = it) }
            OptionalFlag(R.string.own_software_audio, draft.softwareAudio, globalSoftware) { draft = draft.copy(softwareAudio = it) }
        }
        item { Text(stringResource(R.string.own_compat_hint)) }
        settingsGroup("channel-hls") {
            OptionalFlag(R.string.own_hls_boundaries, draft.hlsDetectAccessUnits, false, false) { draft = draft.copy(hlsDetectAccessUnits = it) }
            OptionalFlag(R.string.own_hls_keyframes, draft.hlsAllowNonIdrKeyframes, false, false) { draft = draft.copy(hlsAllowNonIdrKeyframes = it) }
            OptionalFlag(R.string.own_hls_segments, draft.hlsPrepareFromSegments, false, false) { draft = draft.copy(hlsPrepareFromSegments = it) }
        }
        settingsGroup("save") {
            SettingRow(title = stringResource(R.string.own_save), enabled = !saving, onClick = { onSave(draft.normalized()) })
            SettingRow(title = stringResource(R.string.own_reset), onClick = { draft = ChannelPlaybackOptions() })
            SettingRow(title = stringResource(R.string.own_cancel), onClick = leave)
        }
    }
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text(stringResource(R.string.own_unsaved_title)) }, text = { Text(stringResource(R.string.own_unsaved_note)) },
        confirmButton = { TextButton(onClick = { onSave(draft.normalized()) }) { Text(stringResource(R.string.own_save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.own_discard)) } })
    if (engineSheet) SettingsChoiceSheet(title = stringResource(CoreR.string.settings_live_tv_player), choices = listOf(SettingsChoice<EnginePreference?>(null, inherit)) + listOf(EnginePreference.EXO_ONLY, EnginePreference.MPV_ONLY).map { SettingsChoice<EnginePreference?>(it, engineLabel(it)) }, selected = draft.engine, onSelect = { draft = draft.copy(engine = it) }, onDismiss = { engineSheet = false })
    if (formatSheet) SettingsChoiceSheet(title = stringResource(R.string.own_format), choices = listOf(SettingsChoice<ChannelStreamFormat?>(null, inherit)) + ChannelStreamFormat.entries.map { SettingsChoice<ChannelStreamFormat?>(it, if (it == ChannelStreamFormat.AUTO) automatic else it.name) }, selected = draft.format, onSelect = { draft = draft.copy(format = it) }, onDismiss = { formatSheet = false })
}

@Composable
private fun OptionalFlag(titleRes: Int, value: Boolean?, inherited: Boolean, audio: Boolean = true, onValue: (Boolean?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val inherit = stringResource(R.string.own_inherit)
    val yes = stringResource(if (audio) R.string.own_software_audio else R.string.own_enabled)
    val no = stringResource(if (audio) R.string.own_hardware else R.string.own_disabled)
    SettingRow(title = stringResource(titleRes), value = value?.let { if (it) yes else no }
        ?: stringResource(R.string.own_inherited_value, if (inherited) yes else no, stringResource(R.string.own_global_origin)), onClick = { open = true })
    if (open) SettingsChoiceSheet(title = stringResource(titleRes), choices = listOf(SettingsChoice<Boolean?>(null, inherit), SettingsChoice<Boolean?>(false, no), SettingsChoice<Boolean?>(true, yes)), selected = value, onSelect = onValue, onDismiss = { open = false })
}

@Composable
private fun OptionalNumber(titleRes: Int, value: Int?, range: IntRange, step: Int = 1, unit: String = "", inherited: String, origin: String, onValue: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var text by remember(value, open) { mutableStateOf((value ?: range.first).toString()) }
    val actualUnit = unit.ifBlank { stringResource(R.string.own_unit_seconds) }
    val parsed = text.toIntOrNull()?.takeIf { it in range }
    SettingRow(title = stringResource(titleRes), value = value?.let { stringResource(R.string.own_number_unit, it, actualUnit) } ?: stringResource(R.string.own_inherited_value, inherited, origin), onClick = { open = true })
    if (open) MobileBottomSheet(title = stringResource(titleRes), onDismissRequest = { open = false }) {
        OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = if (range.first < 0) KeyboardType.Text else KeyboardType.Number), isError = parsed == null,
            label = { Text(stringResource(titleRes)) }, supportingText = { Text(stringResource(R.string.own_number_hint, range.first, range.last, actualUnit)) }, modifier = Modifier.fillMaxWidth())
        Row {
            TextButton(onClick = { text = ((parsed ?: range.first) - step).coerceIn(range).toString() }) { Text(stringResource(R.string.own_decrease)) }
            TextButton(onClick = { text = ((parsed ?: range.first) + step).coerceIn(range).toString() }) { Text(stringResource(R.string.own_increase)) }
            TextButton(onClick = { onValue(null); open = false }) { Text(stringResource(R.string.own_inherit)) }
            TextButton(enabled = parsed != null, onClick = { onValue(parsed); open = false }) { Text(stringResource(R.string.own_save)) }
        }
    }
}

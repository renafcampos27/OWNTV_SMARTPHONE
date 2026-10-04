package tv.own.owntv.mobile.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.player.EnginePreference
import tv.own.owntv.core.settings.ChannelPlaybackOptions
import tv.own.owntv.core.settings.ChannelStreamFormat
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow

@Composable
fun SettingsChannelPlaybackPage(vm: SettingsViewModel = koinViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val configs by vm.channelPlaybackConfigs.collectAsStateWithLifecycle()
    var sourceId by remember { mutableStateOf<Long?>(null) }
    var query by remember { mutableStateOf("") }
    var channel by remember { mutableStateOf<ChannelEntity?>(null) }
    val selected = channel
    BackHandler(sourceId != null || selected != null) { if (selected != null) channel = null else sourceId = null }
    if (selected != null) {
        ChannelOptionsEditor(selected, configs.firstOrNull { it.channel.matches(selected) }?.options ?: ChannelPlaybackOptions(), onSave = { vm.saveChannelPlayback(selected, it); channel = null }, onCancel = { channel = null })
        return
    }
    val candidates by produceState<List<ChannelEntity>?>(null, sourceId, query) {
        value = null
        sourceId?.let { delay(200); value = vm.searchPlaybackChannels(it, query) }
    }
    SettingsPage {
        item { Text(stringResource(R.string.own_channel_hint)) }
        if (sourceId == null) {
            sources.filter { it.syncLive }.forEach { source ->
                item(key = source.id) { SettingRow(title = source.name, onClick = { sourceId = source.id; query = "" }) }
            }
        } else {
            item { OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.own_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            candidates.orEmpty().forEach { item ->
                item(key = item.id) { SettingRow(title = item.name, onClick = { channel = item }) }
            }
            item { SettingRow(title = stringResource(R.string.own_choose_playlist), onClick = { sourceId = null }) }
        }
    }
}

@Composable
private fun ChannelOptionsEditor(channel: ChannelEntity, initial: ChannelPlaybackOptions, onSave: (ChannelPlaybackOptions) -> Unit, onCancel: () -> Unit) {
    var draft by remember(channel.id) { mutableStateOf(initial) }
    var engineSheet by remember { mutableStateOf(false) }
    var formatSheet by remember { mutableStateOf(false) }
    val inherit = stringResource(R.string.own_inherit)
    fun engineLabel(engine: EnginePreference?) = engine?.name ?: inherit
    SettingsPage {
        item { Text(channel.name) }
        settingsGroup("channel-engine") {
            SettingRow(title = stringResource(R.string.settings_live_tv_player), value = engineLabel(draft.engine), onClick = { engineSheet = true })
            SettingRow(title = stringResource(R.string.own_format), value = draft.format?.name ?: inherit, onClick = { formatSheet = true })
            OptionalNumber(R.string.own_reserve, draft.reserveSecs, 1..60) { draft = draft.copy(reserveSecs = it) }
            OptionalNumber(R.string.own_extra, draft.extraSecs, 0..10) { draft = draft.copy(extraSecs = it) }
            OptionalNumber(R.string.own_preroll, draft.prerollSecs, 0..10) { draft = draft.copy(prerollSecs = it) }
            OptionalNumber(R.string.own_latency, draft.latencySecs, 1..60) { draft = draft.copy(latencySecs = it) }
            OptionalNumber(R.string.own_audio_delay, draft.audioDelayMs, -5000..5000, 50) { draft = draft.copy(audioDelayMs = it) }
            OptionalFlag(R.string.own_software_audio, draft.softwareAudio) { draft = draft.copy(softwareAudio = it) }
        }
        item { Text(stringResource(R.string.own_compat_hint)) }
        settingsGroup("channel-hls") {
            SettingRow(title = stringResource(R.string.own_hls_boundaries), checked = draft.hlsDetectAccessUnits == true, onCheckedChange = { draft = draft.copy(hlsDetectAccessUnits = it) })
            SettingRow(title = stringResource(R.string.own_hls_keyframes), checked = draft.hlsAllowNonIdrKeyframes == true, onCheckedChange = { draft = draft.copy(hlsAllowNonIdrKeyframes = it) })
            SettingRow(title = stringResource(R.string.own_hls_segments), checked = draft.hlsPrepareFromSegments == true, onCheckedChange = { draft = draft.copy(hlsPrepareFromSegments = it) })
        }
        settingsGroup("save") {
            SettingRow(title = stringResource(R.string.own_save), onClick = { onSave(draft.normalized()) })
            SettingRow(title = stringResource(R.string.own_reset), onClick = { onSave(ChannelPlaybackOptions()) })
            SettingRow(title = stringResource(R.string.own_cancel), onClick = onCancel)
        }
    }
    if (engineSheet) SettingsChoiceSheet(title = stringResource(R.string.settings_live_tv_player), choices = listOf(SettingsChoice<EnginePreference?>(null, inherit)) + listOf(EnginePreference.EXO_ONLY, EnginePreference.MPV_ONLY).map { SettingsChoice<EnginePreference?>(it, it.name) }, selected = draft.engine, onSelect = { draft = draft.copy(engine = it) }, onDismiss = { engineSheet = false })
    if (formatSheet) SettingsChoiceSheet(title = stringResource(R.string.own_format), choices = listOf(SettingsChoice<ChannelStreamFormat?>(null, inherit)) + ChannelStreamFormat.entries.map { SettingsChoice<ChannelStreamFormat?>(it, it.name) }, selected = draft.format, onSelect = { draft = draft.copy(format = it) }, onDismiss = { formatSheet = false })
}

@Composable
private fun OptionalFlag(titleRes: Int, value: Boolean?, onValue: (Boolean?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val inherit = stringResource(R.string.own_inherit)
    val software = stringResource(R.string.own_software_audio)
    val hardware = stringResource(R.string.own_hardware)
    SettingRow(title = stringResource(titleRes), value = if (value == null) inherit else if (value) software else hardware, onClick = { open = true })
    if (open) SettingsChoiceSheet(title = stringResource(titleRes), choices = listOf(SettingsChoice<Boolean?>(null, inherit), SettingsChoice<Boolean?>(false, hardware), SettingsChoice<Boolean?>(true, software)), selected = value, onSelect = onValue, onDismiss = { open = false })
}

@Composable
private fun OptionalNumber(titleRes: Int, value: Int?, range: IntRange, step: Int = 1, onValue: (Int?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val inherit = stringResource(R.string.own_inherit)
    SettingRow(title = stringResource(titleRes), value = value?.toString() ?: inherit, onClick = { open = true })
    if (open) SettingsChoiceSheet(title = stringResource(titleRes), choices = listOf(SettingsChoice<Int?>(null, inherit)) + (range step step).map { SettingsChoice<Int?>(it, it.toString()) }, selected = value, onSelect = onValue, onDismiss = { open = false })
}

package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.settings.LiveLatency
import tv.own.owntv.core.settings.LiveBuffer
import tv.own.owntv.core.settings.DecoderQueueing
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.SettingRow

@Composable
fun SettingsLiveCustomPage(vm: SettingsViewModel = koinViewModel()) {
    val s = vm.settings
    val simple by vm.simpleMode.collectAsStateWithLifecycle()
    val groups by vm.channelVersionSettings.collectAsStateWithLifecycle()
    val hls = s.liveHlsOnly.pref(true)
    val software = s.softwareAudio.pref(false)
    val queue = s.decoderQueueing.pref(DecoderQueueing.AUTO)
    val reserve = s.liveReserveCustomSecs.pref(LiveBuffer.CUSTOM_DEFAULT)
    val reserveMode = LiveLatency.fromName(s.liveReserveMode.pref(LiveLatency.BALANCED.name))
    val sources by vm.sources.collectAsStateWithLifecycle()
    var sourcePicker by remember { mutableStateOf(false) }
    var sourceId by remember { mutableStateOf<Long?>(null) }
    var reserveModePicker by remember { mutableStateOf(false) }
    val extra = s.liveReserveExtraSecs.pref(2)
    val guideDelay = s.liveGuideDelayMs.pref(3000)
    val shift = s.catchupRequestShiftMinutes.pref(0)
    var decoderSheet by remember { mutableStateOf(false) }
    SettingsPage {
        settingsGroup("compatibility") {
            SettingRow(title = stringResource(R.string.own_hls_only), subtitle = stringResource(R.string.own_hls_hint), checked = hls, onCheckedChange = { vm.edit { setLiveHlsOnly(it) } })
            SettingRow(title = stringResource(R.string.own_software_audio), checked = software, onCheckedChange = { vm.edit { setSoftwareAudio(it) } })
            SettingRow(title = stringResource(R.string.own_decoder_queue), value = stringResource(queue.labelRes()), onClick = { decoderSheet = true })
            SettingRow(title = stringResource(R.string.own_reserve_mode), value = stringResource(reserveMode.reserveLabelRes()), onClick = { reserveModePicker = true })
            SettingRow(title = stringResource(R.string.own_reserve_source), onClick = { sourcePicker = true })
            if (reserveMode == LiveLatency.CUSTOM) SettingsSlider(title = stringResource(R.string.own_reserve), value = reserve, range = 1..60, valueLabel = stringResource(R.string.settings_live_buffer_seconds, reserve), onValueChange = { vm.edit { setLiveReserveRange(it, it + extra) } })
            SettingsSlider(title = stringResource(R.string.own_extra), value = extra, range = 0..10, valueLabel = stringResource(R.string.settings_live_buffer_seconds, extra), onValueChange = { vm.edit { setLiveReserveExtraSecs(it) } })
            SettingsSlider(title = stringResource(R.string.own_shift), subtitle = stringResource(R.string.own_shift_hint), value = shift, range = -720..840, steps = 103, valueLabel = stringResource(R.string.own_minutes_value, shift), onValueChange = { vm.edit { setCatchupRequestShiftMinutes(it / 15 * 15) } })
        }
        settingsGroup("simple-mode") {
            SettingRow(title = stringResource(R.string.own_guide_delay), checked = guideDelay > 0, onCheckedChange = { vm.edit { setLiveGuideDelayMs(if (it) 3000 else 0) } })
            SettingRow(title = stringResource(R.string.own_hide_categories), checked = simple.hideCategories, onCheckedChange = vm::setHideCategories)
            SettingRow(title = stringResource(R.string.own_hide_navigation), checked = simple.hideSidebar, onCheckedChange = vm::setHideSidebar)
            SettingRow(title = stringResource(R.string.own_group), checked = groups.groupChannelVersions, onCheckedChange = vm::setGroupChannelVersions)
            SettingRow(title = stringResource(R.string.own_priority), subtitle = stringResource(R.string.own_priority_hint), checked = groups.prioritizeChannelVersions, onCheckedChange = vm::setPrioritizeChannelVersions)
            SettingRow(title = stringResource(R.string.own_recovery), checked = simple.channelRecovery, onCheckedChange = vm::setChannelRecovery)
            SettingsSlider(title = stringResource(R.string.own_recovery_time), value = simple.recoveryTimeoutSeconds, range = 1..60, valueLabel = stringResource(R.string.settings_live_buffer_seconds, simple.recoveryTimeoutSeconds), onValueChange = vm::setRecoveryTimeout)
        }
    }
    val source = sources.firstOrNull { it.id == sourceId }
    if (sourcePicker && source == null) SettingsChoiceSheet(title = stringResource(R.string.own_reserve_source), choices = sources.filter { it.syncLive }.map { SettingsChoice<Long?>(it.id, it.name) }, selected = null, onSelect = { sourceId = it }, onDismiss = { if (sourceId == null) sourcePicker = false })
    if (source != null) SourceReserveSheet(source, reserve, extra, onSave = { mode, seconds, margin -> vm.setSourceReserve(source.id, mode, seconds, margin); sourceId = null; sourcePicker = false }, onDismiss = { sourceId = null; sourcePicker = false })
    if (reserveModePicker) SettingsChoiceSheet(title = stringResource(R.string.own_reserve_mode), choices = LiveLatency.entries.map { SettingsChoice(it, stringResource(it.reserveLabelRes())) }, selected = reserveMode, onSelect = { vm.edit { setLiveReserveMode(it.name) } }, onDismiss = { reserveModePicker = false })
    if (decoderSheet) SettingsChoiceSheet(title = stringResource(R.string.own_decoder_queue), choices = DecoderQueueing.entries.map { SettingsChoice(it, stringResource(it.labelRes())) }, selected = queue, onSelect = { vm.edit { setDecoderQueueing(it) } }, onDismiss = { decoderSheet = false })
}

@Composable
private fun SourceReserveSheet(source: tv.own.owntv.core.database.entity.SourceEntity, globalSeconds: Int, globalExtra: Int, onSave: (String?, Int, Int) -> Unit, onDismiss: () -> Unit) {
    var mode by remember(source.id) { mutableStateOf(source.liveReserveMode) }
    var seconds by remember(source.id) { mutableStateOf(source.liveReserveCustomSecs.takeIf { it in 1..60 } ?: globalSeconds) }
    var extra by remember(source.id) { mutableStateOf(source.liveReserveExtraSecs.takeIf { it in 0..10 } ?: globalExtra) }
    var modePicker by remember { mutableStateOf(false) }
    tv.own.owntv.mobile.ui.components.MobileBottomSheet(title = source.name, onDismissRequest = onDismiss) {
        SettingRow(title = stringResource(R.string.own_reserve_mode), value = if (mode == null) stringResource(R.string.own_inherit) else stringResource(LiveLatency.fromName(mode).reserveLabelRes()), onClick = { modePicker = true })
        if (mode == LiveLatency.CUSTOM.name) SettingsSlider(title = stringResource(R.string.own_reserve), value = seconds, range = 1..60, valueLabel = stringResource(R.string.settings_live_buffer_seconds, seconds), onValueChange = { seconds = it })
        if (mode != null) SettingsSlider(title = stringResource(R.string.own_extra), value = extra, range = 0..10, valueLabel = stringResource(R.string.settings_live_buffer_seconds, extra), onValueChange = { extra = it })
        SettingRow(title = stringResource(R.string.own_save), onClick = { onSave(mode, seconds, extra) })
    }
    if (modePicker) SettingsChoiceSheet(title = stringResource(R.string.own_reserve_mode), choices = listOf(SettingsChoice<String?>(null, stringResource(R.string.own_inherit))) + LiveLatency.entries.map { SettingsChoice<String?>(it.name, stringResource(it.reserveLabelRes())) }, selected = mode, onSelect = { mode = it }, onDismiss = { modePicker = false })
}

private fun LiveLatency.reserveLabelRes() = when (this) {
    LiveLatency.LOW -> R.string.settings_live_latency_low
    LiveLatency.BALANCED -> R.string.settings_live_latency_balanced
    LiveLatency.STABLE -> R.string.settings_live_latency_stable
    LiveLatency.CUSTOM -> R.string.settings_live_latency_custom
}
private fun DecoderQueueing.labelRes() = when (this) {
    DecoderQueueing.AUTO -> R.string.own_auto
    DecoderQueueing.ASYNCHRONOUS -> R.string.settings_decoder_queueing_async
    DecoderQueueing.SYNCHRONOUS -> R.string.settings_decoder_queueing_sync
}

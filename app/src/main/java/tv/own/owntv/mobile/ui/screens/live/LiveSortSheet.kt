package tv.own.owntv.mobile.ui.screens.live

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow

internal fun SettingsRepository.SortMode.liveLabelRes(): Int = when (this) {
    SettingsRepository.SortMode.ALPHA -> R.string.own_channel_sort_name
    SettingsRepository.SortMode.CUSTOM -> R.string.own_channel_sort_custom
    else -> R.string.own_channel_sort_provider
}

@Composable
internal fun LiveSortSheet(sort: SettingsRepository.SortMode, editable: Boolean,
    onSelect: (SettingsRepository.SortMode) -> Unit, onEdit: () -> Unit,
    onRestore: () -> Unit, onDismiss: () -> Unit) {
    MobileBottomSheet(onDismissRequest=onDismiss, title=stringResource(R.string.own_channel_sort_title)) {
        listOf(SettingsRepository.SortMode.ALPHA, SettingsRepository.SortMode.PLAYLIST, SettingsRepository.SortMode.CUSTOM).forEach { mode ->
            MobileListRow(title=stringResource(mode.liveLabelRes()),
                subtitle=if (mode == SettingsRepository.SortMode.PLAYLIST) stringResource(R.string.own_channel_sort_provider_hint) else null,
                leading={ RadioButton(selected=sort == mode, onClick={onSelect(mode)}) },
                onClick={onSelect(mode)})
        }
        if (editable) {
            HorizontalDivider()
            MobileListRow(title=stringResource(R.string.own_channel_sort_edit),onClick=onEdit)
            MobileListRow(title=stringResource(R.string.own_channel_sort_restore),onClick=onRestore)
        }
    }
}

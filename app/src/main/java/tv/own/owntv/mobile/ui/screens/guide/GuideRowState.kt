package tv.own.owntv.mobile.ui.screens.guide

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

internal sealed interface GuideRowState {
    data object Loading : GuideRowState
    data class Data(val rows: List<EpgProgrammeEntity>) : GuideRowState
    data object Empty : GuideRowState
    data object Error : GuideRowState
}

@Composable
internal fun rememberGuideRow(vm: GuideViewModel, channel: ChannelEntity, window: GuideWindow, revision: Int, retry: Int): State<GuideRowState> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<GuideRowState>(GuideRowState.Loading, vm, channel, window, revision, retry, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            value = vm.cachedRow(channel.id)?.let { if (it.isEmpty()) GuideRowState.Empty else GuideRowState.Data(it) } ?: GuideRowState.Loading
            try {
                val rows = vm.row(channel)
                value = if (rows.isEmpty()) GuideRowState.Empty else GuideRowState.Data(rows)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { value = GuideRowState.Error }
        }
    }
}

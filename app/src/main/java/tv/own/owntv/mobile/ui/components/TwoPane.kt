package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * A list beside what it opens — Live TV, Library and Settings at expanded width.
 *
 * The list keeps a share rather than a fixed width, so the detail takes the whole of the extra on a
 * large tablet instead of stretching one column of rows across it. Both panes fill the height:
 * neither is a card, because the page they sit on is already the app's plate.
 *
 * [listShare] is the one thing that differs between the three callers. A list of rows needs about a
 * third; Library's poster grid needs over half, or the thing the screen exists for becomes two
 * columns of thumbnails beside a preview of one of them.
 */
@Composable
fun TwoPane(
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    listShare: Float = LIST_SHARE,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapMedium),
    ) {
        Box(Modifier.weight(listShare).fillMaxHeight()) { list() }
        Box(Modifier.weight(1f - listShare).fillMaxHeight()) { detail() }
    }
}

/** A grid pane: more than half, because the grid is what the screen is for. */
const val GRID_LIST_SHARE = 0.55f

private const val LIST_SHARE = 0.38f

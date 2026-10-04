package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.mobile.R

/**
 * Hide or move a category without leaving the screen it is on — a long press on its chip.
 *
 * The same four moves and the same wording as Settings → Customize, because it is the same edit:
 * both write the profile's category order, so a change here shows there and the other way round.
 */
@Composable
fun BrowseCategorySheet(
    title: String,
    onHide: () -> Unit,
    onMove: (MoveKind) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        MobileListRow(
            title = stringResource(R.string.settings_customize_hide),
            onClick = { onDismiss(); onHide() },
        )
        listOf(
            R.string.settings_customize_move_top to MoveKind.TOP,
            R.string.settings_row_menu_move_up to MoveKind.UP,
            R.string.settings_row_menu_move_down to MoveKind.DOWN,
            R.string.settings_customize_move_bottom to MoveKind.BOTTOM,
        ).forEach { (labelRes, kind) ->
            MobileListRow(
                title = stringResource(labelRes),
                onClick = { onDismiss(); onMove(kind) },
            )
        }
    }
}

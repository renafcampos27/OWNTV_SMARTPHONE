package tv.own.owntv.mobile.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R

/** The item a long press was made on, wherever it was made. */
data class ContentTarget(val type: MediaType, val id: Long, val title: String)

/**
 * The short long-press menu: everything an item can do to itself, and nothing that needs the list it
 * would normally sit in — reordering a folder from a Home rail has no folder to reorder.
 *
 * The keys are the ones the rest of the app uses, so the order the user arranged in Settings still
 * applies here.
 */
@Composable
fun ContentActionsMenu(
    target: ContentTarget,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit,
    onHide: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = buildList {
        add(
            SheetAction(
                key = "favourite",
                label = stringResource(
                    if (isFavorite) R.string.content_remove_favourite else R.string.content_add_favourite,
                ),
                icon = if (isFavorite) MobileIcons.Star else MobileIcons.StarBorder,
                group = 0,
                onClick = onToggleFavorite,
            ),
        )
        if (target.type != MediaType.LIVE) {
            add(
                SheetAction(
                    key = "download",
                    label = stringResource(
                        if (target.type == MediaType.MOVIE) R.string.content_download
                        else R.string.content_download_all_episodes,
                    ),
                    icon = MobileIcons.Download,
                    group = 1,
                    onClick = onDownload,
                ),
            )
        }
        add(
            SheetAction(
                key = "hide",
                label = stringResource(
                    if (target.type == MediaType.LIVE) R.string.content_hide_channel else R.string.common_hide,
                ),
                icon = MobileIcons.VisibilityOff,
                destructive = true,
                group = 2,
                onClick = onHide,
            ),
        )
    }

    ContentMenuSheet(
        menu = when (target.type) {
            MediaType.LIVE -> ContentMenu.LIVE
            MediaType.MOVIE -> ContentMenu.MOVIE
            else -> ContentMenu.SERIES
        },
        title = target.title,
        actions = actions,
        onDismiss = onDismiss,
    )
}

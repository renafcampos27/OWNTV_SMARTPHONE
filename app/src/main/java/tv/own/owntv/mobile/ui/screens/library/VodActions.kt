package tv.own.owntv.mobile.ui.screens.library

import tv.own.owntv.mobile.ui.components.MobileIcons
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ContentMenuSheet
import tv.own.owntv.mobile.ui.components.downloadActionFor
import tv.own.owntv.mobile.ui.components.icon
import tv.own.owntv.mobile.ui.components.label
import tv.own.owntv.mobile.ui.components.onTap
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.MoveToCategorySheet
import tv.own.owntv.mobile.ui.components.NewCategoryDialog
import tv.own.owntv.mobile.ui.components.ReorderSheet
import tv.own.owntv.mobile.ui.components.SheetAction
import tv.own.owntv.mobile.ui.components.TmdbDetailsSheet
import tv.own.owntv.mobile.ui.components.movieDetails
import tv.own.owntv.mobile.ui.components.seriesDetails
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/** Which follow-up the sheet handed off to. Only ever one at a time. */
private enum class VodDialog { MOVE, MOVE_TO_CATEGORY, NEW_CATEGORY, DETAILS, TMDB_NAME, SUBTITLES }

/**
 * The long-press menu for a film or a show, and everything it opens.
 *
 * The keys are the TV app's, because the order the user arranged in Settings is saved against exactly
 * those strings — a menu rearranged on the television comes out rearranged here.
 *
 * The sheet closes the moment an action is tapped, so [onDismiss] is deliberately *not* called from
 * it: the caller is released only once the sheet is gone **and** nothing it opened is still up.
 */
@Composable
fun VodMenu(
    item: VodItem,
    tab: LibraryTab,
    selected: LiveKey,
    isFavorite: Boolean,
    originName: String,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val movie = tab == LibraryTab.MOVIES
    val watched = movie && vm.isWatched(item.id)
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sheetOpen by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<VodDialog?>(null) }
    LaunchedEffect(sheetOpen, dialog) { if (!sheetOpen && dialog == null) onDismiss() }

    // Resolved once the menu opens, and again after a refetch. Null while it is still being looked up,
    // which is why the TMDB rows appear a moment after the rest — they are the only ones that need it.
    var meta by remember { mutableStateOf<MetadataCacheEntity?>(null) }
    var metaTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(metaTick) { if (mode.enrich) meta = vm.metaFor(tab, item.id) }

    // Films only, and only when there is something to delete — the TV app's gating.
    var subtitles by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(item.id) {
        subtitles = if (movie) vm.downloadedSubtitles(item.id).map { it.cacheId } else emptyList()
    }

    // What this item's Download action should now say and do — the row watches its own transfer.
    val downloadRows by vm.downloadsOf(item.id).collectAsStateWithLifecycle(emptyList())
    val downloadAction = downloadActionFor(downloadRows)

    if (sheetOpen) {
        val canMove = vm.contextKeyOf(selected) != null
        val actions = buildList {
            add(
                SheetAction(
                    key = "favourite",
                    label = stringResource(
                        if (isFavorite) R.string.content_remove_favourite else R.string.content_add_favourite,
                    ),
                    icon = if (isFavorite) MobileIcons.Star else MobileIcons.StarBorder,
                    group = 0,
                    onClick = { vm.toggleFavorite(item.id) },
                ),
            )
            if (movie) {
                add(
                    SheetAction(
                        key = "mark_watched",
                        label = stringResource(
                            if (watched) R.string.content_mark_unwatched else R.string.content_mark_watched,
                        ),
                        icon = if (watched) MobileIcons.RadioButtonUnchecked else MobileIcons.CheckCircle,
                        group = 0,
                        onClick = { vm.setWatched(item.id, !watched) },
                    ),
                )
                add(
                    SheetAction(
                        key = "play_external",
                        label = stringResource(R.string.content_play_external_short),
                        icon = MobileIcons.OpenInNew,
                        group = 1,
                        onClick = { vm.playExternal(item.id) },
                    ),
                )
            }
            add(
                SheetAction(
                    key = "download",
                    label = downloadAction.label(
                        stringResource(
                            if (movie) R.string.content_download else R.string.content_download_all_episodes,
                        ),
                    ),
                    icon = downloadAction.icon(),
                    destructive = downloadAction.deletes,
                    group = 1,
                    onClick = downloadAction.onTap(
                        onDownload = { vm.download(item.id) },
                        onRetry = { vm.retryDownloads(downloadRows) },
                        onDelete = { vm.deleteDownloads(downloadRows) },
                    ),
                ),
            )
            if (subtitles.isNotEmpty()) {
                add(
                    SheetAction(
                        key = "delete_subtitles",
                        label = stringResource(R.string.content_delete_subtitles),
                        icon = MobileIcons.Subtitles,
                        group = 1,
                        onClick = { dialog = VodDialog.SUBTITLES },
                    ),
                )
            }
            if (canMove) {
                add(
                    SheetAction(
                        key = "move",
                        label = stringResource(R.string.content_move),
                        icon = MobileIcons.SwapVert,
                        group = 2,
                        onClick = { dialog = VodDialog.MOVE },
                    ),
                )
                add(
                    SheetAction(
                        key = "move_to_category",
                        label = stringResource(R.string.content_move_to_category),
                        icon = MobileIcons.PlaylistAdd,
                        group = 2,
                        onClick = { dialog = VodDialog.MOVE_TO_CATEGORY },
                    ),
                )
            }
            if (mode.enrich) {
                // Only once a confident match has actually resolved: there is nothing to show otherwise.
                if (meta != null) {
                    add(
                        SheetAction(
                            key = "tmdb_details",
                            label = stringResource(R.string.content_tmdb_details),
                            icon = MobileIcons.Info,
                            group = 3,
                            onClick = { dialog = VodDialog.DETAILS },
                        ),
                    )
                }
                tv.own.owntv.core.metadata.TrailerKeys.split(meta?.trailerKey).firstOrNull()?.let { key ->
                    add(
                        SheetAction(
                            key = "play_trailer",
                            label = stringResource(R.string.content_play_trailer),
                            icon = MobileIcons.OpenInNew,
                            group = 3,
                            onClick = { context.playTrailer(key) },
                        ),
                    )
                }
                // Always offered while enrichment is on, so a wrong match — or a "no match" the app
                // remembers for a week — can be cleared and searched again on the spot.
                add(
                    SheetAction(
                        key = "refetch_tmdb",
                        label = stringResource(R.string.content_refetch_tmdb),
                        icon = MobileIcons.Refresh,
                        group = 3,
                        onClick = {
                            Toast.makeText(context, R.string.content_researching_tmdb, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                vm.clearMeta(tab, item.id)
                                meta = vm.metaFor(tab, item.id)
                            }
                        },
                    ),
                )
                add(
                    SheetAction(
                        key = "set_tmdb_name",
                        label = stringResource(R.string.content_set_tmdb_name),
                        icon = MobileIcons.Title,
                        group = 3,
                        onClick = { dialog = VodDialog.TMDB_NAME },
                    ),
                )
            }
            add(
                SheetAction(
                    key = "hide",
                    label = stringResource(R.string.common_hide),
                    icon = MobileIcons.VisibilityOff,
                    destructive = true,
                    group = 4,
                    onClick = { vm.hide(item.id) },
                ),
            )
            if (selected == LiveKey.History) {
                add(
                    SheetAction(
                        key = "remove_history",
                        label = stringResource(R.string.content_remove_history),
                        destructive = true,
                        group = 4,
                        onClick = { vm.removeFromHistory(item.id) },
                    ),
                )
            }
        }

        ContentMenuSheet(
            menu = if (movie) ContentMenu.MOVIE else ContentMenu.SERIES,
            title = item.name,
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }

    when (dialog) {
        VodDialog.MOVE -> ReorderSheet(
            title = item.name,
            openAt = item.id,
            load = { vm.moveList(selected) },
            onSave = { ids -> vm.contextKeyOf(selected)?.let { vm.commitMove(it, ids) } },
            onDismiss = { dialog = null },
        )
        VodDialog.MOVE_TO_CATEGORY -> MoveToCategorySheet(
            originName = originName,
            targets = vm.customCategories.collectAsStateWithLifecycle().value,
            onNewCategory = { dialog = VodDialog.NEW_CATEGORY },
            onMove = { targetId, keep ->
                vm.contextKeyOf(selected)?.let { vm.moveToCategory(item.id, it, targetId, keep) }
            },
            onDismiss = { dialog = null },
        )
        VodDialog.NEW_CATEGORY -> NewCategoryDialog(
            onCreate = { vm.createCustomCategory(it) },
            // Back to the picker, where the category just created is waiting.
            onDismiss = { dialog = VodDialog.MOVE_TO_CATEGORY },
        )
        VodDialog.DETAILS -> VodDetails(tab, item.id, meta, mode.tmdbWins, vm) { dialog = null }
        VodDialog.TMDB_NAME -> SetTmdbNameDialog(
            load = { vm.tmdbNamePrefill(tab, item.id) },
            onSave = { title, year ->
                scope.launch {
                    vm.setTmdbName(tab, item.id, title, year)
                    metaTick++
                }
            },
            onDismiss = { dialog = null },
        )
        VodDialog.SUBTITLES -> DeleteSubtitlesSheet(
            load = { vm.downloadedSubtitles(item.id).map { it.cacheId to (it.languageName ?: it.fileName) } },
            onDelete = { cacheId -> vm.deleteSubtitle(cacheId) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** The details sheet needs the row itself, which the grid does not carry — so it is fetched here. */
@Composable
private fun VodDetails(
    tab: LibraryTab,
    itemId: Long,
    meta: MetadataCacheEntity?,
    tmdbWins: Boolean,
    vm: LibraryViewModel,
    onDismiss: () -> Unit,
) {
    val movie = tab == LibraryTab.MOVIES
    val entity by produceState<Any?>(null, itemId) {
        value = if (movie) vm.movieById(itemId) else vm.seriesById(itemId)
    }
    val details = when (val e = entity) {
        is MovieEntity -> movieDetails(e, meta, tmdbWins)
        is SeriesEntity -> seriesDetails(e, meta, tmdbWins)
        else -> null
    }
    details?.let { TmdbDetailsSheet(it, onDismiss) }
}

/**
 * Type the exact title TMDB should be searched under, when the automatic match got it wrong.
 *
 * Saving a blank title is how the override comes off again, which is why there is no separate Clear
 * button: the field starts filled, and emptying it says "use the provider's name".
 */
@Composable
private fun SetTmdbNameDialog(
    load: suspend () -> LibraryViewModel.TmdbNamePrefill?,
    onSave: (title: String, year: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        load()?.let {
            title = it.title
            year = it.year?.toString().orEmpty()
        }
    }
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setup_tmdb_name)) },
        text = {
            Column {
                Text(stringResource(R.string.setup_tmdb_description))
                MobileTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.common_title),
                )
                MobileTextField(
                    value = year,
                    onValueChange = { s -> year = s.filter { it.isDigit() }.take(4) },
                    label = stringResource(R.string.setup_year_optional),
                    keyboardType = KeyboardType.Number,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(title.trim(), year.trim().toIntOrNull())
                    onDismiss()
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** The subtitles downloaded for this film or episode. Tapping one deletes it; the row goes with it. */
@Composable
fun DeleteSubtitlesSheet(
    load: suspend () -> List<Pair<Long, String>>,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val rows = remember { mutableStateListOf<Pair<Long, String>>() }
    LaunchedEffect(Unit) { rows.addAll(load()) }
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.content_delete_subtitles),
    ) {
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(rows, key = { it.first }) { (cacheId, label) ->
                MobileListRow(
                    title = label,
                    onClick = {
                        onDelete(cacheId)
                        rows.removeAll { it.first == cacheId }
                    },
                )
            }
        }
    }
}

/**
 * Trailers open in whatever plays YouTube on this phone — the app if it is installed, the browser if
 * it is not. The television has to embed a player because a TV often has neither; a phone always has
 * one, and it will be better than anything embedded here.
 */
internal fun Context.playTrailer(key: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$key"))
    runCatching { startActivity(intent) }
}

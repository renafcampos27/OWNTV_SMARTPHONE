package tv.own.owntv.mobile.ui.screens.library

import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.mobile.ui.nav.posterKey
import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.SharedFlow
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileSlider
import tv.own.owntv.mobile.ui.components.CategoryPickerSheet
import tv.own.owntv.mobile.ui.components.BrowseCategorySheet
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.mobileGroupPlate
import tv.own.owntv.mobile.ui.components.PosterCard
import tv.own.owntv.mobile.ui.components.GRID_LIST_SHARE
import tv.own.owntv.mobile.ui.components.TwoPane
import tv.own.owntv.mobile.ui.nav.isExpandedWidth
import tv.own.owntv.mobile.ui.screens.ObeyScrollToTop
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Movies and Series.
 *
 * On a phone they are two tabs of one screen, because the bottom bar has five slots and neither
 * deserves one of its own. On a tablet the rail has room for both, and [fixedTab] is how that screen
 * says which half it is — the tab strip disappears and everything below it stays the same.
 *
 * The grid decides its own column count from the width it was given, until the user pinches: from
 * then on their number is kept and travels with their backup, exactly like the fixed grid setting on
 * the television.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    scrollToTop: SharedFlow<String>,
    route: String,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    fixedTab: LibraryTab? = null,
    /**
     * Pins the grid to one folder and takes the chip strip away — how the Favourites and History
     * screens show films and shows without a second copy of this grid existing.
     */
    lockedKey: LiveKey? = null,
    vm: LibraryViewModel = koinViewModel(),
) {
    LaunchedEffect(fixedTab) { fixedTab?.let { vm.select(it) } }
    // Locking and unlocking are one pair: the pin belongs to this screen's lifetime, not to the view
    // model's. On the television that view model is a single instance shared with the browse section,
    // so a pin left behind froze its category rail. `DisposableEffect` (not `LaunchedEffect`) also
    // means the pin is in place before the first frame, so the list never flashes the wrong folder.
    if (lockedKey != null) {
        val pinned = lockedKey
        DisposableEffect(pinned) {
            vm.lock(pinned)
            onDispose { vm.unlock() }
        }
    }

    val tab by vm.tab.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()
    val chosenColumns by vm.gridColumns.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val progress by vm.movieProgress.collectAsStateWithLifecycle()

    val items = when (tab) {
        LibraryTab.MOVIES -> vm.movies
        LibraryTab.SERIES -> vm.series
    }.collectAsLazyPagingItems()

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    gridState.ObeyGridScrollToTop(route, scrollToTop)
    listState.ObeyScrollToTop(route, scrollToTop)

    var menuFor by remember { mutableStateOf<VodItem?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }
    var categoryPicker by remember { mutableStateOf(false) }
    var categoryMenuFor by remember { mutableStateOf<VodCategory?>(null) }

    val twoPane = isExpandedWidth()
    // Beside the grid, something is always open: the pane is a preview of the grid, so it falls back
    // to the first tile until the user taps another. Derived rather than stored, so a category whose
    // first title happens to be the one already showing still fills the pane instead of emptying it.
    var tappedItem by rememberSaveable { mutableStateOf<Long?>(null) }
    val openItem = tappedItem ?: if (twoPane) items.peekFirstId() else null

    // A new selection's scroll position has nothing to do with the old one's, and neither does the
    // title that was open in the pane — it is not in this category any more.
    LaunchedEffect(tab, selected) {
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
        tappedItem = null
    }

    val listPane = @Composable {
    // The column count comes from the width this pane actually got, not the window's: beside a
    // detail pane, or next to the rail, the grid has less room than the screen is wide.
    BoxWithConstraints(Modifier.fillMaxSize()) {
    // Roughly one poster per 110 dp of width, which is three on a small phone and eight on a tablet
    // in landscape. The user's own number wins whenever they have set one.
    val width = maxWidth.value.toInt()
    val columns = chosenColumns.takeIf { it > 0 } ?: (width / COLUMN_WIDTH_DP).coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    // The gaps between the columns come out of the tiles, or the last one is pushed off the edge.
    val gaps = MobileDimens.GridGap.value.toInt() * (columns - 1)
    val posterWidth = ((width - GRID_PADDING_DP * 2 - gaps) / columns).dp

    Column(Modifier.fillMaxSize()) {
        if (fixedTab == null) {
            // Transparent, or Material's own opaque surface paints a square black band across the
            // top of the page's rounded glass pane and squares off its two top corners.
            PrimaryTabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = Color.Transparent,
            ) {
                LibraryTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { vm.select(entry) },
                        text = { Text(stringResource(entry.labelRes())) },
                    )
                }
            }
        }
        if (lockedKey == null) Box(Modifier.fillMaxWidth()) {
            FilterChipRow(
                labels = categories.map { it.label(tab) },
                selectedIndex = categories.indexOfFirst { it.key == selected },
                onSelect = { index -> categories.getOrNull(index)?.let { vm.select(it.key) } },
                modifier = Modifier.padding(end = MobileDimens.TouchTarget * 2),
                // All, Favorites and History are not folders: there is nothing to hide or move.
                onLongPress = { index ->
                    categoryMenuFor = categories.getOrNull(index)?.takeIf { it.builtIn == null }
                },
                onLongPressLabel = stringResource(R.string.settings_customize_categories),
            )
            Row(Modifier.align(Alignment.CenterEnd)) {
                IconButton(onClick = { categoryPicker = true }) {
                    Icon(MobileIcons.Search, stringResource(R.string.content_search_categories))
                }
                IconButton(onClick = { sheetOpen = true }) {
                    Icon(MobileIcons.Tune, stringResource(R.string.content_sorting))
                }
            }
        }
        if (categoryPicker) {
            CategoryPickerSheet(
                labels = categories.map { it.label(tab) },
                selectedIndex = categories.indexOfFirst { it.key == selected },
                onSelect = { index -> categories.getOrNull(index)?.let { vm.select(it.key) } },
                onDismiss = { categoryPicker = false },
            )
        }
        val categoryLabel = categories.firstOrNull { it.key == selected }?.label(tab).orEmpty()
        Text(
            // "Action (312 movies)" — core's own wording, so the count reads as it does on the TV.
            text = pluralStringResource(
                if (tab == LibraryTab.MOVIES) R.plurals.content_count_movies else R.plurals.content_count_series,
                count,
                categoryLabel,
                count,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        // Empty only once the first page has actually come back — otherwise every category change
        // flashes "No movies here" for as long as the query takes on a 170k-title catalogue.
        if (items.itemCount == 0 && items.loadState.refresh !is LoadState.Loading) {
            EmptyLibrary(tab)
        } else if (viewMode == SettingsRepository.VodViewMode.LIST) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().mobileGroupPlate()) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { item ->
                        MobileListRow(
                            title = item.name,
                            subtitle = item.details(),
                            // Beside the grid the title opens in the pane; on a phone it is a
                            // screen of its own. The same screen, reached two ways.
                            onClick = {
                                if (twoPane) tappedItem = item.id else onOpenItem(tab, item.id)
                            },
                            onLongClick = { menuFor = item },
                        )
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(MobileDimens.GapSmall),
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GridGap),
                verticalArrangement = Arrangement.spacedBy(MobileDimens.GridGap),
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToResize(columns) { vm.setGridColumns(it) },
            ) {
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { item ->
                        PosterCard(
                            title = item.name,
                            imageUrl = item.posterUrl,
                            subtitle = item.details(),
                            progress = progress[item.id]?.takeIf { tab == LibraryTab.MOVIES }
                                ?.let { it.positionMs.toFloat() / it.durationMs.coerceAtLeast(1) },
                            width = posterWidth,
                            sharedKey = posterKey(tab.name, item.id),
                            // Beside the grid the title opens in the pane; on a phone it is a
                            // screen of its own. The same screen, reached two ways.
                            onClick = {
                                if (twoPane) tappedItem = item.id else onOpenItem(tab, item.id)
                            },
                            onLongClick = { menuFor = item },
                        )
                    }
                }
            }
        }
    }

    if (sheetOpen) {
        LibraryOptionsSheet(vm = vm, columns = columns, onDismiss = { sheetOpen = false })
    }
    }
    }

    if (twoPane) {
        TwoPane(
            list = listPane,
            detail = {
                openItem?.let { id ->
                    DetailScreen(tab = tab, itemId = id, onPlay = onPlay)
                }
            },
            modifier = modifier,
            listShare = GRID_LIST_SHARE,
        )
    } else {
        Box(modifier) { listPane() }
    }

    menuFor?.let { item ->
        VodMenu(
            item = item,
            tab = tab,
            selected = selected,
            isFavorite = item.id in favorites,
            originName = categories.firstOrNull { it.key == selected }?.label(tab).orEmpty(),
            vm = vm,
            onDismiss = { menuFor = null },
        )
    }

    categoryMenuFor?.let { category ->
        BrowseCategorySheet(
            title = category.label(tab),
            onHide = { vm.hideCategory(category.key) },
            onMove = { kind -> vm.moveCategory(category.key, kind) },
            onDismiss = { categoryMenuFor = null },
        )
    }
}

/**
 * Sorting, grid-or-list, and how big the posters are.
 *
 * The size slider is the same number the pinch gesture sets, for the people who never think to pinch
 * — dragging it left puts more, smaller posters on a row.
 */
@Composable
private fun LibraryOptionsSheet(vm: LibraryViewModel, columns: Int, onDismiss: () -> Unit) {
    val sort by vm.sortMode.collectAsStateWithLifecycle()
    val viewMode by vm.viewMode.collectAsStateWithLifecycle()

    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.content_sorting)) {
        SettingsRepository.SortMode.entries.forEach { mode ->
            MobileListRow(
                title = stringResource(mode.labelRes()),
                leading = { RadioButton(selected = mode == sort, onClick = { vm.setSort(mode); onDismiss() }) },
                onClick = { vm.setSort(mode); onDismiss() },
            )
        }
        HorizontalDivider()
        SettingsRepository.VodViewMode.entries.forEach { mode ->
            MobileListRow(
                title = stringResource(mode.labelRes()),
                leading = {
                    RadioButton(selected = mode == viewMode, onClick = { vm.setViewMode(mode); onDismiss() })
                },
                onClick = { vm.setViewMode(mode); onDismiss() },
            )
        }
        if (viewMode == SettingsRepository.VodViewMode.GRID) {
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(
                    start = MobileDimens.ScreenPaddingH,
                    top = MobileDimens.GapSmall,
                ),
            )
            MobileSlider(
                // Left is smaller posters, so the slider runs the opposite way to the column count.
                value = (MAX_COLUMNS + MIN_COLUMNS - columns).toFloat(),
                onValueChange = { vm.setGridColumns(MAX_COLUMNS + MIN_COLUMNS - it.toInt()) },
                valueRange = MIN_COLUMNS.toFloat()..MAX_COLUMNS.toFloat(),
                steps = MAX_COLUMNS - MIN_COLUMNS - 1,
                modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(tab: LibraryTab) {
    Box(Modifier.fillMaxSize().padding(MobileDimens.GapLarge), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(
                if (tab == LibraryTab.MOVIES) R.string.content_no_movies_here else R.string.content_no_series_here,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The year, the rating, or both — whatever the provider actually sent for this item. */
@Composable
private fun VodItem.details(): String? {
    val stars = rating?.takeIf { it > 0 }?.let { stringResource(R.string.content_rating, it.toFloat()) }
    val separator = stringResource(R.string.content_epg_bits_separator)
    return listOfNotNull(year?.takeIf { it > 0 }?.toString(), stars)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator)
}

@Composable
private fun VodCategory.label(tab: LibraryTab): String = when (builtIn) {
    VodCategory.BuiltIn.ALL -> stringResource(
        if (tab == LibraryTab.MOVIES) R.string.content_category_all_movies else R.string.content_category_all_series,
    )
    VodCategory.BuiltIn.FAVORITES -> stringResource(R.string.content_category_favorites)
    VodCategory.BuiltIn.HISTORY -> stringResource(R.string.content_category_history)
    null -> title.orEmpty()
}

private fun LibraryTab.labelRes() = when (this) {
    LibraryTab.MOVIES -> R.string.common_nav_movies
    LibraryTab.SERIES -> R.string.common_nav_series
}

private fun SettingsRepository.SortMode.labelRes() = when (this) {
    SettingsRepository.SortMode.PLAYLIST -> R.string.settings_sort_playlist
    SettingsRepository.SortMode.ALPHA -> R.string.settings_sort_alpha
    SettingsRepository.SortMode.RATING -> R.string.settings_sort_rating
    SettingsRepository.SortMode.DATE_ADDED -> R.string.settings_sort_date_added
}

private fun SettingsRepository.VodViewMode.labelRes() = when (this) {
    SettingsRepository.VodViewMode.GRID -> R.string.settings_view_grid
    SettingsRepository.VodViewMode.LIST -> R.string.settings_view_list
}

/** [tv.own.owntv.mobile.ui.screens.ObeyScrollToTop] for a grid. */
@Composable
private fun LazyGridState.ObeyGridScrollToTop(route: String, scrollToTop: SharedFlow<String>) {
    val instant = LocalAnimations.current == AnimationLevel.OFF
    LaunchedEffect(route, instant) {
        scrollToTop.collect { requested ->
            if (requested != route) return@collect
            if (instant) scrollToItem(0) else animateScrollToItem(0)
        }
    }
}

/**
 * Pinch the posters bigger or smaller.
 *
 * It watches for a second finger and only then takes the gesture, so an ordinary one-finger scroll
 * is never stolen from the grid underneath. One step per pinch: the count changes once and the
 * gesture then does nothing until the fingers come up, which stops a slow squeeze from running the
 * whole way from eight columns to two.
 */
private fun Modifier.pinchToResize(columns: Int, onChange: (Int) -> Unit): Modifier =
    pointerInput(columns) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var startDistance = 0f
            var changed = false
            var down = true
            while (down) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.size >= 2) {
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (startDistance == 0f) {
                        startDistance = distance
                    } else if (!changed) {
                        val ratio = distance / startDistance
                        if (ratio > PINCH_OUT || ratio < PINCH_IN) {
                            // Fingers apart means bigger posters, which means fewer of them.
                            val next = if (ratio > 1f) columns - 1 else columns + 1
                            onChange(next.coerceIn(MIN_COLUMNS, MAX_COLUMNS))
                            changed = true
                        }
                    }
                    pressed.forEach { it.consume() }
                }
                down = event.changes.any { it.pressed }
            }
        }
    }

/**
 * The first title in the grid, without asking the pager to load a page for it — the detail pane
 * follows it, and a preview must never be the reason a page is fetched.
 */
private fun LazyPagingItems<VodItem>.peekFirstId(): Long? = itemSnapshotList.firstOrNull()?.id

private const val COLUMN_WIDTH_DP = 110
private const val GRID_PADDING_DP = 8
private const val MIN_COLUMNS = 2
private const val MAX_COLUMNS = 8
private const val PINCH_OUT = 1.25f
private const val PINCH_IN = 0.8f

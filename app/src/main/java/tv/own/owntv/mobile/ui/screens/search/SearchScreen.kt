package tv.own.owntv.mobile.ui.screens.search

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.content.SearchIntent
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ContentActionsMenu
import tv.own.owntv.mobile.ui.components.ContentTarget
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * One field over everything the profile has.
 *
 * A phone has no room for the television's three columns, so the three kinds of result are one list
 * with a heading and a count above each group. Nothing is searched until two characters are typed;
 * until then the screen offers the recent terms and the three curated lists, which is what a user
 * opening search without a word in mind actually wants.
 */
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onOpenChannel: (Long) -> Unit,
    onOpenMovie: (Long) -> Unit,
    onOpenSeries: (Long) -> Unit,
    modifier: Modifier = Modifier,
    vm: SearchViewModel = koinViewModel(),
) {
    // Arriving with a title already chosen — "All versions" on Home's trending hero. Keyed on the
    // text, so returning to a search the user has since edited does not type over it again.
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) vm.setQuery(initialQuery) }

    val query by vm.query.collectAsStateWithLifecycle()
    val intent by vm.intent.collectAsStateWithLifecycle()
    val typed by vm.results.collectAsStateWithLifecycle()
    val curated by vm.curated.collectAsStateWithLifecycle()
    val recents by vm.recentSearches.collectAsStateWithLifecycle()
    val sources by vm.sourceNames.collectAsStateWithLifecycle()
    val favoriteChannels by vm.favoriteChannels.collectAsStateWithLifecycle()
    val favoriteMovies by vm.favoriteMovies.collectAsStateWithLifecycle()
    val favoriteSeries by vm.favoriteSeries.collectAsStateWithLifecycle()

    val searching = query.trim().length >= MIN_QUERY
    val results = if (searching) typed else curated
    // One playlist needs no attribution; several do, and that is the case the line is there for.
    val showProvider = sources.size > 1
    val listState = rememberLazyListState()
    var menuFor by remember { mutableStateOf<ContentTarget?>(null) }

    // The list itself is not a composable scope, so everything it needs from resources is read here.
    val channelsLabel = stringResource(R.string.search_channels)
    val moviesLabel = stringResource(R.string.search_movie)
    val seriesLabel = stringResource(R.string.search_series)

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Focus alone does not reliably raise the IME across Android versions, keyboard apps and
    // navigation states. Request focus, then explicitly show the keyboard once the field is
    // attached. Delayed: requesting in the same frame as composition is dropped on some builds.
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
        delay(150)
        runCatching { keyboard?.show() }
    }
    LaunchedEffect(query, intent) { listState.scrollToItem(0) }

    // Reaching the bottom asks for the next page. Without it a search stops at the first 40 of each
    // kind, and a provider with hundreds of CNN feeds looks like it only has forty.
    LaunchedEffect(listState, searching) {
        if (!searching) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - LOAD_MORE_MARGIN) vm.loadMore()
        }
    }

    Column(modifier.fillMaxSize().imePadding()) {
        MobileTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = stringResource(R.string.search_hint),
            imeAction = ImeAction.Search,
            onSearch = { vm.rememberQuery(); keyboard?.hide() },
            modifier = Modifier
                .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall)
                .focusRequester(focus),
        )
        IntentChips(selected = intent, enabled = !searching, onSelect = vm::setIntent)

        when {
            !searching && intent == null -> RecentSearches(
                recents = recents,
                onPick = vm::setQuery,
                onClear = vm::clearRecentSearches,
            )
            results.isEmpty -> CenterMessage(
                if (searching) stringResource(R.string.search_no_results, query.trim())
                else stringResource(R.string.search_nothing_in_list, intent.label()),
            )
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                group(
                    label = channelsLabel,
                    count = results.channels.size,
                    items = results.channels,
                    key = { "c${it.channel.id}" },
                ) { row ->
                    MobileListRow(
                        title = row.channel.name,
                        subtitle = subtitle(
                            row.categoryName,
                            if (showProvider) sources[row.channel.sourceId] else null,
                        ),
                        trailing = { FavoriteMark(row.channel.id in favoriteChannels) },
                        onClick = { vm.rememberQuery(); onOpenChannel(row.channel.id) },
                        onLongClick = {
                            menuFor = ContentTarget(MediaType.LIVE, row.channel.id, row.channel.name)
                        },
                    )
                }
                group(
                    label = moviesLabel,
                    count = results.movies.size,
                    items = results.movies,
                    key = { "m${it.id}" },
                ) { movie ->
                    MobileListRow(
                        title = movie.name,
                        subtitle = subtitle(
                            movie.meta(),
                            if (showProvider) sources[movie.sourceId] else null,
                        ),
                        trailing = { FavoriteMark(movie.id in favoriteMovies) },
                        onClick = { vm.rememberQuery(); onOpenMovie(movie.id) },
                        onLongClick = { menuFor = ContentTarget(MediaType.MOVIE, movie.id, movie.name) },
                    )
                }
                group(
                    label = seriesLabel,
                    count = results.series.size,
                    items = results.series,
                    key = { "s${it.id}" },
                ) { show ->
                    MobileListRow(
                        title = show.name,
                        subtitle = subtitle(
                            show.meta(),
                            if (showProvider) sources[show.sourceId] else null,
                        ),
                        trailing = { FavoriteMark(show.id in favoriteSeries) },
                        onClick = { vm.rememberQuery(); onOpenSeries(show.id) },
                        onLongClick = { menuFor = ContentTarget(MediaType.SERIES, show.id, show.name) },
                    )
                }
            }
        }
    }

    menuFor?.let { target ->
        ContentActionsMenu(
            target = target,
            isFavorite = target.id in when (target.type) {
                MediaType.LIVE -> favoriteChannels
                MediaType.MOVIE -> favoriteMovies
                else -> favoriteSeries
            },
            onToggleFavorite = { vm.toggleFavorite(target.type, target.id) },
            onDownload = { vm.download(target.type, target.id) },
            onHide = { vm.hide(target.type, target.id) },
            onDismiss = { menuFor = null },
        )
    }
}

/** A heading with its count, then the rows — skipped entirely when the group came back empty. */
private fun <T> LazyListScope.group(
    label: String,
    count: Int,
    items: List<T>,
    key: (T) -> Any,
    row: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "hdr_$label") { GroupHeader(label, count) }
    items(items, key = key) { row(it) }
}

@Composable
private fun GroupHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.content_downloads_count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The three curated lists, offered while the field is empty.
 *
 * They go quiet the moment a query is typed rather than disappearing, so the strip does not make the
 * results jump up the screen the instant the second character lands.
 */
@Composable
private fun IntentChips(selected: SearchIntent?, enabled: Boolean, onSelect: (SearchIntent?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
    ) {
        SearchIntent.entries.forEach { entry ->
            FilterChip(
                selected = entry == selected,
                enabled = enabled,
                onClick = { onSelect(if (entry == selected) null else entry) },
                label = { Text(entry.label()) },
            )
        }
    }
}

@Composable
private fun RecentSearches(recents: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    if (recents.isEmpty()) {
        CenterMessage(stringResource(R.string.search_recent_empty))
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH, vertical = MobileDimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            AssistChip(onClick = onClear, label = { Text(stringResource(R.string.search_clear)) })
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(recents, key = { it }) { term ->
                MobileListRow(title = term, onClick = { onPick(term) })
            }
        }
    }
}

@Composable
private fun FavoriteMark(isFavorite: Boolean) {
    if (!isFavorite) return
    Icon(
        imageVector = MobileIcons.Star,
        contentDescription = stringResource(R.string.content_category_favorites),
        tint = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun CenterMessage(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(MobileDimens.GapLarge),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** "2019 · 7.8" — whatever the provider actually sent. */
@Composable
private fun MovieEntity.meta(): String? = metaLine(year, rating)

@Composable
private fun SeriesEntity.meta(): String? = metaLine(year, rating)

@Composable
private fun metaLine(year: Int?, rating: Double?): String? = listOfNotNull(
    year?.takeIf { it > 0 }?.toString(),
    rating?.takeIf { it > 0 }?.let { stringResource(R.string.content_rating, it.toFloat()) },
).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.content_epg_bits_separator))

/** The row's second line: what it is, and — when several playlists are loaded — where it came from. */
@Composable
private fun subtitle(detail: String?, provider: String?): String? = listOfNotNull(
    detail?.takeIf { it.isNotBlank() },
    provider?.takeIf { it.isNotBlank() },
).takeIf { it.isNotEmpty() }?.joinToString(stringResource(R.string.content_epg_bits_separator))

@Composable
private fun SearchIntent?.label(): String = when (this) {
    SearchIntent.CONTINUE -> stringResource(R.string.search_continue)
    SearchIntent.UNWATCHED -> stringResource(R.string.search_unwatched)
    SearchIntent.CHANNELS -> stringResource(R.string.search_channels)
    null -> stringResource(R.string.search_title)
}

private const val MIN_QUERY = 2

/** How many rows from the end the next page is fetched, so the list does not visibly stall. */
private const val LOAD_MORE_MARGIN = 5

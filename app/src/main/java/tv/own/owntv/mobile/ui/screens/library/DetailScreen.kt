package tv.own.owntv.mobile.ui.screens.library

import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import tv.own.owntv.mobile.ui.nav.posterKey
import tv.own.owntv.mobile.ui.nav.sharedPoster
import tv.own.owntv.mobile.ui.components.MobileIcons
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import tv.own.owntv.core.theme.GlassSurface
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.EpisodeEntity
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ContentMenuSheet
import tv.own.owntv.mobile.ui.components.DownloadActionButton
import tv.own.owntv.mobile.ui.components.downloadActionFor
import tv.own.owntv.mobile.ui.components.icon
import tv.own.owntv.mobile.ui.components.label
import tv.own.owntv.mobile.ui.components.onTap
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.rememberAirDateLabel
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SheetAction
import tv.own.owntv.mobile.ui.components.TmdbDetailsSheet
import tv.own.owntv.mobile.ui.components.episodeDetails
import tv.own.owntv.mobile.ui.player.formatTimestamp
import tv.own.owntv.mobile.ui.player.rememberResumeGate
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.MobilePosterShape
import tv.own.owntv.mobile.ui.theme.glassSurface

/**
 * A film or a show, opened.
 *
 * One screen for both: a film's Play button is the whole of it, a show grows a season strip and its
 * episodes underneath. Everything scrolls as one list, because on a phone a fixed header would leave
 * about four rows of episodes visible.
 */
@Composable
fun DetailScreen(
    tab: LibraryTab,
    itemId: Long,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    vm: DetailViewModel = koinViewModel(),
) {
    LaunchedEffect(tab, itemId) { vm.open(tab, itemId) }

    val movie by vm.movie.collectAsStateWithLifecycle()
    val show by vm.show.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val seasons by vm.seasons.collectAsStateWithLifecycle()
    val season by vm.season.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val episodeProgress by vm.episodeProgress.collectAsStateWithLifecycle()
    val completedIds by vm.completedIds.collectAsStateWithLifecycle()
    val seasonMeta by vm.seasonMeta.collectAsStateWithLifecycle()
    val lastWatchedId by vm.lastWatchedId.collectAsStateWithLifecycle()
    val nextUpId by vm.nextUpId.collectAsStateWithLifecycle()
    val hideWatched by vm.hideWatched.collectAsStateWithLifecycle()
    val order by vm.order.collectAsStateWithLifecycle()
    val itemDownloads by vm.itemDownloads.collectAsStateWithLifecycle()
    val episodeDownloads by vm.episodeDownloadStates.collectAsStateWithLifecycle()

    val title = movie?.name ?: show?.name.orEmpty()
    val plot = movie?.plot ?: show?.plot
    val poster = movie?.posterUrl ?: show?.posterUrl
    val backdrop = movie?.backdropUrl ?: show?.backdropUrl
    val year = movie?.year ?: show?.year
    val rating = movie?.rating ?: show?.rating
    // Null until the show has loaded; then the last-watched season, or its first.
    val currentSeason = season ?: seasons.firstOrNull()
    val seasonEpisodes = remember(episodes, currentSeason, order) {
        episodes.filter { it.seasonNumber == currentSeason }
            .sortedBy { it.episodeNumber }
            .let { if (order.episodesDescending) it.reversed() else it }
    }
    val shown = if (hideWatched) seasonEpisodes.filterNot { it.id in completedIds } else seasonEpisodes
    val nextUp = episodes.firstOrNull { it.id == nextUpId }
    val resumeMs = progress?.takeIf { it.durationMs > 1L }?.positionMs ?: 0L

    // An episode is one tap with one meaning, so a part-watched one goes through the Resume playback
    // setting. The two buttons above it do NOT: "Resume at 12:34" and "Play" are already the answer to
    // the question, and asking it again after the user has pressed one of them is asking twice.
    val resumeGate = rememberResumeGate()

    var menuFor by remember { mutableStateOf<EpisodeEntity?>(null) }
    var episodeOptions by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        item {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AsyncImage(
                    model = backdrop ?: poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // The tile the user tapped, landed. It overlaps the backdrop rather than replacing
                // it, which is what gives the travelling poster somewhere to arrive at — and it is
                // the one picture on this screen that is certainly the same picture as on the grid.
                AsyncImage(
                    model = poster,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = MobileDimens.ScreenPaddingH, bottom = MobileDimens.GapSmall)
                        .height(MobileDimens.DetailPosterHeight)
                        .aspectRatio(2f / 3f)
                        .sharedPoster(posterKey(tab.name, itemId))
                        .clip(MobilePosterShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                )
            }
            Column(
                Modifier
                    .glassSurface(GlassSurface.PREVIEW, RectangleShape)
                    .padding(MobileDimens.ScreenPaddingH),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    // A glass surface is not an M3 container, so nothing on it inherits a content
                    // colour — unstated, the title came out black on a dark backdrop.
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                    modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
                ) {
                    year?.takeIf { it > 0 }?.let { Chip(it.toString()) }
                    rating?.takeIf { it > 0 }?.let {
                        Chip(stringResource(R.string.content_rating, it.toFloat()))
                    }
                    movie?.durationSecs?.takeIf { it > 0 }?.let {
                        Chip(formatTimestamp(it * 1000L))
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                ) {
                    MobileButton(
                        text = if (resumeMs > 0) {
                            stringResource(R.string.content_resume_at, formatTimestamp(resumeMs))
                        } else {
                            stringResource(R.string.content_play)
                        },
                        onClick = { vm.play(resumeMs, onPlay) },
                    )
                    if (resumeMs > 0) {
                        MobileButton(
                            text = stringResource(R.string.content_play),
                            onClick = { vm.play(0L, onPlay) },
                            style = MobileButtonStyle.SECONDARY,
                        )
                    }
                    IconButton(onClick = { vm.toggleFavorite() }) {
                        Icon(
                            imageVector = if (favorite) MobileIcons.Star else MobileIcons.StarBorder,
                            contentDescription = stringResource(
                                if (favorite) R.string.content_remove_favourite
                                else R.string.content_add_favourite,
                            ),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DownloadActionButton(
                        action = downloadActionFor(itemDownloads),
                        onDownload = { vm.download() },
                        onRetry = { vm.retryDownloads(itemDownloads) },
                        onDelete = { vm.deleteDownloads(itemDownloads) },
                        idleLabel = stringResource(
                            if (tab == LibraryTab.SERIES) R.string.content_download_all_episodes
                            else R.string.content_download,
                        ),
                    )
                    // How the episode list is shown belongs with the show's other buttons, not
                    // wedged between "Next up" and the first episode — down there it read as part of
                    // the resume card and pushed the list itself off the screen.
                    if (tab == LibraryTab.SERIES) {
                        IconButton(onClick = { episodeOptions = true }) {
                            Icon(
                                imageVector = MobileIcons.Tune,
                                contentDescription = stringResource(R.string.content_episode_options),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                if (!plot.isNullOrBlank()) {
                    Text(
                        text = plot,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MobileDimens.GapMedium),
                    )
                }
            }
        }

        if (tab == LibraryTab.SERIES) {
            item {
                nextUp?.let { episode ->
                    NextUpCard(
                        episode = episode,
                        positionMs = episodeProgress[episode.id]?.takeIf { it.durationMs > 1L }?.positionMs ?: 0L,
                        onPlay = {
                            resumeGate(episodeProgress[episode.id]?.positionMs ?: 0L) {
                                vm.playEpisode(episode.id, it, onPlay)
                            }
                        },
                    )
                }
                if (seasons.size > 1) {
                    FilterChipRow(
                        labels = seasons.map { number ->
                            val total = episodes.count { it.seasonNumber == number }
                            val done = episodes.count { it.seasonNumber == number && it.id in completedIds }
                            if (total > 0) {
                                stringResource(R.string.content_season_progress, number, done, total)
                            } else {
                                stringResource(R.string.content_season, number)
                            }
                        },
                        selectedIndex = seasons.indexOf(currentSeason),
                        onSelect = { index -> seasons.getOrNull(index)?.let { vm.selectSeason(it) } },
                    )
                }
                if (loading) {
                    Box(
                        Modifier.fillMaxWidth().padding(MobileDimens.GapLarge),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                } else if (shown.isEmpty()) {
                    Text(
                        text = stringResource(R.string.content_no_episodes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(MobileDimens.GapLarge),
                    )
                }
            }
            items(shown, key = { it.id }) { episode ->
                val watched = episodeProgress[episode.id]
                EpisodeRow(
                    episode = episode,
                    meta = seasonMeta[episode.id],
                    positionMs = watched?.positionMs ?: 0L,
                    durationMs = watched?.durationMs ?: 0L,
                    completed = episode.id in completedIds,
                    lastWatched = episode.id == lastWatchedId,
                    onClick = {
                        resumeGate(watched?.positionMs ?: 0L) { vm.playEpisode(episode.id, it, onPlay) }
                    },
                    onLongClick = { menuFor = episode },
                )
            }
        }
    }

    if (episodeOptions) {
        EpisodeOptionsSheet(
            order = order,
            hideWatched = hideWatched,
            canHideWatched = completedIds.isNotEmpty(),
            onHideWatched = { vm.setHideWatched(!hideWatched) },
            onChange = { seasonsDesc, episodesDesc -> vm.setOrder(seasonsDesc, episodesDesc) },
            onDismiss = { episodeOptions = false },
        )
    }

    menuFor?.let { episode ->
        EpisodeMenu(
            episode = episode,
            watched = episode.id in completedIds,
            vm = vm,
            onDismiss = { menuFor = null },
        )
    }
}

/**
 * The show's resume target, offered above the season strip.
 *
 * Hidden once every episode has been watched, because there is nothing left to carry on with.
 */
@Composable
private fun NextUpCard(episode: EpisodeEntity, positionMs: Long, onPlay: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(MobileDimens.ScreenPaddingH)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                MaterialTheme.shapes.medium,
            )
            .padding(MobileDimens.GapMedium),
    ) {
        Text(
            text = stringResource(R.string.content_next_up),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = episode.rowTitle(),
            style = MaterialTheme.typography.titleSmall,
            // A tinted background is not a container either, so this line needs its own colour.
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (positionMs > 0) {
            Text(
                text = stringResource(R.string.content_resume_at, formatTimestamp(positionMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MobileButton(
            text = stringResource(R.string.content_play),
            onClick = onPlay,
            modifier = Modifier.padding(top = MobileDimens.GapSmall),
        )
    }
}

/**
 * How the episode list is shown: what is hidden, and in what order.
 *
 * One sheet rather than a strip of chips above the list. The chips cost two lines of a phone screen
 * on every visit to settle a question most people answer once, and they sat between the resume card
 * and the first episode, where they read as part of the card. Hide watched only appears once there
 * is something watched to hide.
 *
 * Sorting is presentation only — playing on ignores it.
 */
@Composable
private fun EpisodeOptionsSheet(
    order: DetailViewModel.SeriesOrder,
    hideWatched: Boolean,
    canHideWatched: Boolean,
    onHideWatched: () -> Unit,
    onChange: (seasonsDescending: Boolean, episodesDescending: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.content_episode_options),
    ) {
        if (canHideWatched) {
            Row(
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            ) {
                FilterChip(
                    selected = hideWatched,
                    onClick = onHideWatched,
                    label = {
                        Text(
                            stringResource(
                                if (hideWatched) R.string.content_show_watched else R.string.content_hide_watched,
                            ),
                        )
                    },
                )
            }
        }
        SortingRow(
            label = stringResource(R.string.content_seasons),
            descending = order.seasonsDescending,
            onSelect = { desc -> onChange(desc, order.episodesDescending) },
        )
        SortingRow(
            label = stringResource(R.string.content_episodes),
            descending = order.episodesDescending,
            onSelect = { desc -> onChange(order.seasonsDescending, desc) },
        )
    }
}

@Composable
private fun SortingRow(label: String, descending: Boolean, onSelect: (Boolean) -> Unit) {
    Column(
        Modifier.padding(
            horizontal = MobileDimens.ScreenPaddingH,
            vertical = MobileDimens.GapSmall,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
            FilterChip(
                selected = !descending,
                onClick = { onSelect(false) },
                label = { Text(stringResource(R.string.content_oldest_first)) },
            )
            FilterChip(
                selected = descending,
                onClick = { onSelect(true) },
                label = { Text(stringResource(R.string.content_newest_first)) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: EpisodeEntity,
    meta: MetadataCacheEntity?,
    positionMs: Long,
    durationMs: Long,
    completed: Boolean,
    lastWatched: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column {
        MobileListRow(
            leading = if (completed) {
                {
                    Icon(
                        imageVector = MobileIcons.CheckCircle,
                        contentDescription = stringResource(R.string.content_mark_watched),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
            trailing = if (lastWatched) {
                {
                    Text(
                        text = stringResource(R.string.content_last_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
            title = episode.rowTitle(),
            // The day it aired leads the line, because on a show with a thousand episodes that is
            // what tells two near-identical titles apart; the plot follows it where there is one.
            subtitle = listOfNotNull(
                rememberAirDateLabel(episode, meta),
                episode.plot?.takeIf { it.isNotBlank() },
            ).joinToString(stringResource(R.string.content_metadata_separator)).takeIf { it.isNotEmpty() },
            onClick = onClick,
            onLongClick = onLongClick,
        )
        // Only an episode actually started has a line, and a finished one is full rather than reset.
        if (positionMs > 0 && durationMs > 1) {
            LinearProgressIndicator(
                progress = { (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MobileDimens.ScreenPaddingH),
            )
        }
    }
}

/** Which follow-up the episode sheet handed off to. Only ever one at a time. */
private enum class EpisodeDialog { DETAILS, SUBTITLES }

/**
 * The long-press menu for one episode, and everything it opens.
 *
 * The keys are the TV app's, so an order arranged on the television comes out arranged here. As in
 * the film and show menus, the caller is released only once the sheet is gone **and** nothing it
 * opened is still up.
 */
@Composable
private fun EpisodeMenu(
    episode: EpisodeEntity,
    watched: Boolean,
    vm: DetailViewModel,
    onDismiss: () -> Unit,
) {
    val mode by vm.metadataMode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var sheetOpen by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<EpisodeDialog?>(null) }
    LaunchedEffect(sheetOpen, dialog) { if (!sheetOpen && dialog == null) onDismiss() }

    var meta by remember { mutableStateOf<MetadataCacheEntity?>(null) }
    LaunchedEffect(episode.id) { if (mode.enrich) meta = vm.episodeMeta(episode) }

    var hasSubtitles by remember { mutableStateOf(false) }
    LaunchedEffect(episode.id) { hasSubtitles = vm.downloadedSubtitles(episode).isNotEmpty() }

    // This episode's own download row, so the menu offers what is actually left to do with it.
    val episodeDownloads by vm.episodeDownloadStates.collectAsStateWithLifecycle()
    val episodeRows = listOfNotNull(episodeDownloads[episode.id])
    val downloadAction = downloadActionFor(episodeRows)

    val title = episode.rowTitle()

    if (sheetOpen) {
        val actions = buildList {
            add(
                SheetAction(
                    key = "mark_watched",
                    label = stringResource(
                        if (watched) R.string.content_mark_unwatched else R.string.content_mark_watched,
                    ),
                    icon = if (watched) MobileIcons.RadioButtonUnchecked else MobileIcons.CheckCircle,
                    onClick = { vm.setEpisodeWatched(episode, !watched) },
                ),
            )
            add(
                SheetAction(
                    key = "download",
                    label = downloadAction.label(),
                    icon = downloadAction.icon(),
                    destructive = downloadAction.deletes,
                    group = 1,
                    onClick = downloadAction.onTap(
                        onDownload = { vm.download(episode) },
                        onRetry = { vm.retryDownloads(episodeRows) },
                        onDelete = { vm.deleteDownloads(episodeRows) },
                    ),
                ),
            )
            add(
                SheetAction(
                    key = "play_external",
                    label = stringResource(R.string.content_play_external_short),
                    icon = MobileIcons.OpenInNew,
                    group = 1,
                    onClick = { vm.playExternal(episode) {} },
                ),
            )
            if (hasSubtitles) {
                add(
                    SheetAction(
                        key = "delete_subtitles",
                        label = stringResource(R.string.content_delete_subtitles),
                        icon = MobileIcons.Subtitles,
                        group = 1,
                        onClick = { dialog = EpisodeDialog.SUBTITLES },
                    ),
                )
            }
            if (mode.enrich) {
                if (meta != null) {
                    add(
                        SheetAction(
                            key = "tmdb_details",
                            label = stringResource(R.string.content_tmdb_details),
                            icon = MobileIcons.Info,
                            group = 2,
                            onClick = { dialog = EpisodeDialog.DETAILS },
                        ),
                    )
                }
                add(
                    SheetAction(
                        key = "refetch_tmdb",
                        label = stringResource(R.string.content_refetch_tmdb),
                        icon = MobileIcons.Refresh,
                        group = 2,
                        onClick = {
                            Toast.makeText(context, R.string.content_researching_tmdb, Toast.LENGTH_SHORT).show()
                            scope.launch {
                                vm.clearEpisodeMeta(episode)
                                meta = vm.episodeMeta(episode)
                            }
                        },
                    ),
                )
            }
        }
        ContentMenuSheet(
            menu = ContentMenu.EPISODE,
            title = title,
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }

    when (dialog) {
        EpisodeDialog.DETAILS -> TmdbDetailsSheet(
            details = episodeDetails(episode, meta, mode.tmdbWins),
            onDismiss = { dialog = null },
        )
        EpisodeDialog.SUBTITLES -> DeleteSubtitlesSheet(
            load = { vm.downloadedSubtitles(episode).map { it.cacheId to (it.languageName ?: it.fileName) } },
            onDelete = { cacheId -> vm.deleteSubtitle(cacheId) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** "S1E2 · Title", or just "S1E2" when the provider gave the episode no name of its own. */
@Composable
private fun EpisodeEntity.rowTitle(): String = if (name.isBlank()) {
    stringResource(R.string.content_season_episode, seasonNumber, episodeNumber)
} else {
    stringResource(R.string.content_season_episode_title, seasonNumber, episodeNumber, name)
}

@Composable
private fun Chip(label: String) {
    AssistChip(onClick = { }, label = { Text(label) })
}

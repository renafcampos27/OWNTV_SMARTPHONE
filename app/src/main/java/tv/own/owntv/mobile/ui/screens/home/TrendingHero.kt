package tv.own.owntv.mobile.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import tv.own.owntv.core.database.entity.MetadataCacheEntity
import tv.own.owntv.core.home.TrendingHomeItem
import tv.own.owntv.core.metadata.MetadataCast
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.trending.ProviderVariantParser
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MediaDetails
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.components.TmdbDetailsSheet
import tv.own.owntv.mobile.ui.components.jsonList
import tv.own.owntv.mobile.ui.screens.library.playTrailer
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Now Trending, as one hero card — the television's, in a portrait shape.
 *
 * Everything the TV shows is here and worded the same way: the rank, the badges, the sentence saying
 * why this item was picked, the same four buttons and the same ten-second rotation. The one thing a
 * phone cannot copy is the layout: the TV puts the poster, the copy and the "why" panel side by side
 * in three columns, and a portrait screen has room for one. So the same things are stacked instead,
 * and the "why" panel — the third column there — opens with a tap here rather than always standing
 * open. The TV pauses its clock while the buttons hold focus; here it pauses while a finger is on the
 * card, and a sideways swipe is a second way to move between items.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrendingHero(
    items: List<TrendingHomeItem>,
    preferredLanguage: String,
    seasonCounts: Map<Long, Int>,
    onActivate: (TrendingHomeItem, onUnavailable: () -> Unit) -> Unit,
    onOpenSearch: (String) -> Unit,
    resolveDetails: suspend (TrendingHomeItem) -> HomeViewModel.TrendingDetails,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    // Saved, so turning the phone does not throw the rotation back to the first item. The TV keeps
    // this in its view model; a phone's screen is the thing that gets rebuilt.
    var activeIndex by rememberSaveable(items.size) { mutableIntStateOf(0) }
    var manuallyPaused by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var whyExpanded by remember { mutableStateOf(false) }
    var resetClock by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    var unavailable by remember { mutableStateOf(false) }
    var detailsFor by remember { mutableStateOf<TrendingHomeItem?>(null) }
    val item = items.getOrNull(activeIndex) ?: return
    val snapshot = item.snapshot
    val context = LocalContext.current

    fun navigate(delta: Int) {
        activeIndex = (activeIndex + delta + items.size) % items.size
        progress = 0f
        resetClock++
    }

    // A different item has its own availability and its own reason; neither carries over.
    LaunchedEffect(activeIndex) {
        unavailable = false
        whyExpanded = false
    }

    LaunchedEffect(activeIndex, manuallyPaused, touching, resetClock, items.size) {
        if (manuallyPaused || touching || items.size < 2) return@LaunchedEffect
        val startProgress = progress.coerceIn(0f, 1f)
        val duration = (INTERVAL_MS * (1f - startProgress)).toLong().coerceAtLeast(1L)
        val startedAt = System.nanoTime()
        while (true) {
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            progress = (startProgress + (1f - startProgress) * elapsedMs.toFloat() / duration).coerceIn(0f, 1f)
            if (elapsedMs >= duration) break
            delay(TICK_MS)
        }
        progress = 0f
        activeIndex = (activeIndex + 1) % items.size
    }

    val movieLabel = stringResource(R.string.home_trending_movie)
    val seriesLabel = stringResource(R.string.home_trending_series)
    val isMovie = item is TrendingHomeItem.Movie
    val typeLabel = if (isMovie) movieLabel else seriesLabel
    val backdrop = MetadataImages.backdrop(snapshot.backdropPath, size = "w1280")
        ?: when (item) {
            is TrendingHomeItem.Movie -> item.movie.backdropUrl ?: item.movie.posterUrl
            is TrendingHomeItem.Series -> item.series.backdropUrl ?: item.series.posterUrl
        }
    val poster = MetadataImages.poster(snapshot.posterPath, size = "w500")
        ?: when (item) {
            is TrendingHomeItem.Movie -> item.movie.posterUrl
            is TrendingHomeItem.Series -> item.series.posterUrl
        }
    val displaySignals = ProviderVariantParser.displaySignals(snapshot.providerRawName)
    val providerLanguage = snapshot.providerLanguage
    val languageBadge = when {
        providerLanguage == preferredLanguage ->
            stringResource(R.string.home_trending_language_choice, preferredLanguage)
        providerLanguage == "EN" -> stringResource(R.string.home_trending_english_fallback)
        providerLanguage == null -> stringResource(R.string.home_trending_untagged_fallback)
        else -> stringResource(R.string.home_trending_other_fallback, providerLanguage)
    }
    val reasonTitle = stringResource(R.string.home_trending_reason_title, snapshot.trendingRank, typeLabel)
    val reasonCopy = when {
        providerLanguage == preferredLanguage ->
            stringResource(R.string.home_trending_reason_preferred, preferredLanguage)
        providerLanguage == "EN" -> stringResource(R.string.home_trending_reason_english, preferredLanguage)
        providerLanguage == null -> stringResource(R.string.home_trending_reason_untagged, preferredLanguage)
        else -> stringResource(R.string.home_trending_reason_other)
    }
    val seasonCount = (item as? TrendingHomeItem.Series)?.let { seasonCounts[it.series.id] }
    val surface = MaterialTheme.colorScheme.surfaceContainerLowest

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH)
            .clip(RoundedCornerShape(MobileDimens.GroupCorner))
            .background(surface)
            // The clock stops while a finger is down, the way the TV's stops while its buttons hold
            // focus. Nothing is consumed here, so the buttons underneath still work normally.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    touching = true
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    touching = false
                }
            }
            .pointerInput(items.size) {
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragged = 0f },
                    onDragEnd = { if (kotlin.math.abs(dragged) > SWIPE_SLOP_PX) navigate(if (dragged < 0) 1 else -1) },
                ) { change, amount ->
                    dragged += amount
                    change.consume()
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (!backdrop.isNullOrBlank()) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // The TV's two washes, turned upright: the picture has to end in the card rather than
            // stop against it, and the poster and the rank pill have to stay legible over it.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        0f to surface.copy(alpha = 0.82f),
                        0.58f to surface.copy(alpha = 0.30f),
                        1f to Color.Transparent,
                    ),
                ),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.45f to Color.Transparent, 1f to surface),
                ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(MobileDimens.GapMedium)
                    .width(PosterWidth)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(MobileDimens.PosterArtCorner))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(MobileDimens.PosterArtCorner)),
                contentAlignment = Alignment.Center,
            ) {
                if (!poster.isNullOrBlank()) {
                    AsyncImage(
                        model = poster,
                        contentDescription = snapshot.localizedTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = if (isMovie) MobileIcons.Movie else MobileIcons.Tv,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(RankIconSize),
                    )
                }
                Text(
                    text = "#${activeIndex + 1}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopStart).padding(MobileDimens.GapSmall)
                        .clip(CircleShape)
                        .background(Color(0xDC030A08))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(MobileDimens.GapMedium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(SectionDotSize).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(MobileDimens.GapSmall))
                Text(
                    text = stringResource(R.string.home_trending_section_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(MobileDimens.GapSmall))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                TrendingTypeBadge(typeLabel)
                snapshot.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                snapshot.rating?.let {
                    Text(
                        text = stringResource(R.string.content_rating, it),
                        style = MaterialTheme.typography.titleSmall,
                        color = RatingColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(MobileDimens.GapSmall))
            Text(
                text = snapshot.localizedTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            snapshot.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(MobileDimens.GapSmall))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(MobileDimens.GapSmall))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                TrendingMatchBadge("✓ ${stringResource(R.string.home_trending_provider_match)}")
                Text(
                    text = snapshot.providerRawName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(MobileDimens.GapSmall))
            // A phone is narrow and German is long: six badges wrap onto as many lines as they need
            // rather than the last two falling off the edge.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                TrendingBadge(languageBadge, primary = true)
                displaySignals.quality.label?.let { TrendingBadge(it) }
                displaySignals.capabilities.forEach { TrendingBadge(it) }
                seasonCount?.let {
                    TrendingBadge(pluralStringResource(R.plurals.home_trending_seasons, it, it))
                }
            }

            Spacer(Modifier.height(MobileDimens.GapMedium))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(MobileDimens.CardCorner))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { whyExpanded = !whyExpanded }
                    .padding(MobileDimens.GapMedium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.home_trending_why_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (whyExpanded) MobileIcons.KeyboardArrowUp else MobileIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AnimatedVisibility(visible = whyExpanded) {
                    Column {
                        Spacer(Modifier.height(MobileDimens.GapSmall))
                        Text(
                            text = reasonTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(MobileDimens.GapTiny))
                        Text(
                            text = reasonCopy,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(MobileDimens.GapMedium))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                verticalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
            ) {
                TrendingActionButton(
                    label = stringResource(
                        if (isMovie) R.string.home_trending_play else R.string.home_trending_open_episodes,
                    ),
                    icon = if (isMovie) MobileIcons.PlayArrow else MobileIcons.Tv,
                    primary = true,
                    onClick = { onActivate(item) { unavailable = true } },
                )
                tv.own.owntv.core.metadata.TrailerKeys.split(snapshot.trailerKey).firstOrNull()?.let { key ->
                    TrendingActionButton(
                        label = stringResource(R.string.home_trending_trailer),
                        icon = MobileIcons.OpenInNew,
                        onClick = { context.playTrailer(key) },
                    )
                }
                TrendingActionButton(
                    label = stringResource(R.string.home_trending_more_details),
                    icon = MobileIcons.Info,
                    onClick = { detailsFor = item },
                )
                TrendingActionButton(
                    label = stringResource(R.string.home_trending_all_versions),
                    icon = MobileIcons.Search,
                    onClick = { onOpenSearch(snapshot.canonicalTitle) },
                )
            }
            if (unavailable) {
                Spacer(Modifier.height(MobileDimens.GapSmall))
                Text(
                    text = stringResource(R.string.home_trending_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = MobileDimens.GapSmall),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrendingControlButton(
                icon = MobileIcons.SkipPrevious,
                description = stringResource(R.string.home_trending_previous),
                onClick = { navigate(-1) },
            )
            Spacer(Modifier.width(MobileDimens.GapMedium))
            TrendingControlButton(
                icon = if (manuallyPaused) MobileIcons.PlayArrow else MobileIcons.Pause,
                description = stringResource(
                    if (manuallyPaused) R.string.home_trending_resume else R.string.home_trending_pause,
                ),
                onClick = {
                    manuallyPaused = !manuallyPaused
                    if (!manuallyPaused) {
                        progress = 0f
                        resetClock++
                    }
                },
            )
            Spacer(Modifier.width(MobileDimens.GapMedium))
            TrendingControlButton(
                icon = MobileIcons.SkipNext,
                description = stringResource(R.string.home_trending_next),
                onClick = { navigate(1) },
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(ProgressHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(ProgressHeight)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }

    detailsFor?.let { target ->
        TrendingDetailsSheet(target, resolveDetails, onDismiss = { detailsFor = null })
    }
}

/** The same details sheet the library shows, filled from Trending's own confirmed TMDB id. */
@Composable
private fun TrendingDetailsSheet(
    target: TrendingHomeItem,
    resolveDetails: suspend (TrendingHomeItem) -> HomeViewModel.TrendingDetails,
    onDismiss: () -> Unit,
) {
    val resolved by produceState<HomeViewModel.TrendingDetails?>(null, target) {
        value = resolveDetails(target)
    }
    val state = resolved ?: return
    TmdbDetailsSheet(trendingDetails(target, state.cache, state.tmdbWins), onDismiss)
}

/** Provider and TMDB merged the TV's way, with the snapshot standing in for anything TMDB has not
 *  cached yet — the hero is built from that snapshot, so it is never emptier than the card above. */
@Composable
private fun trendingDetails(
    item: TrendingHomeItem,
    meta: MetadataCacheEntity?,
    tmdbWins: Boolean,
): MediaDetails {
    val snapshot = item.snapshot
    val providerPoster = when (item) {
        is TrendingHomeItem.Movie -> item.movie.posterUrl
        is TrendingHomeItem.Series -> item.series.posterUrl
    }
    val providerBackdrop = when (item) {
        is TrendingHomeItem.Movie -> item.movie.backdropUrl
        is TrendingHomeItem.Series -> item.series.backdropUrl
    }
    val providerTitle = when (item) {
        is TrendingHomeItem.Movie -> item.movie.name
        is TrendingHomeItem.Series -> item.series.name
    }
    val providerPlot = when (item) {
        is TrendingHomeItem.Movie -> item.movie.plot?.takeIf { it.isNotBlank() }
        is TrendingHomeItem.Series -> item.series.plot?.takeIf { it.isNotBlank() }
    }
    val tmdbPlot = meta?.overview?.takeIf { it.isNotBlank() } ?: snapshot.overview
    val tmdbPoster = MetadataImages.poster(meta?.posterPath ?: snapshot.posterPath, size = "w500")
    return MediaDetails(
        title = providerTitle,
        subtitle = stringResource(
            if (item is TrendingHomeItem.Movie) R.string.home_trending_movie else R.string.home_trending_series,
        ),
        backdropUrl = MetadataImages.backdrop(meta?.backdropPath ?: snapshot.backdropPath, size = "w1280")
            ?: providerBackdrop,
        posterUrl = if (tmdbWins) tmdbPoster ?: providerPoster else providerPoster ?: tmdbPoster,
        metaLine = listOfNotNull(
            snapshot.year?.toString(),
            snapshot.rating?.let { stringResource(R.string.content_rating, it) },
        ).joinToString(" · "),
        genres = jsonList(meta?.genresJson),
        plot = if (tmdbWins) tmdbPlot ?: providerPlot else providerPlot ?: tmdbPlot,
        cast = MetadataCast.parse(meta?.castJson),
    )
}

@Composable
private fun TrendingTypeBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.11f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun TrendingMatchBadge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MatchTextColor,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MatchFillColor)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TrendingBadge(label: String, primary: Boolean = false) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (primary) BadgePrimaryText else BadgeText,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(BadgeFill)
            .border(
                1.dp,
                if (primary) BadgePrimaryBorder else Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TrendingActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false,
) {
    val container =
        if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val content =
        if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = MobileDimens.TouchTarget)
            .clip(RoundedCornerShape(9.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = MobileDimens.GapMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(ActionIconSize))
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrendingControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(MobileDimens.TouchTarget)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(ControlIconSize),
        )
    }
}

private const val INTERVAL_MS = 10_000L
private const val TICK_MS = 80L
private const val SWIPE_SLOP_PX = 60f

private val PosterWidth = 96.dp
private val RankIconSize = 32.dp
private val SectionDotSize = 7.dp
private val ProgressHeight = 3.dp
private val ActionIconSize = 16.dp
private val ControlIconSize = 20.dp

private val RatingColor = Color(0xFFFFE071)
private val MatchTextColor = Color(0xFFBDECA5)
private val MatchFillColor = Color(0x3659AD2F)
private val BadgeText = Color(0xFFDCE6E2)
private val BadgePrimaryText = Color(0xFFD5F4C5)
private val BadgeFill = Color(0x8C06100D)
private val BadgePrimaryBorder = Color(0x7A74CF42)

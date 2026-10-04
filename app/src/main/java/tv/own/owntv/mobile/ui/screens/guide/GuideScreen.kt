package tv.own.owntv.mobile.ui.screens.guide

import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import tv.own.owntv.core.model.RecordingStatus
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileSlider
import tv.own.owntv.mobile.ui.components.BrowseCategorySheet
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.mobileGroupPlate
import tv.own.owntv.mobile.ui.components.rememberClockTick
import tv.own.owntv.mobile.ui.screens.ObeyScrollToTop
import tv.own.owntv.mobile.ui.screens.live.LiveCategory
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/**
 * The guide, on a screen you hold.
 *
 * The television draws one thing — a grid — because it has the width for it and a remote to fling
 * across it. A phone gets three, and remembers which one was picked: a list of what is on right now
 * (the only shape that fits a portrait phone), the grid itself for a tablet or a turned phone, and a
 * single channel's schedule read top to bottom.
 */
@Composable
fun GuideScreen(
    scrollToTop: SharedFlow<String>,
    onOpenChannel: (Long) -> Unit,
    onAddEpg: () -> Unit,
    modifier: Modifier = Modifier,
    vm: GuideViewModel = koinViewModel(),
) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val guideDays by vm.guideDays.collectAsStateWithLifecycle()
    val window by vm.window.collectAsStateWithLifecycle()
    val storedMode by vm.viewMode.collectAsStateWithLifecycle()
    val density by vm.densityPct.collectAsStateWithLifecycle()
    val sort by vm.sortGuide.collectAsStateWithLifecycle()
    val replayOnly by vm.replayOnly.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val matching by vm.matching.collectAsStateWithLifecycle()
    val review by vm.review.collectAsStateWithLifecycle()
    val summary by vm.matchSummary.collectAsStateWithLifecycle()

    // Nothing has been chosen yet: a grid needs width, so a portrait phone opens on the "on now" list.
    val wide = LocalConfiguration.current.screenWidthDp >= WIDE_DP
    val chosenMode = storedMode ?: if (wide) SettingsRepository.GuideView.GRID else SettingsRepository.GuideView.ON_NOW
    val mode = if (day < 0 && chosenMode == SettingsRepository.GuideView.ON_NOW) SettingsRepository.GuideView.TIMELINE else chosenMode

    val listState = rememberLazyListState()
    listState.ObeyScrollToTop(route = "guide", scrollToTop = scrollToTop)

    var optionsOpen by remember { mutableStateOf(false) }
    var sheetFor by remember { mutableStateOf<Pair<ChannelEntity, EpgProgrammeEntity>?>(null) }
    var menuFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var categoryMenuFor by remember { mutableStateOf<LiveCategory?>(null) }

    // A different category, day or search is a different list; the old scroll position means nothing.
    LaunchedEffect(selected, day, query, mode) { listState.scrollToItem(0) }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val guideKeyboard = LocalSoftwareKeyboardController.current
            MobileTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = stringResource(R.string.content_epg_search_hint),
                imeAction = ImeAction.Search,
                onSearch = { guideKeyboard?.hide() },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { optionsOpen = true }) {
                Icon(MobileIcons.Tune, stringResource(R.string.content_epg_title))
            }
        }
        DayStrip(selected = day, days = guideDays, onSelect = vm::selectDay)

        if (matching) {
            MatchingBanner()
        } else {
            summary?.let { MatchSummaryBanner(it, onDismiss = vm::clearReview) }
        }
        // Not an empty state: the rows below are drawn, they are simply all blank until it is fixed.
        if (stats?.mismatchedIds == true) {
            GuideNotice(stringResource(R.string.content_epg_mismatched_ids))
        }

        // The list gets the height that is left, explicitly, rather than filling the column.
        Box(Modifier.weight(1f)) {
        if (channels.itemCount == 0) {
            EmptyGuide(
                query = query,
                stats = stats,
                // Favourites and Catch-up narrow the list rather than reorder it, so an empty grid
                // under one of them is a filter result, not an empty guide.
                filterHidesEverything = sort == SettingsRepository.GuideSort.FAVORITES ||
                    sort == SettingsRepository.GuideSort.CATCHUP || replayOnly,
                onAddEpg = onAddEpg,
            )
        } else {
            when (mode) {
                SettingsRepository.GuideView.ON_NOW -> OnNowList(
                    vm = vm,
                    channels = channels,
                    listState = listState,
                    favorites = emptySet(),
                    onOpen = { channel, programme -> sheetFor = channel to programme },
                    onOpenChannel = onOpenChannel,
                    onMenu = { menuFor = it },
                )
                SettingsRepository.GuideView.GRID -> GuideGrid(
                    vm = vm,
                    channels = channels,
                    listState = listState,
                    window = window,
                    densityPct = density,
                    onOpen = { channel, programme -> sheetFor = channel to programme },
                    onOpenChannel = onOpenChannel,
                    onMenu = { menuFor = it },
                )
                SettingsRepository.GuideView.TIMELINE -> GuideTimeline(
                    vm = vm,
                    channels = channels,
                    listState = listState,
                    onOpen = { channel, programme -> sheetFor = channel to programme },
                )
            }
        }
        }
    }

    if (optionsOpen) {
        GuideOptionsSheet(
            mode = mode,
            densityPct = density,
            sort = sort,
            replayOnly = replayOnly,
            onReplayOnly = vm::setReplayOnly,
            stats = stats,
            matching = matching,
            onMode = { vm.setViewMode(it); optionsOpen = false },
            onDensity = vm::setDensityPct,
            onSort = vm::setSortGuide,
            onAutoMatch = { vm.autoMatchEpg(); optionsOpen = false },
            onDismiss = { optionsOpen = false },
        )
    }
    if (review.isNotEmpty()) {
        val includeLogos by vm.includeGuideLogos.collectAsStateWithLifecycle()
        EpgReviewSheet(
            suggestions = review,
            includeLogos = includeLogos,
            onIncludeLogos = vm::setIncludeGuideLogos,
            onAccept = vm::acceptSuggestion,
            onSkip = vm::skipSuggestion,
            onAcceptAll = vm::acceptAllSuggestions,
            onDone = vm::clearReview,
        )
    }
    menuFor?.let { channel ->
        GuideChannelSheet(channel = channel, vm = vm, onDismiss = { menuFor = null })
    }
    sheetFor?.let { (channel, programme) ->
        ProgrammeSheet(
            channel = channel,
            programme = programme,
            isFavorite = false,
            vm = vm,
            onOpenChannel = onOpenChannel,
            onDismiss = { sheetFor = null },
        )
    }
    categoryMenuFor?.let { category ->
        BrowseCategorySheet(
            title = category.label(),
            onHide = { vm.hideCategory(category.key) },
            onMove = { kind -> vm.moveCategory(category.key, kind) },
            onDismiss = { categoryMenuFor = null },
        )
    }
}

/** Today first, then the week the guide usually holds. A day with nothing in it shows as empty. */
@Composable
private fun DayStrip(selected: Int, days: Int, onSelect: (Int) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val offsets = remember(days) { listOf(0) + (-1 downTo -tv.own.owntv.core.live.MAX_CATCHUP_DAYS).toList() + (1 until days).toList() }
    val labels = remember(locale, offsets) {
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM")
        val format = SimpleDateFormat(pattern, locale)
        val cal = Calendar.getInstance()
        offsets.map { offset ->
            val day = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            format.format(day.time)
        }
    }
    FilterChipRow(labels = labels, selectedIndex = offsets.indexOf(selected), onSelect = { onSelect(offsets[it]) })
}

/**
 * What is on every channel right now, and what follows it.
 *
 * This is the guide a phone can actually read: one channel per row, the programme underneath the
 * name, and a bar showing how much of it is gone. The rows on screen are answered in one query.
 */
@Composable
private fun OnNowList(
    vm: GuideViewModel,
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    favorites: Set<Long>,
    onOpen: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onOpenChannel: (Long) -> Unit,
    onMenu: (ChannelEntity) -> Unit,
) {
    val onNow by vm.onNow.collectAsStateWithLifecycle()
    val revision by vm.revision.collectAsStateWithLifecycle()
    // "On now" has to keep meaning now. Without this the list answered the question once, when it
    // opened, and then stood still: an hour later the bars were where they had been and the titles
    // were the programmes that had already finished.
    val nowMs by rememberClockTick()

    // A new match clears what was read, so the rows on screen have to ask for it again — and so does
    // the clock moving on, which is what makes a finished programme give way to the one after it.
    LaunchedEffect(listState, channels, revision, nowMs) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collect { indices ->
                vm.loadOnNow(indices.mapNotNull { channels.itemSnapshotList.getOrNull(it) })
            }
    }

    val times = rememberGuideTimeFormat()
    val separator = stringResource(R.string.content_epg_bits_separator)
    val nextLabel = stringResource(R.string.content_next_up)

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().mobileGroupPlate()) {
        items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
            val channel = channels[index] ?: return@items
            val slot = onNow[channel.id]
            val now = slot?.now
            MobileListRow(
                title = channel.name,
                subtitle = now?.title ?: stringResource(R.string.content_epg_no_guide),
                leading = { ChannelLogo(channel) },
                trailing = {
                    if (channel.id in favorites) {
                        Icon(
                            imageVector = MobileIcons.Star,
                            contentDescription = stringResource(R.string.content_category_favorites),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(TRAILING_ICON),
                        )
                    }
                },
                // Without a programme there is nothing to open a sheet about — go straight to the channel.
                onClick = { if (now != null) onOpen(channel, now) else onOpenChannel(channel.id) },
                onLongClick = { onMenu(channel) },
            )
            if (now != null) {
                NowProgress(now, nowMs)
                slot.next?.let { next ->
                    Text(
                        text = nextLabel + separator + times.format(Date(next.startMs)) + separator + next.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // Lined up under the programme title rather than under the logo, so the bar
                        // and this line read as the row's own second half instead of a strip
                        // floating between two channels.
                        modifier = Modifier.padding(
                            start = ROW_TEXT_INSET,
                            end = MobileDimens.ScreenPaddingH,
                            bottom = MobileDimens.GapSmall,
                        ),
                    )
                }
            }
        }
    }
}

/** How much of the current programme has already gone, at [nowMs] — which moves. */
@Composable
private fun NowProgress(programme: EpgProgrammeEntity, nowMs: Long) {
    val span = (programme.stopMs - programme.startMs).coerceAtLeast(1)
    val done = (nowMs - programme.startMs).toFloat() / span
    LinearProgressIndicator(
        progress = { done.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ROW_TEXT_INSET,
                end = MobileDimens.ScreenPaddingH,
                bottom = MobileDimens.GapTiny,
            ),
    )
}

@Composable
internal fun ChannelLogo(channel: ChannelEntity) {
    ChannelLogoImage(
        url = channel.displayLogoUrl,
        modifier = Modifier.size(LOGO_SIZE),
        fallback = {
            Icon(
                imageVector = MobileIcons.LiveTv,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(LOGO_SIZE),
            )
        },
    )
}

/**
 * One programme, opened.
 *
 * The synopsis is fetched here rather than carried by the list: a day of every channel's descriptions
 * is megabytes of text, and only the one that was tapped is ever read.
 */
@Composable
private fun ProgrammeSheet(
    channel: ChannelEntity,
    programme: EpgProgrammeEntity,
    isFavorite: Boolean,
    vm: GuideViewModel,
    onOpenChannel: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember(programme.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(programme.id) { description = vm.description(programme.id) }

    val times = rememberGuideTimeFormat()

    MobileBottomSheet(onDismissRequest = onDismiss, title = programme.title) {
        Text(
            text = stringResource(
                R.string.content_epg_time_range,
                times.format(Date(programme.startMs)),
                times.format(Date(programme.stopMs)),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        val detailsSeparator = stringResource(R.string.content_epg_bits_separator)
        val details = listOfNotNull(
            programme.categories?.split('\u001F')?.joinToString(detailsSeparator),
            programme.year?.toString(), programme.rating,
            programme.lengthMin?.let { androidx.compose.ui.res.pluralStringResource(R.plurals.recording_minutes, it, it) },
            programme.episode,
        ).filter(String::isNotBlank).joinToString(detailsSeparator)
        if (details.isNotBlank()) Text(details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH))
        description?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = MobileDimens.ScreenPaddingH,
                    vertical = MobileDimens.GapSmall,
                ),
            )
        }
        HorizontalDivider()
        if (vm.canCatchup(channel, programme)) {
            MobileListRow(
                title = stringResource(R.string.content_epg_watch_start),
                onClick = { onDismiss(); vm.playCatchup(channel, programme); onOpenChannel(channel.id) },
            )
        }
        // Record. What it offers depends on what is already true of this programme, so the row never
        // promises something it cannot do.
        if (vm.canRecord(channel, programme)) {
            val recordingRows by vm.recordingRows.collectAsStateWithLifecycle()
            val existing = remember(recordingRows, channel.id, programme.startMs) {
                vm.recordingFor(channel, programme)
            }
            // A database read, so once per opened programme rather than once per frame.
            val clash by produceState<String?>(null, channel.id, programme.startMs, recordingRows) {
                value = vm.clashFor(channel, programme)
            }
            when (existing?.status) {
                RecordingStatus.RECORDING -> MobileListRow(
                    title = stringResource(R.string.recording_stop),
                    leading = { Icon(MobileIcons.Pause, contentDescription = null) },
                    onClick = { vm.stopRecording(existing); onDismiss() },
                )
                RecordingStatus.SCHEDULED -> MobileListRow(
                    title = stringResource(R.string.common_cancel),
                    leading = { Icon(MobileIcons.Delete, contentDescription = null) },
                    onClick = { vm.cancelRecording(existing); onDismiss() },
                )
                else -> MobileListRow(
                    title = stringResource(
                        if (programme.stopMs <= System.currentTimeMillis()) {
                            R.string.recording_from_archive
                        } else {
                            R.string.recording_record
                        },
                    ),
                    // The clash, said before committing: a live programme cannot wait its turn, so a
                    // warning afterwards would be of no use at all (D10).
                    subtitle = clash?.let { stringResource(R.string.recording_clash_with, it) },
                    subtitleMaxLines = 2,
                    leading = { Icon(MobileIcons.LiveTv, contentDescription = null) },
                    onClick = { vm.record(channel, programme); onDismiss() },
                )
            }
            // "Every showing" only for a programme still to come — a rule is a standing instruction
            // about the future, and offering it on last night's repeat would promise nothing.
            if (programme.stopMs > System.currentTimeMillis()) {
                val seriesRule by produceState<tv.own.owntv.core.database.entity.RecordingRuleEntity?>(
                    null, channel.id, programme.title, recordingRows,
                ) {
                    value = vm.seriesRuleFor(channel, programme)
                }
                MobileListRow(
                    title = stringResource(
                        if (seriesRule != null) R.string.recording_stop_series
                        else R.string.recording_record_series,
                    ),
                    leading = { Icon(MobileIcons.CalendarMonth, contentDescription = null) },
                    onClick = {
                        seriesRule?.let { vm.stopSeries(it) } ?: vm.recordSeries(channel, programme)
                        onDismiss()
                    },
                )
            }
        }

    }
}

/** Which of the three shapes the guide takes, and — for the grid — how much time a screen holds. */
@Composable
private fun GuideOptionsSheet(
    mode: SettingsRepository.GuideView,
    densityPct: Int,
    sort: SettingsRepository.GuideSort,
    replayOnly: Boolean,
    onReplayOnly: (Boolean) -> Unit,
    stats: GuideStats?,
    matching: Boolean,
    onMode: (SettingsRepository.GuideView) -> Unit,
    onDensity: (Int) -> Unit,
    onSort: (SettingsRepository.GuideSort) -> Unit,
    onAutoMatch: () -> Unit,
    onDismiss: () -> Unit,
) {
    MobileBottomSheet(onDismissRequest = onDismiss, title = stringResource(R.string.content_epg_title)) {
        tv.own.owntv.mobile.ui.components.SettingRow(
            title = stringResource(if (replayOnly) R.string.guide_replay_only_on else R.string.guide_replay_only_off),
            checked = replayOnly,
            onCheckedChange = onReplayOnly,
        )
        SettingsRepository.GuideView.entries.forEach { entry ->
            MobileListRow(
                title = stringResource(entry.labelRes()),
                leading = { RadioButton(selected = entry == mode, onClick = { onMode(entry) }) },
                onClick = { onMode(entry) },
            )
        }
        HorizontalDivider()
        Text(
            text = stringResource(R.string.content_epg_sort_button, stringResource(sort.labelRes())),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(
                start = MobileDimens.ScreenPaddingH,
                top = MobileDimens.GapSmall,
            ),
        )
        // Catch-up is a filter wearing a sort's clothes: it shows only channels with an archive. On a
        // playlist that has none it can only ever empty the guide, so it is not offered — which is
        // exactly how the Live TV menu treats its own catch-up category.
        val sorts = SettingsRepository.GuideSort.entries.filter { it == SettingsRepository.GuideSort.ALPHA || it == SettingsRepository.GuideSort.LIVE_TV }
        FilterChipRow(
            labels = sorts.map { stringResource(it.labelRes()) },
            selectedIndex = sorts.indexOf(sort),
            onSelect = { index -> sorts.getOrNull(index)?.let(onSort) },
        )
        HorizontalDivider()
        if (mode == SettingsRepository.GuideView.GRID) {
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
                value = densityPct.toFloat(),
                onValueChange = { onDensity(it.toInt()) },
                valueRange = MIN_DENSITY.toFloat()..MAX_DENSITY.toFloat(),
                modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            )
        }
    }
}

/**
 * Nothing to draw — and which of the four reasons it is decides what the user can do about it.
 *
 * A search that found nothing is not the same problem as having no guide feed at all, and offering
 * "Add EPG" to someone who simply mistyped a channel name helps nobody.
 */
@Composable
private fun EmptyGuide(
    query: String,
    stats: GuideStats?,
    filterHidesEverything: Boolean,
    onAddEpg: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(MobileDimens.GapLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val noEpg = stats?.hasEpgSources == false
        // A filter that hides everything must say so. "Add a playlist" is what this used to show
        // someone with three playlists and eighteen thousand programmes, because the guide could not
        // tell "you have nothing" apart from "your Favourites/Catch-up filter matched nothing".
        val hasGuide = (stats?.programmes ?: 0) > 0
        val filtered = filterHidesEverything && hasGuide
        Text(
            text = when {
                query.isNotBlank() -> stringResource(R.string.content_epg_no_channels_query, query)
                filtered -> stringResource(R.string.content_epg_filter_hides_all)
                noEpg -> stringResource(R.string.content_epg_empty)
                else -> stringResource(R.string.content_epg_add_playlist)
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (query.isBlank() && noEpg && !filtered) {
            Text(
                text = stringResource(R.string.content_epg_add_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = MobileDimens.GapSmall),
            )
            MobileButton(text = stringResource(R.string.content_epg_add), onClick = onAddEpg)
        }
    }
}

/** The guide is being matched: an indeterminate bar, because the matcher counts nothing usefully. */
@Composable
private fun MatchingBanner() {
    Column(Modifier.fillMaxWidth()) {
        GuideNotice(stringResource(R.string.content_epg_matching))
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
        )
    }
}

/** What the run did, until it is tapped away. */
@Composable
private fun MatchSummaryBanner(summary: EpgMatchSummary, onDismiss: () -> Unit) {
    MobileListRow(
        title = epgMatchSummaryText(summary),
        trailing = {
            Text(
                text = stringResource(R.string.common_done),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = onDismiss,
    )
}

/** One line of explanation across the top of the guide. */
@Composable
private fun GuideNotice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            horizontal = MobileDimens.ScreenPaddingH,
            vertical = MobileDimens.GapTiny,
        ),
    )
}

/** The chip's text: a translated label for the two built-in lists, the stored name otherwise. */
@Composable
private fun LiveCategory.label(): String = when (builtIn) {
    LiveCategory.BuiltIn.ALL -> stringResource(R.string.content_epg_all_categories)
    LiveCategory.BuiltIn.FAVORITES -> stringResource(R.string.content_category_favorites)
    else -> title.orEmpty()
}

private fun SettingsRepository.GuideSort.labelRes() = when (this) {
    SettingsRepository.GuideSort.ALPHA -> R.string.content_epg_sort_alpha
    SettingsRepository.GuideSort.PROVIDER -> R.string.content_epg_sort_provider
    SettingsRepository.GuideSort.LIVE_TV -> R.string.content_epg_sort_live
    SettingsRepository.GuideSort.CATCHUP -> R.string.content_epg_sort_catchup
    SettingsRepository.GuideSort.FAVORITES -> R.string.content_epg_sort_favorites
}

private fun SettingsRepository.GuideView.labelRes() = when (this) {
    SettingsRepository.GuideView.GRID -> R.string.settings_view_grid
    SettingsRepository.GuideView.ON_NOW -> R.string.home_row_on_now
    SettingsRepository.GuideView.TIMELINE -> R.string.settings_guide_width_epg
}

/** Programme clocks follow the phone's own locale, so 20:00 and 8:00 PM are both right somewhere. */
@Composable
internal fun rememberGuideTimeFormat(): DateFormat {
    val locales = LocalConfiguration.current.locales
    return remember(locales) { DateFormat.getTimeInstance(DateFormat.SHORT) }
}

/** Today plus the week most providers publish. */

internal const val MIN_DENSITY = 70
internal const val MAX_DENSITY = 130

private const val WIDE_DP = 600
/** Where a row's text column begins — what the bar and the "Next up" line line up with. */
private val ROW_TEXT_INSET =
    MobileDimens.ListRowPaddingH + MobileDimens.ListRowIconSize + MobileDimens.ListRowIconGap

private val LOGO_SIZE = 32.dp
private val TRAILING_ICON = 18.dp

package tv.own.owntv.mobile.ui.screens.guide

import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.launch
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.theme.LocalAnimations
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.util.Date

/**
 * The television's grid, on a touch screen.
 *
 * The channel column is pinned because only the right-hand half of each row scrolls sideways, and
 * every row shares one scroll position, so dragging any of them moves the whole grid — the same
 * relationship the TV app gets from a focus ladder, with a finger instead of a remote.
 *
 * Each row's programmes are read as the row appears, not all at once: a lineup of two thousand
 * channels would otherwise cost two thousand queries to draw twelve rows.
 */
@Composable
internal fun GuideGrid(
    vm: GuideViewModel,
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    window: GuideWindow,
    densityPct: Int,
    onOpen: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onOpenChannel: (Long) -> Unit,
    onMenu: (ChannelEntity) -> Unit,
) {
    val revision by vm.revision.collectAsStateWithLifecycle()
    val timeScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val instant = LocalAnimations.current == AnimationLevel.OFF
    val minuteWidth = (BASE_MINUTE_DP * densityPct / 100f).dp
    val minutePx = with(LocalDensity.current) { minuteWidth.toPx() }
    val times = rememberGuideTimeFormat()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(CHANNEL_COLUMN), contentAlignment = Alignment.Center) {
                IconButton(
                    onClick = {
                        // Where "now" falls in the window. Outside it — another day — this is the start.
                        val minutes = ((System.currentTimeMillis() - window.start) / MINUTE_MS)
                            .coerceAtLeast(0L)
                        val to = (minutes * minutePx).toInt()
                        scope.launch {
                            if (instant) timeScroll.scrollTo(to) else timeScroll.animateScrollTo(to)
                        }
                    },
                ) {
                    Icon(MobileIcons.Schedule, stringResource(R.string.content_epg_jump_now))
                }
            }
            Row(Modifier.horizontalScroll(timeScroll)) {
                var tick = window.start
                while (tick < window.end) {
                    Text(
                        text = times.format(Date(tick)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .width(minuteWidth * TICK_MINUTES)
                            .padding(start = MobileDimens.GapTiny),
                    )
                    tick += TICK_MINUTES * MINUTE_MS
                }
            }
        }
        HorizontalDivider()
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                channels[index]?.let { channel ->
                    GuideGridRow(
                        vm = vm,
                        channel = channel,
                        window = window,
                        minuteWidth = minuteWidth,
                        timeScroll = timeScroll,
                        revision = revision,
                        onOpen = onOpen,
                        onOpenChannel = onOpenChannel,
                        onMenu = onMenu,
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun GuideGridRow(
    vm: GuideViewModel,
    channel: ChannelEntity,
    window: GuideWindow,
    minuteWidth: androidx.compose.ui.unit.Dp,
    timeScroll: androidx.compose.foundation.ScrollState,
    revision: Int,
    onOpen: (ChannelEntity, EpgProgrammeEntity) -> Unit,
    onOpenChannel: (Long) -> Unit,
    onMenu: (ChannelEntity) -> Unit,
) {
    // A new match or a new offset invalidates what was read, so the row is asked for again.
    var programmes by remember(channel.id, window.start, revision) {
        mutableStateOf(vm.cachedRow(channel.id))
    }
    LaunchedEffect(channel.id, window.start, revision) {
        if (programmes == null) programmes = vm.row(channel)
    }

    Row(Modifier.heightIn(min = ROW_HEIGHT)) {
        Row(
            modifier = Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .combinedClickable(
                    onClick = { onOpenChannel(channel.id) },
                    onLongClick = { onMenu(channel) },
                )
                .padding(horizontal = MobileDimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            ChannelLogo(channel)
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(Modifier.horizontalScroll(timeScroll).fillMaxHeight()) {
            val rows = programmes
            if (rows.isNullOrEmpty()) {
                Text(
                    text = stringResource(R.string.content_epg_no_guide),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MobileDimens.GapSmall),
                )
            } else {
                var cursor = window.start
                rows.forEach { programme ->
                    val start = programme.startMs.coerceAtLeast(window.start)
                    val end = programme.stopMs.coerceAtMost(window.end)
                    if (end <= cursor) return@forEach
                    // A hole in the schedule is drawn as one: pretending it is not there slides every
                    // later programme left and the row stops lining up with the clock above it.
                    if (start > cursor) Spacer(Modifier.width(minuteWidth * minutesBetween(cursor, start)))
                    ProgrammeBlock(
                        programme = programme,
                        width = minuteWidth * minutesBetween(maxOf(start, cursor), end),
                        onClick = { onOpen(channel, programme) },
                    )
                    cursor = end
                }
            }
        }
    }
}

@Composable
private fun ProgrammeBlock(programme: EpgProgrammeEntity, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(BLOCK_GAP)
            .clip(RoundedCornerShape(MobileDimens.GapSmall))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = MobileDimens.GapSmall),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = programme.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One channel, read top to bottom.
 *
 * The grid answers "what else is on"; this answers "what is on this channel tonight", which is the
 * question a phone is actually held up for. The channel strip is paged like the lists are, so the
 * whole lineup is reachable rather than the first page of it.
 */
@Composable
internal fun GuideTimeline(
    vm: GuideViewModel,
    channels: LazyPagingItems<ChannelEntity>,
    listState: LazyListState,
    onOpen: (ChannelEntity, EpgProgrammeEntity) -> Unit,
) {
    val guideWindow by vm.window.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<ChannelEntity?>(null) }
    val channel = selected ?: channels.itemSnapshotList.items.firstOrNull()
    val times = rememberGuideTimeFormat()

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = MobileDimens.ScreenPaddingH,
                vertical = MobileDimens.GapSmall,
            ),
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                channels[index]?.let { entry ->
                    FilterChip(
                        selected = entry.id == channel?.id,
                        onClick = { selected = entry },
                        label = { Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        HorizontalDivider()
        if (channel != null) {
            val revision by vm.revision.collectAsStateWithLifecycle()
            var programmes by remember(channel.id, revision, guideWindow) { mutableStateOf(vm.cachedRow(channel.id)) }
            LaunchedEffect(channel.id, revision, guideWindow) { if (programmes == null) programmes = vm.row(channel) }
            val rows = programmes
            if (rows != null && rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.content_epg_no_guide),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(MobileDimens.ScreenPaddingH),
                )
            }
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows.orEmpty(), key = { it.id }) { programme ->
                    MobileListRow(
                        title = programme.title,
                        subtitle = stringResource(
                            R.string.content_epg_time_range,
                            times.format(Date(programme.startMs)),
                            times.format(Date(programme.stopMs)),
                        ),
                        onClick = { onOpen(channel, programme) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun minutesBetween(from: Long, to: Long): Float =
    ((to - from).coerceAtLeast(0L)).toFloat() / MINUTE_MS

private const val MINUTE_MS = 60_000L

/** Half an hour is the unit every guide is drawn in, so it is what the ruler counts. */
private const val TICK_MINUTES = 30

/** A minute of guide, at 100%: half an hour of it is a comfortable 120 dp. */
private const val BASE_MINUTE_DP = 4f

private val CHANNEL_COLUMN = 96.dp
private val ROW_HEIGHT = 64.dp
private val BLOCK_GAP = 2.dp

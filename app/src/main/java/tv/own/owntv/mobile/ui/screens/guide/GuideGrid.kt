package tv.own.owntv.mobile.ui.screens.guide

import tv.own.owntv.core.theme.AnimationLevel
import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.derivedStateOf
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
import tv.own.owntv.core.R
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
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
            Row(Modifier.weight(1f).horizontalScroll(timeScroll).width(minuteWidth * minutesBetween(window.start, window.end))) {
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
    var retry by remember(channel.id, window, revision) { mutableStateOf(0) }
    val state by rememberGuideRow(vm, channel, window, revision, retry)
    val rows = (state as? GuideRowState.Data)?.rows.orEmpty()
    val cells = remember(rows, window) { guideProgrammeCells(rows, window) }
    val density = LocalDensity.current
    val minutePx = with(density) { minuteWidth.toPx() }
    val rowHeight = ROW_HEIGHT * density.fontScale.coerceAtLeast(1f)
    Row(Modifier.height(rowHeight)) {
        Row(
            modifier = Modifier.width(CHANNEL_COLUMN).fillMaxHeight()
                .combinedClickable(onClick = { onOpenChannel(channel.id) }, onLongClick = { onMenu(channel) })
                .padding(horizontal = MobileDimens.GapSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
        ) {
            ChannelLogo(channel)
            Text(channel.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
            val viewportMinutes = with(density) { maxWidth.toPx() } / minutePx
            // Read pixel scroll only here, quantized to half-hour bands rather than every frame.
            val band by remember(timeScroll, minutePx) {
                derivedStateOf { (timeScroll.value / minutePx / TICK_MINUTES).toLong() }
            }
            val from = window.start + (band - 1) * TICK_MINUTES * MINUTE_MS
            val until = window.start + ((band + 2) * TICK_MINUTES + viewportMinutes.toLong()) * MINUTE_MS
            val visible = remember(cells, from, until) { guideVisibleCells(cells, from, until) }
            // Every row has the exact same full-day extent, including empty rows and trailing gaps.
            Box(Modifier.fillMaxSize().horizontalScroll(timeScroll)) {
                Box(Modifier.width(minuteWidth * minutesBetween(window.start, window.end)).fillMaxHeight()) {
                    visible.forEach { cell ->
                        androidx.compose.runtime.key(cell.programme.id, cell.programme.sourceId, cell.startMs, cell.endMs) {
                            ProgrammeBlock(cell.programme, minuteWidth * minutesBetween(cell.startMs, cell.endMs),
                                Modifier.offset(x = minuteWidth * minutesBetween(window.start, cell.startMs))) {
                                onOpen(channel, cell.programme)
                            }
                        }
                    }
                }
            }
            if (state !is GuideRowState.Data) GuideRowMessage(state, onRetry = { retry++ })
        }
    }
}

@Composable
private fun GuideRowMessage(state: GuideRowState, onRetry: () -> Unit) {
    Row(Modifier.padding(horizontal = MobileDimens.GapSmall), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(when (state) {
            GuideRowState.Loading -> tv.own.owntv.mobile.R.string.own_guide_loading
            GuideRowState.Error -> tv.own.owntv.mobile.R.string.own_guide_load_error
            else -> R.string.content_epg_no_guide
        }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false))
        if (state == GuideRowState.Error) MobileButton(stringResource(tv.own.owntv.mobile.R.string.own_guide_retry),
            onClick = onRetry, style = MobileButtonStyle.TEXT)
    }
}

@Composable
private fun ProgrammeBlock(programme: EpgProgrammeEntity, width: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .padding(BLOCK_GAP)
            .clip(RoundedCornerShape(MobileDimens.GapSmall))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(MobileDimens.GapSmall))
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
    val context by vm.listContext.collectAsStateWithLifecycle()
    val category by vm.selected.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var selected by remember(context.profileId, context.liveSourceIds, category, query) { mutableStateOf<ChannelEntity?>(null) }
    val channel = selected?.let { chosen -> channels.itemSnapshotList.items.firstOrNull { it.id == chosen.id } ?: chosen }
        ?: channels.itemSnapshotList.items.firstOrNull()
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
            var retry by remember(channel.id, revision, guideWindow) { mutableStateOf(0) }
            val state by rememberGuideRow(vm, channel, guideWindow, revision, retry)
            val rows = (state as? GuideRowState.Data)?.rows.orEmpty()
            if (state !is GuideRowState.Data) GuideRowMessage(state, onRetry = { retry++ })
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.id }, contentType = { "programme" }) { programme ->
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

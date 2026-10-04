package tv.own.owntv.mobile.ui.screens.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.player.CatchupOptions
import tv.own.owntv.mobile.ui.player.CatchupSheet as JumpBackSheet
import tv.own.owntv.mobile.ui.player.VideoStage
import tv.own.owntv.mobile.ui.screens.library.VodTuner
import tv.own.owntv.mobile.ui.shell.LocalMiniRequested
import tv.own.owntv.mobile.ui.theme.MobileDimens
import java.text.DateFormat
import java.util.Date

/** What the lower half of the screen is showing. Catch-up is a chip too, but it opens a sheet. */
private enum class DetailTab { GUIDE, CHANNELS }

/**
 * A channel, playing.
 *
 * The picture stays in a 16:9 box at the top rather than filling the screen, because on a phone the
 * interesting part is usually what is on *next* and which channel to go to — full screen is one
 * rotation away. Underneath, the two panels the TV app shows beside the video become two chips.
 */
@Composable
fun ChannelDetailScreen(
    channelId: Long,
    openCatchup: Boolean,
    onFullscreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    vm: ChannelDetailViewModel = koinViewModel(),
    tuner: LiveTuner = koinInject(),
    vodTuner: VodTuner = koinInject(),
) {
    LaunchedEffect(channelId) { vm.load(channelId) }

    // This screen is watching, the same as the full screen player is — it just watches in a smaller
    // box — so Back means the same thing here: finished, stop the stream. Leaving it running would put
    // a mini player on the list the user just went back to, which is a window they never asked for.
    BackHandler {
        tuner.stop()
        vodTuner.stop()
        onBack()
    }

    // Back is not the only way out: a tab in the bottom bar or the rail leaves this screen without
    // ever reaching the handler above, and the stream went on playing behind whatever the user opened
    // next. Leaving by any route now stops it — **except** the two departures that are meant to keep
    // it: opening the full screen player, and the player's own mini-player button, which pops this
    // screen on its way past and says through [LocalMiniRequested] that the stream was asked for.
    val miniRequested = LocalMiniRequested.current
    var toFullscreen by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (!toFullscreen && !miniRequested.value) {
                tuner.stop()
                vodTuner.stop()
            }
        }
    }
    val openFullscreen = {
        toFullscreen = true
        onFullscreen()
    }

    val channel by vm.channel.collectAsStateWithLifecycle()
    val nowNext by vm.nowNext.collectAsStateWithLifecycle()
    val siblings by vm.siblings.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(DetailTab.GUIDE) }
    var catchupOpen by remember { mutableStateOf(openCatchup) }

    val hasCatchup = channel?.catchup == true
    val labels = buildList {
        add(stringResource(R.string.content_epg_title))
        add(stringResource(R.string.content_channel_overlay_title))
        if (hasCatchup) add(stringResource(R.string.content_catchup))
    }

    Column(modifier.fillMaxSize()) {
        // Tapping the picture is how this screen reaches the full screen player; rotating the phone
        // is the other way, and neither one restarts the stream.
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(VIDEO_ASPECT)
                .background(Color.Black)
                .clickable(onClick = openFullscreen),
        ) {
            VideoStage(player = vm.player, modifier = Modifier.fillMaxSize())
        }
        FilterChipRow(
            labels = labels,
            selectedIndex = tab.ordinal,
            // The catch-up chip is a door, not a tab: it opens the picker and leaves the panel alone.
            onSelect = { index ->
                if (index < DetailTab.entries.size) tab = DetailTab.entries[index] else catchupOpen = true
            },
        )
        when (tab) {
            DetailTab.GUIDE -> GuidePanel(nowNext = nowNext)
            DetailTab.CHANNELS -> LazyColumn(Modifier.fillMaxSize()) {
                items(siblings, key = { it.id }) { sibling ->
                    MobileListRow(title = sibling.name, onClick = { vm.switchTo(sibling) })
                }
            }
        }
    }

    // "Go back to…" is the same sheet the full-screen player opens, reached from the same chip: a
    // programme name is the usual way in, a bare time is the way in when the guide has no listing.
    var jumpOpen by remember { mutableStateOf(false) }

    if (catchupOpen) {
        CatchupSheet(
            channelName = channel?.name.orEmpty(),
            load = vm::catchupProgrammes,
            onPick = { vm.playCatchup(it) },
            onJumpBack = { catchupOpen = false; jumpOpen = true },
            onDismiss = { catchupOpen = false },
        )
    }
    if (jumpOpen) {
        JumpBackSheet(
            options = CatchupOptions(
                offsetsSec = tuner.jumpOptions(),
                windowSec = tuner.archiveWindowSec(),
                onPick = tuner::jumpBackTo,
            ),
            onDismiss = { jumpOpen = false },
        )
    }
}

/** What is on now, in full, then what follows it. */
@Composable
private fun GuidePanel(nowNext: tv.own.owntv.core.live.EpgNowNext?) {
    val times = rememberTimeFormat()
    val now = nowNext?.now
    if (now == null) {
        Text(
            text = stringResource(R.string.content_no_epg),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(MobileDimens.ScreenPaddingH),
        )
        return
    }
    val separator = stringResource(R.string.content_metadata_separator)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(MobileDimens.ScreenPaddingH)) {
                Text(
                    text = stringResource(R.string.content_live_now_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = now.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = times.format(Date(now.startMs)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!now.description.isNullOrBlank()) {
                    Text(
                        text = now.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = MobileDimens.GapSmall),
                    )
                }
            }
            HorizontalDivider()
        }
        // "Up next" and everything after it — the same list the TV app's guide column shows.
        val upcoming = listOfNotNull(nowNext.next) + nowNext.upcoming.filter { it != nowNext.next }
        val first = upcoming.firstOrNull()
        if (first != null) {
            item {
                Text(
                    // The label carries the time the next programme starts; without it the heading
                    // reads as a raw placeholder.
                    text = stringResource(R.string.content_live_next_label, times.format(Date(first.startMs))),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(
                        horizontal = MobileDimens.ScreenPaddingH,
                        vertical = MobileDimens.GapSmall,
                    ),
                )
            }
        }
        // Each upcoming programme gets its synopsis beside the start time, the same way the
        // television's preview pane shows one — a title alone rarely says which episode this is.
        items(upcoming) { entry ->
            val time = times.format(Date(entry.startMs))
            val synopsis = entry.description?.takeIf { it.isNotBlank() }
            MobileListRow(
                title = entry.title,
                subtitle = if (synopsis == null) time else time + separator + synopsis,
                subtitleMaxLines = if (synopsis == null) 1 else 3,
            )
        }
    }
}

/**
 * The catch-up picker: what this channel's archive still holds, newest first.
 *
 * On the TV app this is a centred dialog. Here it is a sheet, so the list of programmes rises under
 * the thumb and the picture keeps playing above it.
 */
@Composable
private fun CatchupSheet(
    channelName: String,
    load: suspend () -> List<EpgProgrammeEntity>,
    onPick: (EpgProgrammeEntity) -> Unit,
    onJumpBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    var programmes by remember { mutableStateOf<List<EpgProgrammeEntity>?>(null) }
    LaunchedEffect(Unit) { programmes = load() }
    val times = rememberTimeFormat()
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val separator = stringResource(R.string.content_epg_bits_separator)

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.content_catchup_title, channelName),
    ) {
        val list = programmes
        if (list != null && list.isEmpty()) {
            Text(
                text = stringResource(R.string.content_catchup_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MobileDimens.ScreenPaddingH),
            )
        }
        LazyColumn(Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp / 2).dp)) {
            items(list.orEmpty(), key = { it.id }) { programme ->
                MobileListRow(
                    title = programme.title,
                    subtitle = dates.format(Date(programme.startMs)) + separator +
                        times.format(Date(programme.startMs)),
                    onClick = { onPick(programme); onDismiss() },
                )
            }
            item(key = "jump-back") {
                MobileListRow(
                    title = stringResource(R.string.content_catchup_jump),
                    onClick = onJumpBack,
                )
            }
        }
    }
}

/** Programme clocks follow the phone's own locale, so 20:00 and 8:00 PM are both right somewhere. */
@Composable
private fun rememberTimeFormat(): DateFormat {
    val locales = LocalConfiguration.current.locales
    return remember(locales) { DateFormat.getTimeInstance(DateFormat.SHORT) }
}

private const val VIDEO_ASPECT = 16f / 9f

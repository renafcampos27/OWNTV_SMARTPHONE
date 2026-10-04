package tv.own.owntv.mobile.ui.screens.live

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import tv.own.owntv.mobile.ui.screens.settings.SimpleModePreferences
import tv.own.owntv.mobile.ui.screens.settings.SimpleModeOptions
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.displayLogoUrl
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.CategoryPickerSheet
import tv.own.owntv.mobile.ui.components.ChannelLogoImage
import tv.own.owntv.mobile.ui.components.BrowseCategorySheet
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.TwoPane
import tv.own.owntv.mobile.ui.components.mobileGroupPlate
import tv.own.owntv.mobile.ui.nav.isExpandedWidth
import tv.own.owntv.mobile.ui.screens.ObeyScrollToTop
import tv.own.owntv.mobile.ui.shell.LocalStreamOnScreen
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * Live TV: a strip of categories, a list of channels, and a long-press menu on each one.
 *
 * The TV app shows three panels at once — rail, list, preview — because it has the width for it and
 * a remote that moves between them. A phone has neither, so the same three things become one
 * scrolling list, a chip strip above it, and a screen you open by tapping a channel.
 *
 * A tablet has the width, so at expanded size the third panel comes back: the list on the left and
 * the channel playing beside it. It starts empty rather than tuning the first channel by itself —
 * opening Live TV is not a request to watch something.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(
    scrollToTop: SharedFlow<String>,
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Pins the list to one folder and takes the chip strip away — how the Favourites and History
     * screens show channels without a second copy of this list existing.
     */
    lockedKey: LiveKey? = null,
    vm: LiveViewModel = koinViewModel(),
) {
    // Locking and unlocking are one pair: the pin belongs to this screen's lifetime, not to the view
    // model's. On the television that view model is a single instance shared with the browse section,
    // so a pin left behind froze its category rail. `DisposableEffect` (not `LaunchedEffect`) also
    // means the pin is in place before the first frame, so the list never flashes the wrong folder.
    // Channels kept with "Add to Multiview" are spent by playing one. While any are waiting, a tap
    // plays rather than browses — see the tap handler below.
    val tuner: LiveTuner = koinInject()
    val multiviewSelection by tuner.multiviewSelection.collectAsStateWithLifecycle()
    val multiviewPending = multiviewSelection.isNotEmpty()

    if (lockedKey != null) {
        val pinned = lockedKey
        DisposableEffect(pinned) {
            vm.lock(pinned)
            onDispose { vm.unlock() }
        }
    }

    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels = vm.channels.collectAsLazyPagingItems()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val favorites by vm.favoriteIds.collectAsStateWithLifecycle()
    val providers by vm.providerNames.collectAsStateWithLifecycle()
    val showNumbers by vm.showChannelNumbers.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val guideDelay by vm.guideDelayMs.collectAsStateWithLifecycle()
    val versionPolicy by vm.channelVersionSettings.collectAsStateWithLifecycle()
    val playingChannel by tuner.channel.collectAsStateWithLifecycle()
    var returnChannelId by rememberSaveable { mutableStateOf<Long?>(null) }
    var returnGroup by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(playingChannel?.id, versionPolicy) {
        playingChannel?.let { returnChannelId = it.id; returnGroup = ChannelVersionPolicy.groupKey(it, versionPolicy) }
    }

    val listState = rememberLazyListState()
    listState.ObeyScrollToTop(route = "live", scrollToTop = scrollToTop)

    val simple by SimpleModePreferences.observe(LocalContext.current).collectAsStateWithLifecycle(SimpleModeOptions())
    var versionsFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var editVersions by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<ChannelEntity?>(null) }
    var categoryPicker by remember { mutableStateOf(false) }
    var categoryMenuFor by remember { mutableStateOf<LiveCategory?>(null) }
    // Survives a rotation, so turning a tablet keeps the channel you were watching beside the list.
    var previewing by rememberSaveable { mutableStateOf<Long?>(null) }
    val twoPane = isExpandedWidth()

    // While the pane is showing the channel, the picture is already on screen, so the shell must not
    // also put a floating window over it. Cleared on the way out, or leaving Live TV with a channel
    // open would take the mini player away on every other screen too.
    val streamOnScreen = LocalStreamOnScreen.current
    val paneHasChannel = twoPane && previewing != null
    DisposableEffect(paneHasChannel) {
        streamOnScreen.value = paneHasChannel
        onDispose { streamOnScreen.value = false }
    }

    // The guide is read for what is actually on screen. Watching the visible range rather than each
    // row means one batched query per scroll settle instead of one per row appearing.
    LaunchedEffect(listState, channels, guideDelay) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
            .distinctUntilChanged()
            .collectLatest { indices ->
                if (guideDelay > 0) delay(guideDelay.toLong())
                vm.loadNowPlaying(indices.mapNotNull { channels.peek(it) })
            }
    }

    // Changing category scrolls back to the top: the position of the old list means nothing in the new one.
    LaunchedEffect(selected) { listState.scrollToItem(0) }
    LaunchedEffect(simple.hideCategories, selected) { if (simple.hideCategories) vm.select(LiveKey.All) }

    val listPane = @Composable {
    Column(Modifier.fillMaxSize()) {
        if (lockedKey == null && !simple.hideCategories) Box(Modifier.fillMaxWidth()) {
            FilterChipRow(
                labels = categories.map { it.label() },
                selectedIndex = categories.indexOfFirst { it.key == selected },
                onSelect = { index -> categories.getOrNull(index)?.let { vm.select(it.key) } },
                modifier = Modifier.padding(end = MobileDimens.TouchTarget),
                // All, Favorites, History and Catch-up are not folders: there is nothing to hide or move.
                onLongPress = { index ->
                    categoryMenuFor = categories.getOrNull(index)?.takeIf { it.builtIn == null }
                },
                onLongPressLabel = stringResource(R.string.settings_customize_categories),
            )
            IconButton(
                onClick = { categoryPicker = true },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Icon(MobileIcons.Search, stringResource(R.string.content_search_categories))
            }
        }
        if (categoryPicker) {
            CategoryPickerSheet(
                labels = categories.map { it.label() },
                selectedIndex = categories.indexOfFirst { it.key == selected },
                onSelect = { index -> categories.getOrNull(index)?.let { vm.select(it.key) } },
                onDismiss = { categoryPicker = false },
            )
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (channels.itemCount == 0) {
                EmptyChannels()
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().mobileGroupPlate()) {
                    items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                        val channel = channels[index]
                        if (channel != null) {
                            ChannelRow(
                                channel = channel,
                                displayName = vm.displayName(channel),
                                isCurrent = returnChannelId == channel.id || (versionPolicy.groupChannelVersions && returnGroup == ChannelVersionPolicy.groupKey(channel, versionPolicy)),
                                number = channel.number?.takeIf { showNumbers && it > 0 },
                                nowPlaying = nowPlaying[channel.id],
                                providerName = providers[channel.sourceId],
                                isFavorite = false,
                                // Beside the list the channel opens in the pane; on a phone it is
                                // a screen of its own, which is the same screen either way.
                                onClick = {
                                    returnChannelId = channel.id
                                    returnGroup = ChannelVersionPolicy.groupKey(channel, versionPolicy)
                                    when {
                                        // Channels are waiting to become a Multiview grid, so the
                                        // tap that says "now" must actually start playing. Sent to
                                        // the channel page instead, it took two taps to open the
                                        // grid and the first one looked like it had done nothing.
                                        multiviewPending -> {
                                            // Selected, not started: the grid is about to open and
                                            // would only have to stop it again — and losing that
                                            // race is what played a second channel under the tiles.
                                            tuner.selectWithoutPlaying(channel)
                                            onOpenPlayer()
                                        }
                                        twoPane -> previewing = channel.id
                                        else -> onOpenChannel(channel.id, false)
                                    }
                                },
                                onLongClick = { versionsFor = channel; editVersions = false },
                                onMenu = { menuFor = channel },
                            )
                        }
                    }
                }
            }
        }
    }
    }

    if (twoPane) {
        TwoPane(
            list = listPane,
            detail = {
                val channelId = previewing
                if (channelId == null) {
                    SelectAChannel()
                } else {
                    ChannelDetailScreen(
                        channelId = channelId,
                        openCatchup = false,
                        onFullscreen = onOpenPlayer,
                        // Back here empties the pane rather than leaving Live TV: the list is still
                        // on screen, so the thing the user is finished with is the channel.
                        onBack = { previewing = null },
                    )
                }
            },
            modifier = modifier,
        )
    } else {
        Box(modifier) { listPane() }
    }

    versionsFor?.let { owner ->
        ChannelVersionsSheet(owner, vm, playOnly = !editVersions,
            onPlay = { tuner.tune(it.id, explicitVersion = true); onOpenPlayer() },
            onDismiss = { versionsFor = null })
    }
    menuFor?.let { channel ->
        ChannelMenu(
            channel = channel,
            selected = selected,
            isFavorite = false,
            originName = categories.firstOrNull { it.key == selected }?.label().orEmpty(),
            vm = vm,
            onOpenCatchup = { onOpenChannel(channel.id, true) },
            onOpenVersions = { versionsFor = channel; editVersions = true; menuFor = null },
            onDismiss = { menuFor = null },
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

/** The chip's text: a translated label for the four built-in lists, the stored name otherwise. */
@Composable
private fun LiveCategory.label(): String = when (builtIn) {
    LiveCategory.BuiltIn.ALL -> stringResource(R.string.content_category_all_channels)
    LiveCategory.BuiltIn.FAVORITES -> stringResource(R.string.content_category_favorites)
    LiveCategory.BuiltIn.HISTORY -> stringResource(R.string.content_category_history)
    LiveCategory.BuiltIn.CATCHUP -> stringResource(R.string.content_catchup)
    null -> title.orEmpty()
}

/** The preview pane before a channel is picked — the television's own sentence for the same panel. */
@Composable
private fun SelectAChannel() {
    Box(Modifier.fillMaxSize().padding(MobileDimens.GapLarge), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.content_preview_select_channel),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyChannels() {
    Box(Modifier.fillMaxSize().padding(MobileDimens.GapLarge), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.content_no_channels_here),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One channel: its number, its logo, its name, and what is on it now.
 *
 * The second line is deliberately shared between the programme title and the playlist name — a
 * phone row has one line to spare, and which of the two is worth showing depends on whether the
 * user has a guide and more than one playlist.
 */
@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    displayName: String,
    isCurrent: Boolean,
    number: Int?,
    nowPlaying: String?,
    providerName: String?,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
) {
    var rowFocused by remember(channel.id) { mutableStateOf(false) }
    val rowFocus = remember(channel.id) { FocusRequester() }
    LaunchedEffect(isCurrent) { if (isCurrent) runCatching { rowFocus.requestFocus() } }
    val keyScope = rememberCoroutineScope()
    var menuTimer by remember(channel.id) { mutableStateOf<Job?>(null) }
    var menuOpened by remember(channel.id) { mutableStateOf(false) }
    val separator = stringResource(R.string.content_epg_bits_separator)
    val subtitle = listOfNotNull(nowPlaying, providerName).takeIf { it.isNotEmpty() }?.joinToString(separator)

    MobileListRow(
        modifier = Modifier.focusRequester(rowFocus).onFocusChanged { rowFocused = it.isFocused; if (!it.hasFocus) menuTimer?.cancel() }.onPreviewKeyEvent { key ->
            val event = key.nativeKeyEvent
            if (!rowFocused || event.keyCode !in listOf(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)) false
            else {
                if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    menuOpened = false
                    menuTimer?.cancel()
                    menuTimer = keyScope.launch { delay(3_000); menuOpened = true; onMenu() }
                } else if (event.action == android.view.KeyEvent.ACTION_UP) {
                    menuTimer?.cancel()
                    if (!menuOpened) when (channelPressAction(event.eventTime - event.downTime)) {
                        ChannelPressAction.PLAY -> onClick()
                        ChannelPressAction.VERSIONS -> onLongClick()
                        ChannelPressAction.MENU -> onMenu()
                    }
                }
                true
            }
        },
        selected = isCurrent,
        title = displayName,
        subtitle = subtitle,
        leading = { ChannelLogo(channel, number) },
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapTiny)) {
                IconButton(onClick = onLongClick) { Icon(MobileIcons.VideoLibrary, stringResource(R.string.channel_versions_title)) }
                IconButton(onClick = onMenu) { Icon(MobileIcons.MoreHoriz, stringResource(R.string.common_nav_more)) }
                if (channel.catchup) {
                    Icon(
                        imageVector = MobileIcons.History,
                        contentDescription = stringResource(R.string.content_catchup),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(TRAILING_ICON),
                    )
                }
                if (isFavorite) {
                    Icon(
                        imageVector = MobileIcons.Star,
                        contentDescription = stringResource(R.string.content_category_favorites),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(TRAILING_ICON),
                    )
                }
            }
        },
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** The logo, with the channel number under it when the Channel numbers setting is on. */
@Composable
private fun ChannelLogo(channel: ChannelEntity, number: Int?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val LOGO_SIZE = 32.dp
private val TRAILING_ICON = 18.dp

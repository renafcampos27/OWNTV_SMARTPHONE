package tv.own.owntv.mobile.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileIcons
import tv.own.owntv.mobile.ui.nav.MobileDestination
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.screens.library.LibraryScreen
import tv.own.owntv.mobile.ui.screens.library.LibraryTab
import tv.own.owntv.mobile.ui.screens.live.LiveScreen

/** Which of the three types the screen is showing. */
private enum class UserDataTab { LIVE, MOVIES, SERIES }

/**
 * Everything you starred, in one place — for the first time on either app.
 *
 * Three chips over one stage, the shape Settings → Customize already uses, so it reads as part of
 * the app rather than a screen bolted onto it. Under each chip is **the list that type already
 * has**, pinned to its Favorites folder: the same channel row with its now-playing line, the same
 * poster grid at the user's own column count, the same long-press menu with un-favourite in it. A
 * favourite film must look like the same film in the Library, because it is.
 *
 * A chip whose count is zero is still shown, with that type's own empty state — a user who has
 * starred only channels must see *why* Movies is empty rather than wonder whether it is broken.
 */
@Composable
fun FavoritesScreen(
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) = UserDataScreen(
    key = LiveKey.Favorites,
    onOpenChannel = onOpenChannel,
    onOpenItem = onOpenItem,
    onOpenPlayer = onOpenPlayer,
    modifier = modifier,
)

/**
 * What you watched, newest first, with how far it got.
 *
 * The same three-chip screen as [FavoritesScreen] over history instead of favourites, and Clear
 * history is its own action — reached from here, where it acts, rather than from three levels inside
 * Settings. The dialog it opens is the one that already shipped, scope for scope.
 *
 * A series entry in history is an **episode**, so tapping it resumes that episode. Favourites →
 * Series opens the series instead: the data is different, so the tap is different.
 */
@Composable
fun WatchHistoryScreen(
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
) = UserDataScreen(
    key = LiveKey.History,
    onOpenChannel = onOpenChannel,
    onOpenItem = onOpenItem,
    onOpenPlayer = onOpenPlayer,
    modifier = modifier,
)

/**
 * The screen both of them are. The only difference is which folder is pinned and, for history, the
 * Clear action — so it is one implementation with one branch rather than two near-copies.
 */
@Composable
private fun UserDataScreen(
    key: LiveKey,
    onOpenChannel: (channelId: Long, openCatchup: Boolean) -> Unit,
    onOpenItem: (LibraryTab, Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    counts: MoreCountsViewModel = koinViewModel(),
    settingsVm: tv.own.owntv.mobile.ui.screens.settings.SettingsViewModel = koinViewModel(),
) {
    val history = key == LiveKey.History
    var tab by rememberSaveable { mutableStateOf(UserDataTab.LIVE) }
    var clearing by remember { mutableStateOf(false) }
    val counted by (if (history) counts.history else counts.favorites).collectAsStateWithLifecycle()

    // The hosted lists respond to a nav-bar tap on their own tab, which this screen is not one of —
    // nothing will ever emit here, and that is the point.
    val noScrollToTop = remember { MutableSharedFlow<String>() }

    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth()) {
            FilterChipRow(
                labels = UserDataTab.entries.map { it.label(counted) },
                selectedIndex = tab.ordinal,
                onSelect = { index -> UserDataTab.entries.getOrNull(index)?.let { tab = it } },
                // Room for Clear at the end of the strip, the way the browse screens leave room for
                // their own actions.
                modifier = if (history) Modifier.padding(end = MobileDimens.TouchTarget) else Modifier,
            )
            if (history) {
                IconButton(
                    onClick = { clearing = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(MobileIcons.Delete, stringResource(R.string.settings_clear_history))
                }
            }
        }
        when (tab) {
            UserDataTab.LIVE -> LiveScreen(
                scrollToTop = noScrollToTop,
                onOpenChannel = onOpenChannel,
                onOpenPlayer = onOpenPlayer,
                lockedKey = key,
                modifier = Modifier.fillMaxSize(),
            )
            UserDataTab.MOVIES, UserDataTab.SERIES -> {
                val libraryTab =
                    if (tab == UserDataTab.MOVIES) LibraryTab.MOVIES else LibraryTab.SERIES
                LibraryScreen(
                    scrollToTop = noScrollToTop,
                    route = MobileDestination.LIBRARY.route,
                    fixedTab = libraryTab,
                    lockedKey = key,
                    onOpenItem = onOpenItem,
                    onPlay = onOpenPlayer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (clearing) {
        ClearHistoryFlow(
            onClear = { scope -> settingsVm.clearHistory(scope) },
            onDismiss = { clearing = false },
        )
    }
}

/** "Live TV · 28" — the type's own name and how many of it there are. */
@Composable
private fun UserDataTab.label(counts: TypeCounts): String {
    val name = stringResource(
        when (this) {
            UserDataTab.LIVE -> R.string.common_nav_live_tv
            UserDataTab.MOVIES -> R.string.common_nav_movies
            UserDataTab.SERIES -> R.string.common_nav_series
        },
    )
    val count = when (this) {
        UserDataTab.LIVE -> counts.live
        UserDataTab.MOVIES -> counts.movies
        UserDataTab.SERIES -> counts.series
    }
    // The same "a · b" joiner the channel rows use for programme and playlist, so one separator
    // serves the whole app and cannot drift between screens.
    return name + stringResource(R.string.content_epg_bits_separator) + count
}

package tv.own.owntv.mobile.ui.nav

import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.SharedFlow
import tv.own.owntv.mobile.BuildConfig
import tv.own.owntv.mobile.dev.ThemeGalleryScreen
import tv.own.owntv.mobile.ui.screens.DevRoute
import tv.own.owntv.mobile.ui.screens.MoreLeaf
import tv.own.owntv.mobile.ui.screens.MoreLeafPage
import tv.own.owntv.mobile.ui.screens.MoreScreen
import tv.own.owntv.mobile.ui.player.PlayerScreen
import tv.own.owntv.mobile.ui.screens.downloads.DownloadsScreen
import tv.own.owntv.mobile.ui.screens.guide.GuideScreen
import tv.own.owntv.mobile.ui.screens.home.HomeScreen
import tv.own.owntv.mobile.ui.screens.library.DetailScreen
import tv.own.owntv.mobile.ui.screens.library.LibraryScreen
import tv.own.owntv.mobile.ui.screens.library.LibraryTab
import tv.own.owntv.mobile.ui.screens.live.ChannelDetailScreen
import tv.own.owntv.mobile.ui.screens.live.LiveScreen
import tv.own.owntv.mobile.ui.screens.search.SearchScreen
import tv.own.owntv.mobile.ui.screens.settings.SettingsGroup
import tv.own.owntv.mobile.ui.screens.settings.SettingsGroupPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsLeaf
import tv.own.owntv.mobile.ui.screens.settings.SettingsLeafPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsScreen
import tv.own.owntv.mobile.ui.setup.SetupFlow
import tv.own.owntv.mobile.ui.shell.LocalMiniRequested
import tv.own.owntv.mobile.ui.theme.LocalMobileMotion

/**
 * Every route in the app.
 *
 * A destination is a route rather than an expanding block so that the system back gesture is what
 * closes it, and so each keeps its own scroll state.
 *
 * The `when` below is exhaustive over [MobileDestination] and deliberately has no `else`: a
 * destination added without a screen is then a compile error rather than a blank page at runtime.
 * It carried one until Plan 4 finished, along with a `PlaceholderScreen` to fill it; every
 * destination has had a real screen for some time, so both are gone.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MobileNavHost(
    navController: NavHostController,
    scrollToTop: SharedFlow<String>,
    modifier: Modifier = Modifier,
) {
    // One layout around the whole graph: it is what lets a poster tile on one screen and the header
    // image on the next be understood as the same picture rather than two pictures crossfading.
    SharedTransitionLayout(modifier = modifier) {
        CompositionLocalProvider(LocalSharedTransition provides this) {
    // Navigation's own default is a 700 ms crossfade that ignores the reduce-motion setting. The
    // app's effects spring is quicker, and it is genuinely nothing at all when animations are off.
    val fade = LocalMobileMotion.current.fast<Float>()
    NavHost(
        navController = navController,
        startDestination = MobileDestination.LIVE.route,
        enterTransition = { fadeIn(fade) },
        exitTransition = { fadeOut(fade) },
    ) {
        MobileDestination.entries.forEach { destination ->
            composable(destination.route) {
                CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                when (destination) {
                    MobileDestination.MORE -> MoreScreen(
                        scrollToTop = scrollToTop,
                        // A plain push, NOT `navigateToTab`. Downloads and Settings are reached only
                        // from here, and switching to them as tabs popped More off the back stack —
                        // so Back out of Settings landed on Home instead of on the hub it was opened
                        // from. Pushed, Back returns to More like every other row here.
                        onNavigate = {
                            navController.navigate(it.route) { launchSingleTop = true }
                        },
                        onOpenLeaf = { navController.navigate(it.route) { launchSingleTop = true } },
                        onDevRoute = { navController.navigate(it.route) },
                    )
                    MobileDestination.HOME -> HomeScreen(
                        scrollToTop = scrollToTop,
                        onOpenChannel = { channelId ->
                            navController.navigate(channelRoute(destination, channelId, false))
                        },
                        onOpenMovie = { id ->
                            navController.navigate(detailRoute(destination.route, LibraryTab.MOVIES, id))
                        },
                        onOpenSeries = { id ->
                            navController.navigate(detailRoute(destination.route, LibraryTab.SERIES, id))
                        },
                        onPlayerOpened = { navController.navigate(PLAYER_ROUTE) },
                        onOpenSearch = { query -> navController.navigate(searchRoute(query)) },
                    )
                    MobileDestination.GUIDE -> GuideScreen(
                        scrollToTop = scrollToTop,
                        onOpenChannel = { channelId ->
                            navController.navigate(channelRoute(MobileDestination.LIVE, channelId, false))
                        },
                        onAddEpg = { navController.navigate(SettingsLeaf.EPG_SOURCES.route) },
                    )
                    MobileDestination.LIVE -> LiveScreen(
                        scrollToTop = scrollToTop,
                        onOpenChannel = { channelId, openCatchup ->
                            navController.navigate(channelRoute(destination, channelId, openCatchup))
                        },
                        onOpenPlayer = { navController.navigate(PLAYER_ROUTE) },
                    )
                    MobileDestination.DOWNLOADS -> DownloadsScreen(
                        onPlayerOpened = { navController.navigate(PLAYER_ROUTE) },
                    )
                    MobileDestination.LIBRARY, MobileDestination.MOVIES, MobileDestination.SERIES ->
                        LibraryScreen(
                            scrollToTop = scrollToTop,
                            route = destination.route,
                            fixedTab = destination.fixedTab(),
                            onOpenItem = { tab, id ->
                                navController.navigate(detailRoute(destination.route, tab, id))
                            },
                            onPlay = { navController.navigate(PLAYER_ROUTE) },
                        )
                    MobileDestination.SETTINGS -> SettingsScreen(
                        onOpenRoute = { navController.navigate(it) },
                        onAddSource = { navController.navigate(SETUP_ROUTE) },
                    )
                }
                }
            }
        }
        // A film or a show sits under the tab it was opened from, so the bar keeps that tab selected
        // — the same reason the channel screen lives under Live. Three parents, because a tablet
        // opens the same item from Movies or Series and a phone opens it from Library.
        LIBRARY_PARENTS.forEach { parent ->
            composable(
                route = "${parent.route}/{$ARG_TAB}/{$ARG_ITEM_ID}",
                arguments = listOf(
                    navArgument(ARG_TAB) { type = NavType.StringType },
                    navArgument(ARG_ITEM_ID) { type = NavType.LongType },
                ),
            ) { entry ->
                val tab = entry.arguments?.getString(ARG_TAB) ?: return@composable
                val itemId = entry.arguments?.getLong(ARG_ITEM_ID) ?: return@composable
                CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                    DetailScreen(
                        tab = LibraryTab.valueOf(tab),
                        itemId = itemId,
                        onPlay = { navController.navigate(PLAYER_ROUTE) },
                    )
                }
            }
        }
        CHANNEL_PARENTS.forEach { parent ->
            composable(
                route = "${parent.route}/$CHANNEL_SEGMENT/{$ARG_CHANNEL_ID}/{$ARG_CATCHUP}",
                arguments = listOf(
                    navArgument(ARG_CHANNEL_ID) { type = NavType.LongType },
                    navArgument(ARG_CATCHUP) { type = NavType.BoolType },
                ),
            ) { entry ->
                ChannelDetailScreen(
                    channelId = entry.arguments?.getLong(ARG_CHANNEL_ID) ?: return@composable,
                    openCatchup = entry.arguments?.getBoolean(ARG_CATCHUP) == true,
                    onFullscreen = { navController.navigate(PLAYER_ROUTE) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(
            route = SEARCH_ROUTE_PATTERN,
            arguments = listOf(navArgument(ARG_QUERY) { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            SearchScreen(
                initialQuery = entry.arguments?.getString(ARG_QUERY).orEmpty(),
                onOpenChannel = { channelId ->
                    navController.navigate(channelRoute(MobileDestination.LIVE, channelId, false))
                },
                onOpenMovie = { id ->
                    navController.navigate(
                        detailRoute(MobileDestination.LIBRARY.route, LibraryTab.MOVIES, id),
                    )
                },
                onOpenSeries = { id ->
                    navController.navigate(
                        detailRoute(MobileDestination.LIBRARY.route, LibraryTab.SERIES, id),
                    )
                },
            )
        }
        // The seven group pages and every leaf under them. Each is a route rather than an expanding
        // block, so the system back gesture is what closes it.
        val openRoute: (String) -> Unit = { navController.navigate(it) }
        val addSource: () -> Unit = { navController.navigate(SETUP_ROUTE) }
        SettingsGroup.entries.forEach { group ->
            composable(group.route) { SettingsGroupPage(group, openRoute) }
        }
        SettingsLeaf.entries.forEach { leaf ->
            composable(leaf.route) { SettingsLeafPage(leaf, openRoute, addSource) }
        }
        // The More pages. Separate routes from the settings ones on purpose: what left Settings has
        // left, so nothing under `settings/` can reach them and the settings search cannot see them.
        MoreLeaf.entries.forEach { leaf ->
            composable(leaf.route) {
                CompositionLocalProvider(LocalNavAnimatedScope provides this) {
                    MoreLeafPage(
                        leaf = leaf,
                        // A channel or a title opened from Favourites lands on the screen it has
                        // under its own tab — the same convention the Guide uses for its channels.
                        onOpenChannel = { channelId, openCatchup ->
                            navController.navigate(
                                channelRoute(MobileDestination.LIVE, channelId, openCatchup),
                            )
                        },
                        onOpenItem = { tab, id ->
                            navController.navigate(
                                detailRoute(MobileDestination.LIBRARY.route, tab, id),
                            )
                        },
                        onOpenPlayer = { navController.navigate(PLAYER_ROUTE) },
                    )
                }
            }
        }
        composable(PLAYER_ROUTE) {
            // `onExit` is only ever the mini-player button, the swipe down and Sound only — the three
            // ways of saying "keep it playing". Back has its own path and stops the stream, so it
            // never reaches here and never raises the flag.
            val miniRequested = LocalMiniRequested.current
            PlayerScreen(
                onExit = {
                    miniRequested.value = true
                    navController.leavePlayerStillPlaying()
                },
            )
        }
        composable(SETUP_ROUTE) {
            SetupFlow(
                onDone = { navController.popBackStack() },
                onCancel = { navController.popBackStack() },
            )
        }
        if (BuildConfig.DEV_TOOLS) {
            composable(DevRoute.GALLERY.route) { ThemeGalleryScreen() }
        }
    }
        }
    }
}

/**
 * A channel, watched. It sits under the tab it was opened from so the shell keeps that tab selected
 * and its title while the channel is open — a detail screen is not a fifth tab.
 *
 * The `channel` segment is what keeps this apart from a film's route under the same parent: both are
 * "parent then two arguments", and the matcher goes by shape, not by argument type.
 */
private fun channelRoute(parent: MobileDestination, channelId: Long, openCatchup: Boolean) =
    "${parent.route}/$CHANNEL_SEGMENT/$channelId/$openCatchup"

/** A channel opened from outside the graph — the profile's "Start on a channel" setting. */
fun liveChannelRoute(channelId: Long) = channelRoute(MobileDestination.LIVE, channelId, false)

/** A channel screen draws the picture itself, under whichever tab it was opened from. */
fun isChannelRoute(route: String?): Boolean = route?.contains("/$CHANNEL_SEGMENT/") == true

/**
 * Leave the full screen player with the stream still running — the small-window button, Sound only,
 * and the swipe down.
 *
 * It has to leave **every** screen that draws the picture, not just the top one. A channel is watched
 * from its own screen, so popping the player alone landed the user back on that screen, and the shell
 * hides the mini player wherever the stream is already on display: the picture went away, no bar and
 * no floating window arrived, and the two buttons looked broken. A film's page is not one of these —
 * it describes the film, it does not play it — so only channel screens are walked past.
 */
private fun NavHostController.leavePlayerStillPlaying() {
    popBackStack()
    while (isChannelRoute(currentDestination?.route)) {
        if (!popBackStack()) return
    }
}

private const val ARG_CHANNEL_ID = "channelId"
private const val ARG_CATCHUP = "catchup"
private const val CHANNEL_SEGMENT = "channel"

/** The tabs a channel can be opened from. Guide sends its channels to Live's. */
private val CHANNEL_PARENTS = listOf(MobileDestination.LIVE, MobileDestination.HOME)

/** The tabs a film or a show can be opened from, and therefore returned to. */
private val LIBRARY_PARENTS = listOf(
    MobileDestination.LIBRARY,
    MobileDestination.MOVIES,
    MobileDestination.SERIES,
    MobileDestination.HOME,
)

/** Movies and Series are one screen locked to one half; Library is that screen with both tabs. */
private fun MobileDestination.fixedTab(): LibraryTab? = when (this) {
    MobileDestination.MOVIES -> LibraryTab.MOVIES
    MobileDestination.SERIES -> LibraryTab.SERIES
    else -> null
}

private fun detailRoute(parentRoute: String, tab: LibraryTab, itemId: Long) =
    "$parentRoute/${tab.name}/$itemId"

private const val ARG_TAB = "tab"
private const val ARG_ITEM_ID = "itemId"

/**
 * The full screen player. A route of its own, outside the tabs, because it is the one destination
 * that hides the shell — the bars would be covering the picture, and the back stack is what returns
 * the user to whatever they were looking at.
 */
const val PLAYER_ROUTE = "player"

/**
 * Adding a playlist, or restoring a backup. Like the player it hides the shell: the bars would offer
 * tabs that lead to empty screens, and on a first run there is nothing behind it to go back to.
 */
const val SETUP_ROUTE = "setup"

/**
 * One field over everything. A route rather than a tab, because search is reached from the top bar of
 * whichever screen the user is on, and back should return them to exactly that screen.
 */
private const val ARG_QUERY = "q"

const val SEARCH_ROUTE = "search"

/**
 * The same screen with a title already typed into it — Home's trending hero offers "All versions",
 * which is a search for that title. The argument is optional, so plain [SEARCH_ROUTE] still matches.
 */
const val SEARCH_ROUTE_PATTERN = "search?$ARG_QUERY={$ARG_QUERY}"

fun searchRoute(query: String): String = "search?$ARG_QUERY=${Uri.encode(query)}"

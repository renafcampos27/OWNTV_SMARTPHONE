package tv.own.owntv.mobile.ui.screens

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.core.sync.work.CatalogSyncScheduler
import tv.own.owntv.mobile.BuildConfig
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileGroup
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.nav.MobileDestination
import tv.own.owntv.mobile.ui.theme.MobileDimens

/**
 * The hub behind the More tab: everything that is neither content nor a preference.
 *
 * Nine rows in three groups — where you go, your own data, and the app itself. Backup, Local sync,
 * the error log and About are here rather than in Settings because none of them is a setting: they
 * were in Settings only because Settings was the only door.
 *
 * Downloads and Settings are also rail destinations on a tablet, so they are reachable two ways
 * there and one way on a phone — the same screen either way.
 *
 * There is deliberately no "Add playlist", "Restore backup" or "Sync now" row: all three already
 * live where they belong, and a second door is what this screen exists to remove.
 */
@Composable
fun MoreScreen(
    scrollToTop: SharedFlow<String>,
    onNavigate: (MobileDestination) -> Unit,
    onOpenLeaf: (MoreLeaf) -> Unit,
    onDevRoute: (DevRoute) -> Unit,
    modifier: Modifier = Modifier,
    counts: MoreCountsViewModel = koinViewModel(),
) {
    // The two counts are the whole point of those rows: they say how much there is before you go
    // and look. Both queries already exist on the DAOs — see [MoreCountsViewModel].
    val favorites by counts.favorites.collectAsStateWithLifecycle()
    val history by counts.history.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    listState.ObeyScrollToTop(route = MobileDestination.MORE.route, scrollToTop = scrollToTop)

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MobileDimens.PagePaddingH,
            end = MobileDimens.PagePaddingH,
            top = MobileDimens.ScreenPaddingV,
            bottom = MobileDimens.GapLarge,
        ),
    ) {
        // Three blocks, not one list with rules across it: where you go, whose account it is, and
        // what you do to the library. The rounded pane is the grouping, the way a settings page
        // groups its rows.
        item {
            MobileGroup {
                
                MoreRow(R.string.common_nav_settings, MobileIcons.Settings) {
                    onNavigate(MobileDestination.SETTINGS)
                }
                LeafRow(MoreLeaf.RECORDINGS, onOpenLeaf)
                LeafRow(MoreLeaf.PROFILES, onOpenLeaf)
            }
            MobileGroup {
                
                
                // Side by side, because they are the same act aimed at a file and at a device.
                LeafRow(MoreLeaf.BACKUP, onOpenLeaf)
                
            }
            MobileGroup {
                LeafRow(MoreLeaf.ERROR_LOG, onOpenLeaf)
                LeafRow(MoreLeaf.ABOUT, onOpenLeaf)
            }

            // Dev-only, and English-only by the same rule the harness itself lives under: R8 removes
            // both rows and both screens from a published build.
            if (BuildConfig.DEV_TOOLS) {
                MobileGroup {
                    MobileListRow(
                        title = "Theme gallery",
                        leading = { Icon(MobileIcons.Build, contentDescription = null) },
                        onClick = { onDevRoute(DevRoute.GALLERY) },
                    )
                    RebuildTrendingRow()
                }
            }
        }
    }
}

/**
 * The television's "Rebuild Now Trending", on the phone.
 *
 * Trending is read-only on mobile by Plan 4's own rule — the row shows what core last stored, and the
 * refresh is scheduled a few days apart. That timer is the problem when a trending bug is being
 * chased: reproducing it means waiting days, or driving the television. This forces the download for
 * every playlist on the active profile, right now, ignoring the timer.
 *
 * Deliberately unthrottled beyond its own two-second chip, for the reason the television's is:
 * `BuildConfig.DEV_TOOLS` is false in every published APK, so R8 deletes this function and the row
 * that calls it, and a maintainer chasing a bug needs to press it as often as the bug requires.
 *
 * English-only, like the two rows above it and by the same exception — it never reaches a user. The
 * chip is the one exception: core already has that word translated, so it costs nothing to use it.
 */
@Composable
private fun RebuildTrendingRow(
    settings: SettingsRepository = koinInject(),
    sourceDao: SourceDao = koinInject(),
    scheduler: CatalogSyncScheduler = koinInject(),
) {
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf(false) }
    MobileListRow(
        title = "Rebuild Now Trending",
        subtitle = if (running) stringResource(R.string.settings_rebuilding) else null,
        leading = { Icon(MobileIcons.Build, contentDescription = null) },
        onClick = {
            if (running) return@MobileListRow
            running = true
            scope.launch {
                val profileId = settings.activeProfileId.first()
                if (profileId >= 0) {
                    sourceDao.sourceIdsForProfile(profileId)
                        .forEach { scheduler.enqueueTrendingRefresh(it, force = true) }
                }
                // The work is a background job, so there is nothing to await — the chip is there to
                // say the press landed, not to report the result. Watch the row itself for that.
                delay(2_500)
                running = false
            }
        },
    )
}

/**
 * The Phase 1 component gallery, reachable only in a dev build.
 *
 * The Plan 3 import harness stood beside it until Plan 4 was finished. It existed to drive a sync
 * and a restore before there were real screens to do either, and there are now: the setup flow adds
 * a playlist, Settings → Playlists re-syncs one, and More → Backup restores. It was also the last
 * English-only text in the app, so deleting it empties `tools/i18n/hardcoded_baseline.txt` and makes
 * the translation gate absolute — a literal can no longer be excused by a file that already lists it.
 */
enum class DevRoute(val route: String) {
    GALLERY("dev_gallery"),
}

@Composable
private fun MoreRow(
    labelRes: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null,
    count: Int? = null,
    onClick: () -> Unit = {},
) {
    MobileListRow(
        title = stringResource(labelRes),
        subtitle = subtitle,
        leading = { Icon(imageVector = icon, contentDescription = null) },
        trailing = count?.let {
            {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
    )
}

/** A row that opens one of More's own pages, named and described by the registry itself. */
@Composable
private fun LeafRow(leaf: MoreLeaf, onOpen: (MoreLeaf) -> Unit, count: Int? = null) {
    MoreRow(
        labelRes = leaf.titleRes,
        icon = leaf.icon,
        subtitle = leaf.summaryRes?.let { stringResource(it) },
        count = count,
        onClick = { onOpen(leaf) },
    )
}

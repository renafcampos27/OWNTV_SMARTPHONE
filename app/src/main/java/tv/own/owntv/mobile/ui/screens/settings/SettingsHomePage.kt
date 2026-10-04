package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.model.HomeConfig
import tv.own.owntv.core.model.HomeLiveRowMode
import tv.own.owntv.core.model.HomeRow
import tv.own.owntv.core.model.HomeTrendingStyle
import tv.own.owntv.core.trending.TrendingAvailability
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileSwitch
import tv.own.owntv.mobile.ui.components.SettingRow

/**
 * Home, as the profile wants it: which rows appear, in what order, and what the Keep watching row is
 * allowed to draw from.
 *
 * Now Trending is first and apart from the rest, because it is the one row that can be empty for
 * reasons the user cannot see — metadata turned off, a channels-only playlist, a sync that has not
 * run. The sentence under it is core's own verdict, so the phone and the television never give two
 * different explanations for one empty row.
 */
@Composable
fun SettingsHomePage(
    modifier: Modifier = Modifier,
    vm: SettingsViewModel = koinViewModel(),
) {
    val profileId = vm.settings.activeProfileId.pref(-1L)
    val home = remember(profileId) {
        if (profileId < 0) flowOf(HomeConfig()) else vm.settings.homeConfig(profileId)
    }.pref(HomeConfig())
    val availability by vm.trendingAvailability.collectAsStateWithLifecycle()

    fun editHome(transform: (HomeConfig) -> HomeConfig) {
        if (profileId < 0) return
        vm.edit { updateHomeConfig(profileId, transform) }
    }

    val trendingOn = HomeRow.TRENDING !in home.hidden

    SettingsPage(modifier) {
        settingsSection(R.string.home_row_now_trending)
        settingsNote(R.string.home_row_trending_description)
        settingsGroup(key = "trending") {
            SettingRow(
                title = stringResource(R.string.home_row_now_trending),
                // The status, not the description: the description is the note above, and what a
                // user opening this page wants is why the row is not there today.
                subtitle = trendingStatusText(hidden = !trendingOn, availability = availability),
                checked = trendingOn,
                onCheckedChange = { show ->
                    editHome {
                        it.copy(hidden = if (show) it.hidden - HomeRow.TRENDING else it.hidden + HomeRow.TRENDING)
                    }
                },
            )
            // Only worth offering while the row is actually on — with Trending off there is nothing
            // for the choice to apply to, so it is not shown at all.
            if (trendingOn) {
                SettingRow(
                    title = stringResource(R.string.home_trending_style),
                    subtitle = stringResource(home.trendingStyle.labelRes()),
                    onClick = {
                        editHome {
                            it.copy(
                                trendingStyle = when (it.trendingStyle) {
                                    HomeTrendingStyle.HERO -> HomeTrendingStyle.POSTERS
                                    HomeTrendingStyle.POSTERS -> HomeTrendingStyle.HERO
                                },
                            )
                        }
                    },
                )
            }
        }

        settingsSection(R.string.settings_sections)
        settingsNote(R.string.settings_hidden_sections)
        settingsGroup(key = "rows") {
            home.settingsRows.forEachIndexed { index, row ->
                val order = home.settingsRows
                ReorderableSwitchRow(
                    title = stringResource(row.titleRes()),
                    subtitle = if (row in home.hidden) stringResource(R.string.settings_hidden)
                    else stringResource(row.descriptionRes()),
                    checked = row !in home.hidden,
                    onCheckedChange = { show ->
                        editHome { it.copy(hidden = if (show) it.hidden - row else it.hidden + row) }
                    },
                    canMoveUp = index > 0,
                    canMoveDown = index < order.lastIndex,
                    onMove = { delta ->
                        val moved = order.toMutableList().apply { add(index + delta, removeAt(index)) }
                        editHome { it.copy(order = listOf(HomeRow.TRENDING) + moved) }
                    },
                )
                // Only the two channel rows have a second shape to draw in, so only they carry the
                // choice — the same rule, and the same stored value, the television uses.
                row.liveMode(home)?.let { mode ->
                    SettingRow(
                        title = stringResource(R.string.settings_mode, stringResource(mode.labelRes())),
                        onClick = {
                            editHome {
                                when (row) {
                                    HomeRow.RECENT_CHANNELS -> it.copy(recentLiveMode = mode.toggled())
                                    else -> it.copy(favoriteLiveMode = mode.toggled())
                                }
                            }
                        },
                    )
                }
            }
        }

        settingsSection(R.string.settings_keep_watching) {
            SettingRow(
                title = stringResource(R.string.settings_hero_preview),
                subtitle = stringResource(R.string.settings_hero_preview_description),
                checked = vm.settings.heroPreviewEnabled.pref(vm.settings.heroPreviewDefault),
                onCheckedChange = { on -> vm.edit { setHeroPreviewEnabled(on) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_live_keep_watching),
                subtitle = stringResource(R.string.settings_live_keep_watching_description),
                checked = home.heroIncludeLive,
                onCheckedChange = { on -> editHome { it.copy(heroIncludeLive = on) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_movies_keep_watching),
                subtitle = stringResource(R.string.settings_movies_keep_watching_description),
                checked = home.heroIncludeMovies,
                onCheckedChange = { on -> editHome { it.copy(heroIncludeMovies = on) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_series_keep_watching),
                subtitle = stringResource(R.string.settings_series_keep_watching_description),
                checked = home.heroIncludeSeries,
                onCheckedChange = { on -> editHome { it.copy(heroIncludeSeries = on) } },
            )
        }
    }
}

/**
 * A row that can be both switched off and moved. The TV app picks a row up with OK and walks it with
 * the D-pad; a thumb gets two arrows instead, because there is nothing to hold on a touch screen.
 */
@Composable
private fun ReorderableSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
) {
    MobileListRow(
        title = title,
        subtitle = subtitle,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Row {
                MoveButton(up = true, enabled = canMoveUp) { onMove(-1) }
                MoveButton(up = false, enabled = canMoveDown) { onMove(1) }
                MobileSwitch(checked = checked)
            }
        },
    )
}

private fun HomeRow.titleRes() = when (this) {
    HomeRow.TRENDING -> R.string.home_row_now_trending
    HomeRow.HERO -> R.string.settings_keep_watching
    HomeRow.RECENT_CHANNELS -> R.string.home_row_recent_channels
    HomeRow.FAVORITE_CHANNELS -> R.string.home_row_favorite_channels
    HomeRow.CONTINUE_MOVIES -> R.string.home_row_continue_movies
    HomeRow.CONTINUE_SERIES -> R.string.home_row_continue_series
}

private fun HomeRow.descriptionRes() = when (this) {
    HomeRow.TRENDING -> R.string.home_row_trending_description
    HomeRow.HERO -> R.string.home_row_hero_description
    HomeRow.RECENT_CHANNELS -> R.string.home_row_recent_description
    HomeRow.FAVORITE_CHANNELS -> R.string.home_row_favorite_description
    HomeRow.CONTINUE_MOVIES -> R.string.home_row_continue_movies_description
    HomeRow.CONTINUE_SERIES -> R.string.home_row_continue_series_description
}

/** Which of the two channel rows this is, and the shape it is currently drawn in. */
private fun HomeRow.liveMode(config: HomeConfig): HomeLiveRowMode? = when (this) {
    HomeRow.RECENT_CHANNELS -> config.recentLiveMode
    HomeRow.FAVORITE_CHANNELS -> config.favoriteLiveMode
    else -> null
}

private fun HomeTrendingStyle.labelRes() = when (this) {
    HomeTrendingStyle.HERO -> R.string.home_trending_style_hero
    HomeTrendingStyle.POSTERS -> R.string.home_trending_style_posters
}

private fun HomeLiveRowMode.labelRes() = when (this) {
    HomeLiveRowMode.CARDS -> R.string.home_row_cards
    HomeLiveRowMode.ON_NOW -> R.string.home_row_on_now
}

/** The ten sentences the row can be explained by, in the television's own order of precedence. */
@Composable
private fun trendingStatusText(hidden: Boolean, availability: TrendingAvailability): String = when {
    hidden -> stringResource(R.string.settings_trending_status_off)
    availability == TrendingAvailability.Building ->
        stringResource(R.string.settings_trending_status_building)
    availability == TrendingAvailability.MetadataDisabled ->
        stringResource(R.string.settings_trending_status_metadata_disabled)
    availability == TrendingAvailability.NoVodScope ->
        stringResource(R.string.settings_trending_status_no_vod)
    availability == TrendingAvailability.Failed ->
        stringResource(R.string.settings_trending_status_failed)
    availability is TrendingAvailability.BelowThreshold && availability.matched == 0 ->
        stringResource(R.string.settings_trending_status_no_matches)
    availability is TrendingAvailability.BelowThreshold -> pluralStringResource(
        R.plurals.settings_trending_status_below_threshold,
        availability.matched,
        availability.matched,
    )
    availability is TrendingAvailability.Showing && availability.refreshFailed -> pluralStringResource(
        R.plurals.settings_trending_status_showing_refresh_failed,
        availability.count,
        availability.count,
    )
    availability is TrendingAvailability.Showing -> pluralStringResource(
        R.plurals.settings_trending_status_showing,
        availability.count,
        availability.count,
    )
    else -> stringResource(R.string.settings_trending_status_waiting)
}

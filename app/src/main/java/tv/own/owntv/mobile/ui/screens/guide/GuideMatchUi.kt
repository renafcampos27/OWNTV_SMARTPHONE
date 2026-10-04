package tv.own.owntv.mobile.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.SettingRow
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.screens.live.EpgMatchSheet
import tv.own.owntv.mobile.ui.screens.live.EpgOffsetDialog
import tv.own.owntv.mobile.ui.theme.MobileDimens
import kotlin.math.roundToInt

/** Which follow-up the guide's channel sheet handed off to. */
private enum class GuideChannelDialog { MATCH_EPG, EPG_OFFSET }

/**
 * What auto-matching left uncertain, one channel per row.
 *
 * The percentage is the matcher's own confidence, and it is worth showing: "BBC One HD → BBC One,
 * 88%" is obviously right, while "Sky Sports 1 → Sky Sports 10, 84%" is obviously not, and only the
 * number tells the two apart at a glance.
 */
@Composable
fun EpgReviewSheet(
    suggestions: List<EpgMatchSuggestion>,
    includeLogos: Boolean,
    onIncludeLogos: (Boolean) -> Unit,
    onAccept: (EpgMatchSuggestion) -> Unit,
    onSkip: (EpgMatchSuggestion) -> Unit,
    onAcceptAll: () -> Unit,
    onDone: () -> Unit,
) {
    MobileBottomSheet(
        onDismissRequest = onDone,
        title = stringResource(R.string.content_epg_review_matches),
    ) {
        Text(
            text = stringResource(R.string.content_epg_review_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        // Above the list rather than beside the buttons: it applies to every row, accepted one by one
        // or all at once.
        SettingRow(
            title = stringResource(R.string.content_epg_include_logos),
            checked = includeLogos,
            onCheckedChange = onIncludeLogos,
        )
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(suggestions, key = { it.channel.id }) { suggestion ->
                MobileListRow(
                    title = suggestion.channel.name,
                    subtitle = stringResource(
                        R.string.content_epg_channel_score,
                        suggestion.epgName ?: suggestion.epgChannelId,
                        (suggestion.score * PERCENT).roundToInt(),
                    ),
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall)) {
                            MobileButton(
                                text = stringResource(R.string.content_epg_skip),
                                onClick = { onSkip(suggestion) },
                                style = MobileButtonStyle.TEXT,
                            )
                            MobileButton(
                                text = stringResource(R.string.content_epg_accept),
                                onClick = { onAccept(suggestion) },
                                style = MobileButtonStyle.SECONDARY,
                            )
                        }
                    },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall, Alignment.End),
        ) {
            // "Skip all" and "Done" are the same act — nothing is written for a skipped row.
            MobileButton(
                text = stringResource(R.string.content_epg_skip_all),
                onClick = onDone,
                style = MobileButtonStyle.TEXT,
            )
            MobileButton(
                text = stringResource(R.string.content_epg_accept_all),
                onClick = onAcceptAll,
            )
        }
    }
}

/** The guide's own long-press menu: fix this row's guide, or shift its clock. */
@Composable
fun GuideChannelSheet(
    channel: ChannelEntity,
    vm: GuideViewModel,
    onDismiss: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<GuideChannelDialog?>(null) }

    LaunchedEffect(sheetOpen, dialog) { if (!sheetOpen && dialog == null) onDismiss() }

    if (sheetOpen) {
        MobileBottomSheet(
            onDismissRequest = { sheetOpen = false },
            title = channel.name,
        ) {
            MobileListRow(
                title = stringResource(R.string.content_epg_match_button),
                onClick = { vm.autoMatchOne(channel); sheetOpen = false },
            )
            MobileListRow(
                title = stringResource(R.string.content_epg_pick_manually),
                onClick = { dialog = GuideChannelDialog.MATCH_EPG; sheetOpen = false },
            )
            MobileListRow(
                title = stringResource(R.string.content_epg_time_offset),
                onClick = { dialog = GuideChannelDialog.EPG_OFFSET; sheetOpen = false },
            )
        }
    }

    when (dialog) {
        GuideChannelDialog.MATCH_EPG -> EpgMatchSheet(
            channelName = channel.name,
            currentMatch = vm.currentEpgMatch(channel),
            search = { vm.availableEpgChannels(channel.name, it) },
            onPick = { vm.setEpgMatch(channel, it) },
            onDismiss = { dialog = null },
        )
        GuideChannelDialog.EPG_OFFSET -> EpgOffsetDialog(
            channelName = channel.name,
            currentMinutes = vm.currentEpgShift(channel),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(channel, it) },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

/** "Guide loaded: 412 channels. The guide contains 91,204 programs. Catch-up on 30 channels." */
@Composable
fun epgStatsText(stats: GuideStats): String = stringResource(
    R.string.content_epg_stats_loaded,
    pluralStringResource(R.plurals.content_epg_stats_channels, stats.guideChannels, stats.guideChannels),
    pluralStringResource(R.plurals.content_epg_stats_programmes, stats.programmes, stats.programmes),
    if (stats.catchupChannels == 0) {
        stringResource(R.string.content_epg_stats_no_catchup)
    } else {
        pluralStringResource(
            R.plurals.content_epg_stats_catchup_available,
            stats.catchupChannels,
            stats.catchupChannels,
        )
    },
)

/** The one line an auto-match run leaves behind, whatever happened. */
@Composable
fun epgMatchSummaryText(summary: EpgMatchSummary): String = when (summary) {
    EpgMatchSummary.MatchedNoProgrammes -> stringResource(R.string.content_epg_matched_no_programmes)
    EpgMatchSummary.AddPlaylist -> stringResource(R.string.content_epg_add_playlist_first)
    EpgMatchSummary.NoData -> stringResource(R.string.content_epg_no_match_data)
    EpgMatchSummary.AllMatched -> stringResource(R.string.content_epg_all_matched)
    is EpgMatchSummary.NoMatch -> stringResource(R.string.content_epg_no_match, summary.channelName)
    is EpgMatchSummary.AutoMatched -> if (summary.review == 0) {
        pluralStringResource(
            R.plurals.content_epg_auto_matched_no_review,
            summary.applied,
            summary.applied,
        )
    } else {
        stringResource(
            R.string.content_epg_auto_matched,
            pluralStringResource(R.plurals.content_epg_auto_matched_applied, summary.applied, summary.applied),
            pluralStringResource(R.plurals.content_epg_auto_matched_review, summary.review, summary.review),
        )
    }
}

private const val PERCENT = 100

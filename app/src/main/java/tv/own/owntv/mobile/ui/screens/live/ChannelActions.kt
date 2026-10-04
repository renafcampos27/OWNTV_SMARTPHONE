package tv.own.owntv.mobile.ui.screens.live

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import tv.own.owntv.core.database.entity.ChannelEntity
import tv.own.owntv.core.epg.GuideCandidate
import tv.own.owntv.core.live.LiveKey
import tv.own.owntv.core.model.ContentMenu
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.ContentMenuSheet
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileButton
import tv.own.owntv.mobile.ui.components.MobileButtonStyle
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.components.MoveToCategorySheet
import tv.own.owntv.mobile.ui.components.NewCategoryDialog
import tv.own.owntv.mobile.ui.components.ReorderSheet
import tv.own.owntv.mobile.ui.components.SheetAction
import tv.own.owntv.mobile.ui.components.sheetListHeight
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/** Which follow-up the sheet handed off to. Only ever one at a time. */
private enum class ChannelDialog { RENAME, MATCH_EPG, EPG_OFFSET, MOVE, MOVE_TO_CATEGORY, NEW_CATEGORY }

/**
 * The long-press menu for a channel, and everything it opens.
 *
 * The ten actions, their keys and their gating are the TV app's, because the arrangement the user
 * saved in Settings is keyed by exactly those strings. What differs is where they land: a sheet
 * instead of a focused column, and dialogs a thumb can reach.
 *
 * The sheet closes the moment an action is tapped, so [onDismiss] is deliberately *not* called from
 * it — the caller is released only once the sheet is gone **and** nothing it opened is still up.
 */
@Composable
fun ChannelMenu(
    channel: ChannelEntity,
    selected: LiveKey,
    isFavorite: Boolean,
    originName: String,
    vm: LiveViewModel,
    onOpenCatchup: () -> Unit,
    onDismiss: () -> Unit,
    onOpenVersions: (() -> Unit)? = null,
) {
    var sheetOpen by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<ChannelDialog?>(null) }

    LaunchedEffect(sheetOpen, dialog) { if (!sheetOpen && dialog == null) onDismiss() }

    // Multiview: offered only once it is switched on, and the confirmation says how many are kept.
    val settings = org.koin.compose.koinInject<tv.own.owntv.core.settings.SettingsRepository>()
    val tuner = org.koin.compose.koinInject<LiveTuner>()
    // Null until DataStore has answered — not `false`.
    //
    // A sheet is handed to the host as a lambda and drawn there, and the host keeps drawing the
    // lambda it was first given. Built while this was still the placeholder `false`, the menu was
    // built *without* the Multiview row and went on showing that version: the row never appeared,
    // however many times it was opened. Waiting for the real answer costs a frame and is correct.
    val multiviewSetting by settings.multiviewEnabled.collectAsStateWithLifecycle(null as Boolean?)
    val multiviewEnabled = multiviewSetting == true
    val multiviewTiles by settings.multiviewTiles.collectAsStateWithLifecycle(
        tv.own.owntv.core.live.DEFAULT_MULTIVIEW_TILES,
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    // Configuration-aware, unlike context.getString: the toast below is formatted at click time with
    // a count that is not known at composition, so it cannot be a stringResource.
    val res = androidx.compose.ui.platform.LocalResources.current

    if (sheetOpen && multiviewSetting != null) {
        val canMove = vm.contextKeyOf(selected) != null
        val actions = buildList {
            onOpenVersions?.let { open ->
                add(SheetAction(key = "channel_versions", label = stringResource(R.string.channel_versions_title),
                    icon = MobileIcons.VideoLibrary, group = 0, onClick = open))
            }
            add(
                SheetAction(
                    key = "rename",
                    label = stringResource(R.string.content_rename),
                    icon = MobileIcons.Edit,
                    group = 0,
                    onClick = { dialog = ChannelDialog.RENAME },
                ),
            )
            add(
                SheetAction(
                    key = "match_epg",
                    label = stringResource(R.string.content_match_epg),
                    icon = MobileIcons.Tv,
                    group = 1,
                    onClick = { dialog = ChannelDialog.MATCH_EPG },
                ),
            )
            add(
                SheetAction(
                    key = "epg_offset",
                    label = stringResource(R.string.content_epg_time_offset),
                    icon = MobileIcons.History,
                    group = 1,
                    onClick = { dialog = ChannelDialog.EPG_OFFSET },
                ),
            )
            if (channel.catchup) {
                add(
                    SheetAction(
                        key = "catchup",
                        label = stringResource(R.string.content_catchup),
                        icon = MobileIcons.History,
                        group = 1,
                        onClick = onOpenCatchup,
                    ),
                )
            }
            // Record this channel from now. The guide's Record needs a programme, so a channel the
            // provider publishes no guide for can only be recorded from here.
            add(
                SheetAction(
                    key = "record",
                    label = stringResource(R.string.recording_record),
                    icon = MobileIcons.LiveTv,
                    group = 1,
                    onClick = { tuner.recordNow(channel) },
                ),
            )
            add(
                SheetAction(
                    key = "play_external",
                    label = stringResource(R.string.content_play_external_short),
                    icon = MobileIcons.OpenInNew,
                    group = 1,
                    onClick = { vm.playExternal(channel) },
                ),
            )
            if (multiviewEnabled) {
                add(
                    SheetAction(
                        key = "add_to_multiview",
                        label = stringResource(R.string.multiview_add_to),
                        icon = MobileIcons.GridView,
                        group = 1,
                        onClick = {
                            tuner.addToMultiview(channel, multiviewTiles)
                            android.widget.Toast.makeText(
                                context,
                                res.getString(
                                    R.string.multiview_added,
                                    tuner.multiviewSelection.value.size,
                                    multiviewTiles,
                                ),
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        },
                    ),
                )
            }
            if (canMove) {
                add(
                    SheetAction(
                        key = "move",
                        label = stringResource(R.string.content_move),
                        icon = MobileIcons.SwapVert,
                        group = 2,
                        onClick = { dialog = ChannelDialog.MOVE },
                    ),
                )
                add(
                    SheetAction(
                        key = "move_to_category",
                        label = stringResource(R.string.content_move_to_category),
                        icon = MobileIcons.PlaylistAdd,
                        group = 2,
                        onClick = { dialog = ChannelDialog.MOVE_TO_CATEGORY },
                    ),
                )
            }
            if (selected is LiveKey.Custom) {
                add(
                    SheetAction(
                        key = "remove_from_category",
                        label = stringResource(R.string.content_remove_from_category),
                        icon = MobileIcons.Close,
                        group = 2,
                        onClick = { vm.removeFromCustomCategory(channel, selected) },
                    ),
                )
            }
            add(
                SheetAction(
                    key = "hide",
                    label = stringResource(R.string.content_hide_channel),
                    icon = MobileIcons.VisibilityOff,
                    destructive = true,
                    group = 3,
                    onClick = { vm.hideChannel(channel) },
                ),
            )
            if (selected == LiveKey.History) {
                add(
                    SheetAction(
                        key = "remove_history",
                        label = stringResource(R.string.content_remove_history),
                        destructive = true,
                        group = 3,
                        onClick = { vm.removeFromHistory(channel.id) },
                    ),
                )
            }
        }
        ContentMenuSheet(
            menu = ContentMenu.LIVE,
            title = channel.name,
            actions = actions,
            onDismiss = { sheetOpen = false },
        )
    }

    when (dialog) {
        ChannelDialog.RENAME -> RenameChannelDialog(
            channel = channel,
            onSet = { vm.renameChannel(channel, it) },
            onDismiss = { dialog = null },
        )
        ChannelDialog.MATCH_EPG -> EpgMatchSheet(
            channelName = channel.name,
            currentMatch = vm.currentEpgMatch(channel),
            search = { vm.availableEpgChannels(channel.name, it) },
            onPick = { vm.setEpgMatch(channel, it) },
            onDismiss = { dialog = null },
        )
        ChannelDialog.EPG_OFFSET -> EpgOffsetDialog(
            channelName = channel.name,
            currentMinutes = vm.currentEpgShift(channel),
            globalMinutes = vm.globalEpgShift(),
            onSet = { vm.setEpgShift(channel, it) },
            onDismiss = { dialog = null },
        )
        ChannelDialog.MOVE -> ReorderSheet(
            title = stringResource(R.string.content_reorder_channel),
            openAt = channel.id,
            load = { vm.moveList(selected) },
            onSave = { ids -> vm.contextKeyOf(selected)?.let { vm.commitMove(it, ids) } },
            onDismiss = { dialog = null },
        )
        ChannelDialog.MOVE_TO_CATEGORY -> MoveToCategorySheet(
            originName = originName,
            targets = vm.customCategories.collectAsStateWithLifecycle().value,
            onNewCategory = { dialog = ChannelDialog.NEW_CATEGORY },
            onMove = { targetId, keep ->
                vm.contextKeyOf(selected)?.let { vm.moveToCategory(channel, it, targetId, keep) }
            },
            onDismiss = { dialog = null },
        )
        ChannelDialog.NEW_CATEGORY -> NewCategoryDialog(
            onCreate = { vm.createCustomCategory(it) },
            // Back to the picker, where the category just created is waiting.
            onDismiss = { dialog = ChannelDialog.MOVE_TO_CATEGORY },
        )
        null -> Unit
    }
}

/** Rename for this profile only. An empty name is how the provider's own name comes back. */
@Composable
private fun RenameChannelDialog(
    channel: ChannelEntity,
    onSet: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(channel.name) }
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_rename_channel)) },
        text = {
            MobileTextField(
                value = text,
                onValueChange = { text = it },
                label = stringResource(R.string.content_rename_channel),
                imeAction = ImeAction.Done,
                onImeDone = { onSet(text.trim().takeIf { it.isNotEmpty() }); onDismiss() },
            )
        },
        confirmButton = {
            TextButton(onClick = { onSet(text.trim().takeIf { it.isNotEmpty() }); onDismiss() }) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onSet(null); onDismiss() }) {
                    Text(stringResource(R.string.common_reset))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/**
 * Point a channel at a guide channel by hand.
 *
 * Providers name the same channel three different ways across a playlist and its XMLTV, and the
 * automatic match gives up on some of them. Searching here is a live query, debounced so a typed
 * word costs one lookup rather than one per letter.
 *
 * The Guide opens this same sheet, which is why it takes plain values rather than a view model.
 */
// `debounce` is still a preview API; one lookup per word rather than one per letter is worth it.
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
internal fun EpgMatchSheet(
    channelName: String,
    currentMatch: String?,
    search: suspend (String) -> List<GuideCandidate>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GuideCandidate>?>(null) }
    val epgKeyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(channelName) {
        snapshotFlow { query }
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            .collect { results = search(it) }
    }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.content_match_epg),
    ) {
        Text(
            text = if (currentMatch != null) {
                stringResource(R.string.content_epg_match_prompt_current, channelName, currentMatch)
            } else {
                stringResource(R.string.content_epg_match_prompt, channelName)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        MobileTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.content_search_guide_channels),
            imeAction = ImeAction.Search,
            onSearch = { epgKeyboard?.hide() },
            modifier = Modifier.padding(
                horizontal = MobileDimens.ScreenPaddingH,
                vertical = MobileDimens.GapSmall,
            ),
        )
        val list = results
        when {
            list == null -> Box(
                Modifier.fillMaxWidth().padding(MobileDimens.GapLarge),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            list.isEmpty() -> Text(
                text = if (query.isBlank()) {
                    stringResource(R.string.content_no_epg_data)
                } else {
                    stringResource(R.string.content_no_guide_channels, query)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(MobileDimens.ScreenPaddingH),
            )
            else -> LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
                items(list, key = { it.epgChannelId }) { epg ->
                    MobileListRow(
                        title = epg.displayName ?: epg.epgChannelId,
                        subtitle = epg.epgChannelId,
                        onClick = { onPick(epg.epgChannelId); onDismiss() },
                    )
                }
            }
        }
        if (currentMatch != null) {
            MobileButton(
                text = stringResource(R.string.content_clear_match),
                onClick = { onPick(null); onDismiss() },
                style = MobileButtonStyle.TEXT,
                modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            )
        }
    }
}

/**
 * Shift one channel's guide.
 *
 * Providers often hang both the East and the West feed of a network off a single guide, so one of
 * them runs hours out. This moves that channel only; the global offset in Settings stays put.
 */
@Composable
internal fun EpgOffsetDialog(
    channelName: String,
    currentMinutes: Int?,
    globalMinutes: Int,
    onSet: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(currentMinutes ?: globalMinutes) }
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.content_epg_time_offset)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.content_epg_offset_channel_description, channelName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.padding(vertical = MobileDimens.GapMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall),
                ) {
                    MobileButton(
                        text = stringResource(R.string.content_epg_shift_minutes, MINUS_SIGN, "30"),
                        onClick = { minutes = (minutes - STEP_MINUTES).coerceAtLeast(MIN_MINUTES) },
                        style = MobileButtonStyle.SECONDARY,
                    )
                    Text(
                        text = epgShiftLabel(minutes),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(SHIFT_LABEL_WIDTH),
                    )
                    MobileButton(
                        text = stringResource(R.string.content_epg_shift_minutes, PLUS_SIGN, "30"),
                        onClick = { minutes = (minutes + STEP_MINUTES).coerceAtMost(MAX_MINUTES) },
                        style = MobileButtonStyle.SECONDARY,
                    )
                }
                Text(
                    text = stringResource(
                        if (currentMinutes == null) {
                            R.string.content_epg_offset_following_global
                        } else {
                            R.string.content_epg_offset_channel_only
                        },
                        epgShiftLabel(globalMinutes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (currentMinutes != null) {
                    TextButton(onClick = { onSet(null); onDismiss() }) {
                        Text(stringResource(R.string.content_epg_offset_use_global))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(minutes); onDismiss() }) {
                Text(stringResource(R.string.common_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** "+2h", "−30m", or "Off". The digits go through the locale's own number format. */
@Composable
internal fun epgShiftLabel(minutes: Int): String {
    if (minutes == 0) return stringResource(R.string.common_off)
    val locale = LocalConfiguration.current.locales[0] ?: java.util.Locale.US
    val number = java.text.NumberFormat.getIntegerInstance(locale)
    val sign = if (minutes < 0) MINUS_SIGN else PLUS_SIGN
    val absolute = kotlin.math.abs(minutes)
    val hours = absolute / 60
    val remainder = absolute % 60
    return when {
        hours == 0 -> stringResource(R.string.content_epg_shift_minutes, sign, number.format(remainder))
        remainder == 0 -> stringResource(R.string.content_epg_shift_hours, sign, number.format(hours))
        else -> stringResource(
            R.string.content_epg_shift_hours_minutes,
            sign,
            number.format(hours),
            number.format(remainder),
        )
    }
}

private const val MINUS_SIGN = "−"
private const val PLUS_SIGN = "+"
private const val STEP_MINUTES = 30
private const val MIN_MINUTES = -12 * 60
private const val MAX_MINUTES = 14 * 60
private const val SEARCH_DEBOUNCE_MS = 250L
private val SHIFT_LABEL_WIDTH = 96.dp

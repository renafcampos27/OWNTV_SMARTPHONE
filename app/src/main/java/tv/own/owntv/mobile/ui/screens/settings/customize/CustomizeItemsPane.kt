package tv.own.owntv.mobile.ui.screens.settings.customize

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import tv.own.owntv.core.customize.SpanSelector
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.screens.settings.SettingsPage
import tv.own.owntv.mobile.ui.screens.settings.settingsNote

/**
 * The inside of one folder: hide, rename, reorder and regroup the channels or films it holds.
 *
 * The system back gesture returns to the folder list, so the header's arrow is a second way home
 * rather than the only one.
 */
@Composable
fun CustomizeItemsPane(
    vm: CustomizeItemsViewModel,
    categoryName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val catInfo by vm.catInfo.collectAsStateWithLifecycle()
    val items = vm.items.collectAsLazyPagingItems()
    val visibilityFilter by vm.visibilityFilter.collectAsStateWithLifecycle()
    val anchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val endKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val selectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()
    val moveTargets by vm.moveTargets.collectAsStateWithLifecycle()

    val mediaType = catInfo?.mediaType ?: MediaType.LIVE
    var menuFor by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var renaming by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var movingTo by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var creatingCategory by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var rangeEnd by remember { mutableStateOf<CustomizeItemRow?>(null) }
    var showFilter by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.setVisibilityFilter(CustomizeVisibilityFilter.ALL) }
    SettingsPage(modifier) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MobileIcons.ArrowBack,
                        contentDescription = stringResource(R.string.settings_customize_back),
                    )
                }
                Text(categoryName, style = MaterialTheme.typography.titleMedium)
            }
        }
        settingsNote(mediaType.itemsDescriptionRes())

        if (anchorKey != null) {
            item(key = "span") {
                SpanBar(
                    mode = rangeMode,
                    hasEnd = endKey != null,
                    selectedCount = selectedKeys.size,
                    onMove = vm::moveRangeBlock,
                    onCancel = { vm.cancelRange() },
                )
            }
        }
        items(
            count = items.itemCount,
            key = items.itemKey { it.key },
        ) { index ->
            val row = items[index] ?: return@items
            MobileListRow(
                title = row.displayName,
                subtitle = row.provenance(),
                onClick = {
                    when {
                        anchorKey == null -> menuFor = row
                        row.key == anchorKey -> vm.cancelRange()
                        rangeMode == SpanSelector.Mode.HIDE -> rangeEnd = row
                        rangeMode == SpanSelector.Mode.RENAME -> {
                            if (vm.finishRenameRange(row) == null) renaming = row
                        }
                        else -> vm.setRangeEnd(row)
                    }
                },
                onLongClick = { menuFor = row },
                leading = spanTick(row.key in selectedKeys),
                selected = row.key in selectedKeys,
            )
        }
    }

    menuFor?.let { row ->
        RowActionsSheet(
            title = row.displayName,
            onDismiss = { menuFor = null },
            hiddenNow = row.hidden,
            onToggleHidden = { menuFor = null; vm.setItemHidden(row, !row.hidden) },
            onMove = { kind -> menuFor = null; vm.moveSingle(row, kind) },
            onSpanHide = { menuFor = null; vm.beginRange(row) },
            onSpanMove = { menuFor = null; vm.beginMoveRange(row) },
            onSpanRename = { menuFor = null; vm.beginRenameRange(row) },
            // A channel's name is its own; films and shows are renamed in batches instead.
            onRename = if (mediaType == MediaType.LIVE) {
                { menuFor = null; renaming = row }
            } else {
                null
            },
            onMoveTo = { menuFor = null; movingTo = row },
            onRemoveFromCategory = if (catInfo?.isCustom == true) ({ menuFor = null; vm.removeFromCategory(row) }) else null,
        )
    }

    renaming?.let { row ->
        TextPromptDialog(
            title = stringResource(R.string.settings_customize_rename),
            initial = row.displayName,
            hint = stringResource(R.string.settings_customize_rename_item_hint, row.originalName),
            onConfirm = { vm.renameItem(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
        )
    }

    movingTo?.let { row ->
        MoveToCategoryDialog(
            moveTargets = moveTargets,
            originName = categoryName,
            onNewCategory = { creatingCategory = row; movingTo = null },
            onMove = { targetId, keepInOrigin ->
                vm.moveTo(row, targetId, keepInOrigin)
                movingTo = null
            },
            onDismiss = { movingTo = null },
        )
    }

    creatingCategory?.let { row ->
        TextPromptDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            initial = "",
            description = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = null; movingTo = row },
            onDismiss = { creatingCategory = null },
        )
    }

    rangeEnd?.let { row ->
        SpanHideDialog(
            title = stringResource(R.string.settings_customize_hide_show_items),
            count = vm.keysInRange(row)?.size ?: 0,
            isCategories = false,
            onHide = { vm.applyRange(row, hidden = true); rangeEnd = null },
            onShow = { vm.applyRange(row, hidden = false); rangeEnd = null },
            onDismiss = { vm.cancelRange(); rangeEnd = null },
        )
    }

    if (showFilter) {
        FilterSheet(
            current = visibilityFilter,
            onSelect = { vm.setVisibilityFilter(it) },
            onDismiss = { showFilter = false },
        )
    }

    BulkRenameFlow(vm.bulk)
}

/** The one-line note under an item's name: hidden, and what the playlist originally called it. */
@Composable
private fun CustomizeItemRow.provenance(): String? {
    val parts = listOfNotNull(
        if (hidden) stringResource(R.string.settings_customize_hidden) else null,
        if (renamed) stringResource(R.string.settings_customize_item_was, originalName) else null,
    )
    return parts.takeIf { it.isNotEmpty() }
        ?.joinToString(stringResource(R.string.settings_customize_metadata_separator))
}

private fun MediaType.itemsDescriptionRes() = when (this) {
    MediaType.MOVIE -> R.string.settings_customize_movies_description
    MediaType.SERIES -> R.string.settings_customize_series_description
    else -> R.string.settings_customize_channels_description
}

package tv.own.owntv.mobile.ui.screens.settings.customize

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.customize.MoveKind
import tv.own.owntv.core.customize.SpanSelector
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.FilterChipRow
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.screens.settings.SettingsPage
import tv.own.owntv.mobile.ui.screens.settings.settingsNote
import tv.own.owntv.mobile.ui.screens.settings.settingsSection

/** The three sections that have folders to arrange. */
internal val SECTIONS = listOf(MediaType.LIVE)

/**
 * Hide, rename, reorder and group a section's folders — and bring back anything hidden.
 *
 * Everything a folder can do sits behind a long press, the way it does everywhere else in this app,
 * so a row stays a single tap that opens the folder's contents.
 */
@Composable
fun CustomizeCategoriesPane(
    vm: CustomizeViewModel,
    onOpenItems: (CustomizeCatRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val section by vm.section.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val hiddenItems by vm.hiddenChannels.collectAsStateWithLifecycle()
    val hideNewCategories by vm.hideNewCategories.collectAsStateWithLifecycle()
    val currentSort by vm.currentSort.collectAsStateWithLifecycle()
    val visibilityFilter by vm.visibilityFilter.collectAsStateWithLifecycle()
    val pinLock by vm.pinLock.collectAsStateWithLifecycle()
    val anchorKey by vm.rangeAnchorKey.collectAsStateWithLifecycle()
    val rangeMode by vm.rangeMode.collectAsStateWithLifecycle()
    val endKey by vm.rangeEndKey.collectAsStateWithLifecycle()
    val selectedKeys by vm.rangeSelectedKeys.collectAsStateWithLifecycle()

    var menuFor by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var renaming by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var deleting by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var creatingCategory by remember { mutableStateOf(false) }
    var rangeEnd by remember { mutableStateOf<CustomizeCatRow?>(null) }
    var sheet by remember { mutableStateOf<CustomizeSheet?>(null) }
    var editingPin by remember { mutableStateOf<PinEdit?>(null) }

    LaunchedEffect(vm) { vm.setVisibilityFilter(CustomizeVisibilityFilter.ALL); vm.setSort(SettingsRepository.SortMode.ALPHA) }
    SettingsPage(modifier) {

        item(key = "actions") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {


                AssistChip(
                    onClick = { sheet = CustomizeSheet.NEW_CATEGORY_BEHAVIOR },
                    label = {
                        Text(
                            stringResource(
                                R.string.settings_customize_new_categories_button,
                                stringResource(
                                    if (hideNewCategories) {
                                        R.string.settings_customize_behavior_hide
                                    } else {
                                        R.string.settings_customize_behavior_show
                                    },
                                ),
                            ),
                        )
                    },
                )
                AssistChip(
                    onClick = { creatingCategory = true },
                    label = { Text(stringResource(R.string.settings_customize_new_category)) },
                )
                if (pinLock.pin == null) {
                    AssistChip(
                        onClick = { editingPin = PinEdit.SET },
                        label = { Text(stringResource(R.string.settings_customize_set_pin)) },
                    )
                } else {
                    AssistChip(
                        onClick = { editingPin = PinEdit.CHANGE },
                        label = { Text(stringResource(R.string.settings_customize_change_pin)) },
                    )
                    AssistChip(
                        onClick = { editingPin = PinEdit.REMOVE },
                        label = { Text(stringResource(R.string.settings_customize_remove_lock)) },
                    )
                }
            }
        }
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

        if (hiddenItems.isNotEmpty()) {
            settingsSection(section.hiddenTitleRes())
            settingsNote(R.string.settings_customize_unhide_description)
            items(
                hiddenItems.entries.sortedBy { it.value.lowercase() },
                key = { "hidden-${it.key}" },
            ) { entry ->
                MobileListRow(
                    title = entry.value.ifBlank { entry.key },
                    trailing = {
                        TextButton(onClick = { vm.unhideChannel(entry.key) }) {
                            Text(stringResource(R.string.settings_customize_unhide))
                        }
                    },
                )
            }
        }

        settingsSection(R.string.settings_customize_categories)
        if (rows.isEmpty()) {
            settingsNote(R.string.settings_customize_empty)
        }
        items(rows, key = { it.key }) { row ->
            MobileListRow(
                title = row.displayName,
                subtitle = row.provenance(),
                onClick = {
                    when {
                        anchorKey == null -> onOpenItems(row)
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
        CategoryActionsSheet(
            row = row,
            onDismiss = { menuFor = null },
            onRename = { menuFor = null; renaming = row },
            onToggleHidden = { menuFor = null; vm.setCategoryHidden(row, !row.hidden) },
            onMove = { kind -> menuFor = null; vm.moveSingle(row, kind) },
            onDelete = { menuFor = null; deleting = row },
            onSpanHide = { menuFor = null; vm.beginRange(row) },
            onSpanMove = { menuFor = null; vm.beginMoveRange(row) },
            onSpanRename = { menuFor = null; vm.beginRenameRange(row) },
        )
    }

    renaming?.let { row ->
        TextPromptDialog(
            title = stringResource(
                if (row.categoryId == null) {
                    R.string.settings_customize_rename_or_delete_category
                } else {
                    R.string.settings_customize_rename_category
                },
            ),
            initial = row.displayName,
            hint = stringResource(R.string.settings_customize_rename_hint, row.originalName),
            onConfirm = { vm.renameCategory(row, it.takeIf { t -> t.isNotBlank() }); renaming = null },
            onDismiss = { renaming = null },
            extraButton = if (row.categoryId != null) {
                null
            } else {
                {
                    TextButton(onClick = { renaming = null; deleting = row }) {
                        Text(stringResource(R.string.common_delete))
                    }
                }
            },
        )
    }

    deleting?.let { row ->
        ConfirmDialog(
            title = stringResource(R.string.settings_customize_delete_category, row.displayName),
            message = stringResource(R.string.settings_customize_delete_category_description),
            confirmLabel = stringResource(R.string.common_delete),
            onConfirm = { vm.deleteCustomCategory(row); deleting = null },
            onDismiss = { deleting = null },
        )
    }

    if (creatingCategory) {
        TextPromptDialog(
            title = stringResource(R.string.settings_customize_new_category_title),
            initial = "",
            description = stringResource(R.string.settings_customize_new_category_description),
            confirmLabel = stringResource(R.string.common_create),
            allowBlank = false,
            onConfirm = { vm.createCustomCategory(it); creatingCategory = false },
            onDismiss = { creatingCategory = false },
        )
    }

    rangeEnd?.let { row ->
        SpanHideDialog(
            title = stringResource(R.string.settings_customize_hide_show_title),
            count = vm.keysInRange(row)?.size ?: 0,
            isCategories = true,
            onHide = { vm.applyRange(row, hidden = true); rangeEnd = null },
            onShow = { vm.applyRange(row, hidden = false); rangeEnd = null },
            onDismiss = { vm.cancelRange(); rangeEnd = null },
        )
    }

    when (sheet) {
        CustomizeSheet.SORT -> SortSheet(
            current = currentSort,
            onSelect = { vm.setSort(it) },
            onDismiss = { sheet = null },
        )
        CustomizeSheet.FILTER -> FilterSheet(
            current = visibilityFilter,
            onSelect = { vm.setVisibilityFilter(it) },
            onDismiss = { sheet = null },
        )
        CustomizeSheet.NEW_CATEGORY_BEHAVIOR -> NewCategoryBehaviorSheet(
            hideNew = hideNewCategories,
            onSelect = { vm.setHideNewCategories(it) },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    editingPin?.let { mode ->
        PinEditFlow(
            mode = mode,
            onSetPin = vm::setPin,
            onDone = { editingPin = null },
        )
    }

    BulkRenameFlow(vm.bulk)
}

/** Which of the header chips' pickers is open. */
private enum class CustomizeSheet { SORT, FILTER, NEW_CATEGORY_BEHAVIOR }

/** The one-line note under a folder's name: hidden, renamed from, and which playlist it came from. */
@Composable
private fun CustomizeCatRow.provenance(): String? {
    val parts = listOfNotNull(
        if (hidden) stringResource(R.string.settings_customize_hidden) else null,
        if (renamed) stringResource(R.string.settings_customize_was, originalName) else null,
        providerName
            ?: if (categoryId == null) {
                stringResource(R.string.settings_customize_custom_category)
            } else {
                null
            },
    )
    return parts.takeIf { it.isNotEmpty() }
        ?.joinToString(stringResource(R.string.settings_customize_metadata_separator))
}

internal fun MediaType.labelRes() = when (this) {
    MediaType.MOVIE -> R.string.common_nav_movies
    MediaType.SERIES -> R.string.common_nav_series
    else -> R.string.common_nav_live_tv
}

internal fun MediaType.hiddenTitleRes() = when (this) {
    MediaType.MOVIE -> R.string.settings_customize_hidden_movies
    MediaType.SERIES -> R.string.settings_customize_hidden_series
    else -> R.string.settings_customize_hidden_channels
}

internal fun CustomizeVisibilityFilter.labelRes() = when (this) {
    CustomizeVisibilityFilter.ALL -> R.string.settings_customize_filter_all
    CustomizeVisibilityFilter.VISIBLE -> R.string.settings_customize_filter_visible
    CustomizeVisibilityFilter.HIDDEN -> R.string.settings_customize_filter_hidden
}

/** Everything a folder can do, on a long press. */
@Composable
private fun CategoryActionsSheet(
    row: CustomizeCatRow,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onToggleHidden: () -> Unit,
    onMove: (MoveKind) -> Unit,
    onDelete: () -> Unit,
    onSpanHide: () -> Unit,
    onSpanMove: () -> Unit,
    onSpanRename: () -> Unit,
) {
    RowActionsSheet(
        title = row.displayName,
        onDismiss = onDismiss,
        hiddenNow = row.hidden,
        onRename = onRename,
        onToggleHidden = onToggleHidden,
        onMove = onMove,
        onSpanHide = onSpanHide,
        onSpanMove = onSpanMove,
        onSpanRename = onSpanRename,
        onDelete = if (row.categoryId == null) onDelete else null,
    )
}

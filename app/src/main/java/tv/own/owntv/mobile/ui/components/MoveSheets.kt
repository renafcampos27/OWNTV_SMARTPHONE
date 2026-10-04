package tv.own.owntv.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/** One row of a list being reordered: what to write back, and what to show. */
data class ReorderItem(val id: Long, val label: String)

/**
 * Reorder the list an item sits in.
 *
 * The TV app does this by picking the item up with OK and walking it with the D-pad. There is no
 * D-pad here, so each row carries its own two arrows — and the order is written only on Save, so
 * shuffling five titles is one edit rather than five.
 *
 * Channels, films and shows all reorder the same way, so they all use this: the caller only says how
 * to fetch the list and what to do with the ids that come back.
 */
@Composable
fun ReorderSheet(
    title: String,
    openAt: Long,
    load: suspend () -> List<ReorderItem>,
    onSave: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember { mutableStateListOf<ReorderItem>() }
    var loaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        items.addAll(load())
        loaded = true
        // Open on the item the user long-pressed; it is rarely near the top of a long folder.
        items.indexOfFirst { it.id == openAt }.takeIf { it >= 0 }?.let { listState.scrollToItem(it) }
    }

    MobileBottomSheet(onDismissRequest = onDismiss, title = title) {
        LazyColumn(state = listState, modifier = Modifier.heightIn(max = sheetListHeight())) {
            items(items, key = { it.id }) { item ->
                val index = items.indexOf(item)
                MobileListRow(
                    title = item.label,
                    trailing = {
                        Row {
                            IconButton(
                                onClick = { if (index > 0) items.add(index - 1, items.removeAt(index)) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    imageVector = MobileIcons.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.settings_row_menu_move_up),
                                )
                            }
                            IconButton(
                                onClick = { if (index < items.lastIndex) items.add(index + 1, items.removeAt(index)) },
                                enabled = index < items.lastIndex,
                            ) {
                                Icon(
                                    imageVector = MobileIcons.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.settings_row_menu_move_down),
                                )
                            }
                        }
                    },
                )
            }
        }
        SheetButtons(
            confirm = stringResource(R.string.common_save),
            confirmEnabled = loaded && items.isNotEmpty(),
            onConfirm = { onSave(items.map { it.id }) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Put the item into one of the user's own combined categories.
 *
 * Unchecked, it leaves where it came from — which for a provider folder means the folder stops
 * showing it while All still does, so nothing is ever actually lost.
 */
@Composable
fun MoveToCategorySheet(
    originName: String,
    targets: List<Pair<String, String>>,
    onNewCategory: () -> Unit,
    onMove: (targetId: String, keepInOrigin: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTarget by remember { mutableStateOf<String?>(null) }
    var keepInOrigin by remember { mutableStateOf(false) }

    MobileBottomSheet(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.settings_move_category_title),
    ) {
        Text(
            text = stringResource(R.string.settings_move_category_description, originName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
        )
        MobileListRow(
            title = stringResource(R.string.settings_move_category_new),
            onClick = onNewCategory,
        )
        LazyColumn(Modifier.heightIn(max = sheetListHeight())) {
            items(targets, key = { it.first }) { (id, name) ->
                MobileListRow(
                    title = name,
                    leading = {
                        RadioButton(selected = selectedTarget == id, onClick = { selectedTarget = id })
                    },
                    onClick = { selectedTarget = id },
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MobileDimens.ScreenPaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = keepInOrigin, onCheckedChange = { keepInOrigin = it })
            Text(
                text = stringResource(R.string.settings_move_category_keep, originName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SheetButtons(
            confirm = stringResource(R.string.settings_move_category_action),
            confirmEnabled = selectedTarget != null,
            onConfirm = { selectedTarget?.let { onMove(it, keepInOrigin) } },
            onDismiss = onDismiss,
        )
    }
}

/** Name prompt for a brand-new combined category. */
@Composable
fun NewCategoryDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_move_category_new)) },
        text = {
            MobileTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.settings_move_category_title),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()); onDismiss() },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** Cancel and confirm, right-aligned under a sheet's list — the shape every one of these sheets ends in. */
@Composable
private fun SheetButtons(
    confirm: String,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MobileDimens.ScreenPaddingH),
        horizontalArrangement = Arrangement.spacedBy(MobileDimens.GapSmall, Alignment.End),
    ) {
        MobileButton(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss,
            style = MobileButtonStyle.TEXT,
        )
        MobileButton(
            text = confirm,
            onClick = { onConfirm(); onDismiss() },
            enabled = confirmEnabled,
        )
    }
}

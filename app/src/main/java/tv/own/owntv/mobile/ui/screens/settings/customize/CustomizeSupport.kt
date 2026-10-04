package tv.own.owntv.mobile.ui.screens.settings.customize

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.components.MobileTextField
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/**
 * One destination row in the "Move to…" dialog: a user-created custom category plus how many items
 * it currently holds.
 */
data class MoveTarget(val id: String, val displayName: String, val count: Int)

/**
 * Ask for one line of text — rename a category, name a new one, type a rule's value.
 *
 * [allowBlank] is what makes a rename able to undo itself: clearing the field and saving restores
 * the provider's own name, which is otherwise unreachable once a custom one is set.
 */
@Composable
fun TextPromptDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    description: String? = null,
    hint: String? = null,
    allowBlank: Boolean = true,
    confirmLabel: String = stringResource(R.string.common_save),
    extraButton: (@Composable () -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodyMedium)
                }
                MobileTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = title,
                    supportingText = hint,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = allowBlank || text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                extraButton?.invoke()
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

/** A short confirm — delete a category, restore original names, refuse an oversized rename. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** A one-of-these picker as a dialog, for the pickers that open on top of another dialog. */
@Composable
fun <T> OptionsDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Pick a custom category to move an item into, and decide whether the item also stays where it is.
 *
 * Moving out of a provider folder never deletes anything: the item keeps its place in All, in
 * search and in recents — it simply stops appearing in the folder it came from.
 */
@Composable
fun MoveToCategoryDialog(
    moveTargets: List<MoveTarget>,
    originName: String,
    onNewCategory: () -> Unit,
    onMove: (targetId: String, keepInOrigin: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedTarget by remember { mutableStateOf<String?>(null) }
    var keepInOrigin by remember { mutableStateOf(false) }
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_move_category_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.settings_move_category_description, originName),
                    style = MaterialTheme.typography.bodyMedium,
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    item(key = "new") {
                        MobileListRow(
                            title = stringResource(R.string.settings_move_category_new),
                            onClick = onNewCategory,
                        )
                    }
                    items(moveTargets, key = { it.id }) { target ->
                        MobileListRow(
                            title = target.displayName,
                            subtitle = stringResource(R.string.common_number_grouped, target.count),
                            onClick = { selectedTarget = target.id },
                            leading = {
                                RadioButton(
                                    selected = selectedTarget == target.id,
                                    onClick = { selectedTarget = target.id },
                                )
                            },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = keepInOrigin, onCheckedChange = { keepInOrigin = it })
                    Text(
                        stringResource(R.string.settings_move_category_keep, originName),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selectedTarget?.let { onMove(it, keepInOrigin) } },
                enabled = selectedTarget != null,
            ) { Text(stringResource(R.string.settings_move_category_action)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** The tick a chosen filter/sort option carries in a bottom sheet. */
fun selectedTick(selected: Boolean): (@Composable () -> Unit)? =
    if (!selected) null else {
        {
            Icon(
                imageVector = MobileIcons.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import tv.own.owntv.core.customize.BulkRenameSession
import tv.own.owntv.core.customize.RenameRules
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/** Which field of a rule row a nested picker is editing. */
private enum class RuleField { TYPE, PLACEMENT, VALUE }

/**
 * Renames a whole batch of folders or items at once: build a rule or two, look at what every name
 * would become, then keep the ones you want.
 *
 * Nothing is written until the review is finished, so a rule that turns out wrong costs a tap on
 * Decline rather than an undo that does not exist.
 */
@Composable
fun BulkRenameFlow(session: BulkRenameSession) {
    when (session.screen.collectAsStateWithLifecycle().value) {
        BulkRenameSession.Screen.CHOICE -> BulkChoiceSheet(session)
        BulkRenameSession.Screen.BUILDER -> BulkRuleBuilderDialog(session)
        BulkRenameSession.Screen.REVIEW -> BulkReviewDialog(session)
        BulkRenameSession.Screen.RESTORE_CONFIRM -> ConfirmDialog(
            title = stringResource(R.string.settings_bulk_rename_restore_title),
            message = stringResource(R.string.settings_bulk_rename_restore_description),
            confirmLabel = stringResource(R.string.settings_bulk_rename_restore),
            onConfirm = { session.confirmRestore() },
            onDismiss = { session.backToChoice() },
        )
        BulkRenameSession.Screen.REFUSED -> AlertDialog(
            modifier = Modifier.glassDialogWindow(),
            onDismissRequest = { session.dismissRefused() },
            title = { Text(stringResource(R.string.settings_bulk_rename_too_many_title)) },
            text = { Text(stringResource(R.string.settings_bulk_rename_too_many_description)) },
            confirmButton = {
                TextButton(onClick = { session.dismissRefused() }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
        BulkRenameSession.Screen.NONE -> Unit
    }
}

/** The three ways in: write rules, run the cleanup presets, or put the original names back. */
@Composable
private fun BulkChoiceSheet(session: BulkRenameSession) {
    val count = session.entries.collectAsStateWithLifecycle().value.size
    MobileBottomSheet(
        onDismissRequest = { session.close() },
        title = stringResource(R.string.settings_bulk_rename_title),
    ) {
        Text(
            pluralStringResource(R.plurals.settings_bulk_rename_selected, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        MobileListRow(
            title = stringResource(R.string.settings_bulk_rename_add_rule),
            onClick = { session.openBuilder() },
        )
        MobileListRow(
            title = stringResource(R.string.settings_bulk_rename_auto_cleanup),
            onClick = { session.autoCleanup() },
        )
        MobileListRow(
            title = stringResource(R.string.settings_bulk_rename_restore_original),
            onClick = { session.requestRestore() },
        )
    }
}

/** Rows of "add/remove this text before/after the name", plus the two matching options. */
@Composable
private fun BulkRuleBuilderDialog(session: BulkRenameSession) {
    val rules by session.rules.collectAsStateWithLifecycle()
    val options by session.options.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf(rules) }
    var trim by remember { mutableStateOf(options.trimLeftovers) }
    var ignoreCase by remember { mutableStateOf(options.ignoreCase) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Pair<Int, RuleField>?>(null) }

    fun submit() {
        val out = mutableListOf<RenameRules.Rule>()
        for (rule in draft) {
            if (rule.pattern != null) {
                out += rule
            } else if (rule.action == RenameRules.Action.ADD) {
                if (RenameRules.tokensOf(rule).size != 1) {
                    errorRes = R.string.settings_bulk_rename_add_single_value_error
                    return
                }
                out += rule
            } else if (RenameRules.tokensOf(rule).isNotEmpty()) {
                out += rule
            }
        }
        if (out.isEmpty()) {
            errorRes = R.string.settings_bulk_rename_rule_required
            return
        }
        errorRes = null
        session.submitRules(out, RenameRules.Options(trimLeftovers = trim, ignoreCase = ignoreCase))
    }

    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = { session.backToChoice() },
        title = { Text(stringResource(R.string.settings_bulk_rename_rules_title)) },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.settings_bulk_rename_rules_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                draft.forEachIndexed { i, rule ->
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AssistChip(
                            onClick = { editing = i to RuleField.TYPE },
                            label = {
                                Text(
                                    stringResource(
                                        if (rule.action == RenameRules.Action.ADD) {
                                            R.string.settings_bulk_rename_action_add
                                        } else {
                                            R.string.settings_bulk_rename_action_remove
                                        },
                                    ),
                                )
                            },
                        )
                        AssistChip(
                            onClick = { editing = i to RuleField.PLACEMENT },
                            label = {
                                Text(
                                    stringResource(
                                        if (rule.placement == RenameRules.Placement.PREFIX) {
                                            R.string.settings_bulk_rename_before
                                        } else {
                                            R.string.settings_bulk_rename_after
                                        },
                                    ),
                                )
                            },
                        )
                        AssistChip(
                            onClick = { editing = i to RuleField.VALUE },
                            modifier = Modifier.weight(1f),
                            label = {
                                Text(
                                    text = rule.autoLabel?.let { stringResource(it.labelRes()) }
                                        ?: rule.value.ifBlank {
                                            stringResource(R.string.settings_bulk_rename_value_example)
                                        },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                        IconButton(onClick = { draft = draft.toMutableList().apply { removeAt(i) } }) {
                            Icon(
                                imageVector = MobileIcons.Close,
                                contentDescription = stringResource(R.string.common_delete),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = {
                        draft = draft + RenameRules.Rule(
                            RenameRules.Action.ADD,
                            RenameRules.Placement.PREFIX,
                            "",
                        )
                    },
                ) { Text(stringResource(R.string.settings_bulk_rename_add_another_rule)) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = trim,
                        onClick = { trim = !trim },
                        label = { Text(stringResource(R.string.settings_bulk_rename_trim_spaces)) },
                    )
                    FilterChip(
                        selected = ignoreCase,
                        onClick = { ignoreCase = !ignoreCase },
                        label = { Text(stringResource(R.string.settings_bulk_rename_ignore_case)) },
                    )
                }
                errorRes?.let {
                    Text(
                        stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) {
                Text(stringResource(R.string.settings_bulk_rename_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = { session.backToChoice() }) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )

    val target = editing
    when (target?.second) {
        RuleField.TYPE -> OptionsDialog(
            title = stringResource(R.string.settings_bulk_rename_rule_type),
            options = listOf(
                RenameRules.Action.ADD to stringResource(R.string.settings_bulk_rename_action_add),
                RenameRules.Action.REMOVE to stringResource(R.string.settings_bulk_rename_action_remove),
            ),
            selected = draft[target.first].action,
            onSelect = { action ->
                draft = draft.toMutableList().apply {
                    set(target.first, get(target.first).copy(action = action, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.PLACEMENT -> OptionsDialog(
            title = stringResource(R.string.settings_bulk_rename_where),
            options = listOf(
                RenameRules.Placement.PREFIX to stringResource(R.string.settings_bulk_rename_before),
                RenameRules.Placement.SUFFIX to stringResource(R.string.settings_bulk_rename_after),
            ),
            selected = draft[target.first].placement,
            onSelect = { placement ->
                draft = draft.toMutableList().apply {
                    set(target.first, get(target.first).copy(placement = placement, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        RuleField.VALUE -> TextPromptDialog(
            title = stringResource(R.string.settings_bulk_rename_rule_value),
            initial = draft[target.first].value,
            hint = stringResource(R.string.settings_bulk_rename_value_hint),
            onConfirm = { value ->
                draft = draft.toMutableList().apply {
                    set(target.first, get(target.first).copy(value = value, pattern = null, autoLabel = null))
                }
                editing = null
            },
            onDismiss = { editing = null },
        )
        null -> Unit
    }
}

/** Every proposed name, with Apply and Decline on each row and on the batch as a whole. */
@Composable
private fun BulkReviewDialog(session: BulkRenameSession) {
    val rows by session.preview.collectAsStateWithLifecycle()
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = { session.done() },
        title = { Text(stringResource(R.string.settings_bulk_rename_review)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.settings_bulk_rename_review_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val changed = rows.count { !it.unchanged }
                val unchanged = rows.count { it.unchanged }
                val duplicates = rows.count { it.duplicate }
                Text(
                    pluralStringResource(R.plurals.settings_bulk_rename_will_change, changed, changed),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    pluralStringResource(R.plurals.settings_bulk_rename_unchanged_count, unchanged, unchanged),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(R.plurals.settings_bulk_rename_duplicates_count, duplicates, duplicates),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error,
                )
                LazyColumn(
                    Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(rows, key = { it.key }) { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    row.oldName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = when {
                                        row.blankRejected -> stringResource(R.string.settings_bulk_rename_blank_rejected)
                                        row.unchanged -> stringResource(R.string.settings_bulk_rename_unchanged)
                                        else -> stringResource(R.string.settings_bulk_rename_result, row.newName)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when {
                                        row.duplicate -> MaterialTheme.colorScheme.error
                                        row.unchanged -> MaterialTheme.colorScheme.onSurfaceVariant
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.duplicate) {
                                    Text(
                                        stringResource(R.string.settings_bulk_rename_duplicate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (!row.unchanged) {
                                TextButton(onClick = { session.applyRows(setOf(row.key)) }) {
                                    Text(stringResource(R.string.settings_bulk_rename_apply))
                                }
                                TextButton(onClick = { session.declineRows(setOf(row.key)) }) {
                                    Text(stringResource(R.string.settings_bulk_rename_decline))
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { session.applyAll() }) {
                        Text(stringResource(R.string.settings_bulk_rename_apply_all))
                    }
                    TextButton(onClick = { session.declineAll() }) {
                        Text(stringResource(R.string.settings_bulk_rename_decline_all))
                    }
                    TextButton(onClick = { session.editRules() }) {
                        Text(stringResource(R.string.settings_bulk_rename_edit_rules))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { session.done() }) { Text(stringResource(R.string.common_done)) }
        },
    )
}

private fun RenameRules.AutoLabel.labelRes() = when (this) {
    RenameRules.AutoLabel.COUNTRY_PROVIDER -> R.string.settings_bulk_rename_auto_country_provider
    RenameRules.AutoLabel.QUALITY_CODEC -> R.string.settings_bulk_rename_auto_quality_codec
    RenameRules.AutoLabel.EMOJI_SYMBOLS -> R.string.settings_bulk_rename_auto_emoji_symbols
}

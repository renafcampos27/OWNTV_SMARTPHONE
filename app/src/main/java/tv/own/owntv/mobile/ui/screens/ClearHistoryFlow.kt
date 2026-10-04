package tv.own.owntv.mobile.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileBottomSheet
import tv.own.owntv.mobile.ui.components.MobileListRow
import tv.own.owntv.mobile.ui.theme.MobileDimens
import tv.own.owntv.mobile.ui.theme.glassDialogWindow

/** The history scopes the clear-history sheet offers, "All history" first. */
private val HISTORY_SCOPES: List<MediaType?> =
    listOf(null, MediaType.LIVE, MediaType.MOVIE, MediaType.SERIES)

/**
 * Throwing watch history away: pick a scope, then confirm it.
 *
 * Lifted out of the old Settings → Data page **unchanged** — the same title, the same description,
 * the same four scopes in the same order and the same confirm. Only its door moved: it is reached
 * from the Watch history screen it acts on, not from three levels inside Settings.
 */
@Composable
fun ClearHistoryFlow(onClear: (MediaType?) -> Unit, onDismiss: () -> Unit) {
    // An index, not the scope itself: null is a real scope here — it means "all history".
    var confirming by remember { mutableIntStateOf(-1) }

    if (confirming < 0) {
        // Not a SettingsChoiceSheet: every row here is an action, so none of them is "the current one".
        MobileBottomSheet(
            onDismissRequest = onDismiss,
            title = stringResource(R.string.settings_clear_history),
        ) {
            Text(
                text = stringResource(R.string.settings_choose_history),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = MobileDimens.ScreenPaddingH),
            )
            HISTORY_SCOPES.forEachIndexed { index, scope ->
                MobileListRow(
                    title = stringResource(scope.scopeLabelRes()),
                    onClick = { confirming = index },
                )
            }
        }
        return
    }

    val scope = HISTORY_SCOPES[confirming]
    AlertDialog(
        modifier = Modifier.glassDialogWindow(),
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    R.string.settings_clear_history_confirm,
                    stringResource(scope.scopeLabelRes()),
                ),
            )
        },
        text = { Text(stringResource(R.string.settings_cannot_undo)) },
        confirmButton = {
            TextButton(onClick = { onClear(scope); onDismiss() }) {
                Text(stringResource(R.string.settings_yes_clear))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_no)) }
        },
    )
}

private fun MediaType?.scopeLabelRes() = when (this) {
    MediaType.LIVE -> R.string.settings_history_live
    MediaType.MOVIE -> R.string.settings_history_movies
    MediaType.SERIES -> R.string.settings_history_series
    else -> R.string.settings_all_history
}

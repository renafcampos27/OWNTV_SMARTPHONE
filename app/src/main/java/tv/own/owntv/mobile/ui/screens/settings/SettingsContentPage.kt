package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Content settings use the same leaf registry as navigation and search. */
@Composable
fun SettingsContentPage(
    onOpenLeaf: (SettingsLeaf) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier) {
        settingsLeafRows(SettingsGroup.CONTENT, onOpenLeaf)
    }
}

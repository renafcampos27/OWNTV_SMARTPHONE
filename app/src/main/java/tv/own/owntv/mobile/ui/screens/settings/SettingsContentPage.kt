package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Three rows, one per screen-sized feature: what the user renamed or hid, where film details come
 * from, and the subtitle account.
 *
 * The television has the same three as separate screens. Phase 10 had nowhere to put Customize and
 * OpenSubtitles, so the page opened straight onto the TMDB switches and looked like it was only
 * about metadata; the tree fixes that by giving each of the three its own route.
 */
@Composable
fun SettingsContentPage(
    onOpenLeaf: (SettingsLeaf) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier) {
        settingsLeafRows(SettingsGroup.CONTENT, onOpenLeaf)
    }
}

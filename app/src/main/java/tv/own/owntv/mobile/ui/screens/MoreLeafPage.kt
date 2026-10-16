package tv.own.owntv.mobile.ui.screens

import androidx.compose.runtime.Composable
import tv.own.owntv.mobile.ui.screens.settings.AboutPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsBackupPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsErrorLogPage
import tv.own.owntv.mobile.ui.screens.settings.SettingsProfilePage

/**
 * One More page, chosen by its route — the same shape `SettingsGroupPage` has, and for the same
 * reason: a page is wired once, so it can never be reachable by a route and missing from it.
 *
 * Routes for recordings, profiles, backup, diagnostics and version details.
 */
@Composable
fun MoreLeafPage(
    leaf: MoreLeaf,
    onOpenPlayer: () -> Unit,
) {
    when (leaf) {
        MoreLeaf.RECORDINGS -> tv.own.owntv.mobile.ui.screens.recordings.RecordingsScreen(onPlayerOpened = onOpenPlayer)
        MoreLeaf.PROFILES -> SettingsProfilePage()
        MoreLeaf.BACKUP -> SettingsBackupPage()
        MoreLeaf.ERROR_LOG -> SettingsErrorLogPage()
        MoreLeaf.ABOUT -> AboutPage()
    }
}

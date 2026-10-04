package tv.own.owntv.mobile.ui.screens

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import tv.own.owntv.mobile.R
import tv.own.owntv.mobile.ui.components.MobileIcons

/**
 * A page that hangs off the More tab.
 *
 * These are the things that were never settings — a profile manager, two lists of your own data, a
 * place that talks to another device, a log, and a page of facts. Each is a route of its own, and
 * **deliberately not a `settings/...` route**: an item that leaves Settings leaves, so the settings
 * search can no longer find it and a result can never point somewhere the row is not.
 *
 * Route, the bar's title and the More row itself all read this list, so a page can never be
 * reachable by one and invisible to another.
 */
enum class MoreLeaf(
    slug: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val summaryRes: Int?,
    val icon: ImageVector,
) {
    RECORDINGS("recordings", R.string.recording_title, null, MobileIcons.Download),
    PROFILES("profiles", R.string.profiles_title, null, MobileIcons.People),
    FAVORITES("favorites", R.string.content_category_favorites, null, MobileIcons.Favorite),
    HISTORY("history", R.string.content_category_history, null, MobileIcons.History),
    BACKUP(
        "backup",
        R.string.settings_backup_title,
        R.string.settings_backup_restore_description,
        MobileIcons.Save,
    ),
    LOCAL_SYNC("localsync", R.string.local_sync_title, R.string.local_sync_description, MobileIcons.Sync),
    ERROR_LOG(
        "errorlog",
        R.string.settings_playback_error_log,
        R.string.settings_playback_error_description,
        MobileIcons.BugReport,
    ),
    ABOUT("about", R.string.settings_about, R.string.settings_about_description, MobileIcons.Info),
    ;

    /** `more/backup` — the tab's own route with the page hung off it. */
    val route: String = "more/$slug"
}

/** The bar's title for a More page, or null when the route is not one. */
@StringRes
fun morePageTitleRes(route: String?): Int? =
    MoreLeaf.entries.firstOrNull { it.route == route }?.titleRes

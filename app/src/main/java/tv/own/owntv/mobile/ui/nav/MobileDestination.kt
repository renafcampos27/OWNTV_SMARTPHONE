package tv.own.owntv.mobile.ui.nav

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import tv.own.owntv.core.nav.MainSection
import tv.own.owntv.mobile.R

/**
 * Where the phone can navigate to.
 *
 * [sections] is what ties a destination to core's visibility rule: a destination shows only when at
 * least one of its sections is in `NavVisibility.visibleSections()`. Library is one tab covering two
 * sections, which is why the rule is a set and not a single value — a movies-only playlist still
 * gets a Library tab, a channels-only one does not.
 *
 * MORE has no section: it is always reachable, because Settings, Profiles and Downloads live behind
 * it and a user must never be able to hide their way out of the app's own settings.
 */
enum class MobileDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val sections: Set<MainSection>,
) {
    HOME("home", R.string.common_nav_home, MobileIcons.Home, setOf(MainSection.HOME)),
    LIVE("live", R.string.common_nav_live_tv, MobileIcons.LiveTv, setOf(MainSection.LIVE_TV)),
    LIBRARY(
        "library",
        R.string.common_nav_library,
        MobileIcons.VideoLibrary,
        setOf(MainSection.MOVIES, MainSection.SERIES),
    ),
    GUIDE("guide", R.string.common_nav_guide, MobileIcons.CalendarMonth, setOf(MainSection.EPG)),
    MORE("more", R.string.common_nav_more, MobileIcons.MoreHoriz, emptySet()),

    // Rail-only. On a phone these live inside Library and More; on a tablet there is room to show
    // them as destinations of their own, which is what step 3 of the plan calls the eight-item rail.
    MOVIES("movies", R.string.common_nav_movies, MobileIcons.Movie, setOf(MainSection.MOVIES)),
    SERIES("series", R.string.common_nav_series, MobileIcons.Theaters, setOf(MainSection.SERIES)),
    DOWNLOADS(
        "downloads",
        R.string.common_nav_downloads,
        MobileIcons.Download,
        setOf(MainSection.DOWNLOADS),
    ),
    SETTINGS("settings", R.string.common_nav_settings, MobileIcons.Settings, emptySet());

    companion object {
        /** The bottom bar, in order. Library stands in for Movies and Series on a narrow screen. */
        val bottomBar: List<MobileDestination> = listOf(LIVE, GUIDE, MORE)

        /**
         * The rail, in order. The same five places as the bottom bar, with Library split into
         * Movies and Series because a rail has the room for both.
         *
         * **Downloads and Settings are deliberately not on it.** They are not browse sections — they
         * are what More is for, on every width — and a rail that lists them says the tablet is a
         * different app from the phone rather than the same one with more room.
         */
        val rail: List<MobileDestination> =
            listOf(LIVE, GUIDE, MORE)

        /**
         * Filters a bar or rail to what the active playlist actually offers. A destination with no
         * sections is always kept; the rest survive if core says any of their sections is visible.
         */
        fun List<MobileDestination>.visible(sections: Set<MainSection>): List<MobileDestination> =
            filter { it.sections.isEmpty() || it.sections.any { section -> section in sections } }
    }
}

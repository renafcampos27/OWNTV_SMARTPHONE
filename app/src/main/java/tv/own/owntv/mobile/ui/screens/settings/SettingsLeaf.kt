package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.mobile.ui.components.MobileIcons
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import tv.own.owntv.mobile.R

/**
 * A screen-sized setting that hangs off a group page — the third level of the tree.
 *
 * The television has nineteen screens under its settings root, several of them features in their own
 * right rather than a switch: Customize, the Glass Effect, the video player, Backup. A flat page
 * cannot hold one, so each becomes a route of its own here, registered once with its group, its
 * title and the keywords search matches on. Route, breadcrumb, page title and the search index all
 * read this list, so a leaf can never be reachable by one and invisible to another.
 *
 * A leaf is registered when its screen exists. The remaining television screens — the navigation bar,
 * the long-press menus, DNS and About — are still rows on their group pages, and each moves here in
 * the phase that builds it out; registering an empty one now would put a dead row above content that
 * already works.
 */
enum class SettingsLeaf(
    val group: SettingsGroup,
    slug: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val keywordsRes: Int,
    @param:StringRes val summaryRes: Int? = null,
    val icon: ImageVector,
    /**
     * Whether the group's own page lists this leaf. Subtitle appearance is reached from inside Video
     * player, where it belongs — it is a setting *of* the player, not a sibling of it — so listing it
     * again beside Video player offered the same page by two routes and made Playback look like it
     * held two unrelated things.
     */
    val listedInGroup: Boolean = true,
) {
    PLAYLISTS(
        SettingsGroup.SOURCES, "playlists",
        R.string.settings_playlists, R.string.settings_search_keywords_playlists,
        R.string.settings_sources_description,
        MobileIcons.PlaylistPlay,
    ),
    EPG_SOURCES(
        SettingsGroup.SOURCES, "epg",
        R.string.settings_epg_sources, R.string.settings_search_keywords_epg,
        R.string.settings_epg_sources_description,
        MobileIcons.CalendarMonth,
    ),

    GLASS_EFFECT(
        SettingsGroup.APPEARANCE, "glass",
        R.string.settings_glass_effect, R.string.settings_search_keywords_glass,
        R.string.settings_glass_description,
        MobileIcons.AutoAwesome,
    ),
    FONTS(
        SettingsGroup.APPEARANCE, "fonts",
        R.string.settings_font_customization, R.string.settings_search_keywords_fonts,
        R.string.settings_font_customization_description,
        MobileIcons.TextFields,
    ),
    WEATHER(
        SettingsGroup.APPEARANCE, "weather",
        R.string.settings_weather, R.string.settings_search_keywords_weather,
        R.string.settings_weather_description_root,
        MobileIcons.WbSunny,
    ),

    CUSTOMIZE(
        SettingsGroup.CONTENT, "customize",
        R.string.settings_customize_title, R.string.settings_search_keywords_customize,
        R.string.settings_customize_nav_description,
        MobileIcons.Tune,
    ),
    METADATA(
        SettingsGroup.CONTENT, "metadata",
        R.string.settings_metadata, R.string.settings_search_keywords_metadata,
        R.string.settings_metadata_source_description,
        MobileIcons.Movie,
    ),
    OPEN_SUBTITLES(
        SettingsGroup.CONTENT, "opensubtitles",
        R.string.settings_open_subtitles, R.string.settings_search_keywords_subtitle_appearance,
        R.string.settings_open_subtitles_access_priority,
        MobileIcons.Subtitles,
    ),

    VIDEO_PLAYER(
        SettingsGroup.PLAYBACK, "video",
        R.string.settings_video_player, R.string.settings_search_keywords_video,
        R.string.settings_video_player_description,
        MobileIcons.PlayCircle,
    ),
    // Video player's categories: that page lists them, so the Playback page does not (see
    // [VIDEO_PLAYER_CATEGORIES]). Each is a page of its own, so back from one returns to the list.
    VP_PLAYER(
        SettingsGroup.PLAYBACK, "video-player",
        R.string.settings_vp_cat_player, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_player_summary,
        MobileIcons.PlayArrow,
        listedInGroup = false,
    ),
    VP_PICTURE(
        SettingsGroup.PLAYBACK, "video-picture",
        R.string.settings_vp_cat_picture, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_picture_summary,
        MobileIcons.Image,
        listedInGroup = false,
    ),
    VP_STREAMING(
        SettingsGroup.PLAYBACK, "video-streaming",
        R.string.settings_vp_cat_streaming, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_streaming_summary,
        MobileIcons.Wifi,
        listedInGroup = false,
    ),
    VP_LIVE(
        SettingsGroup.PLAYBACK, "video-live",
        R.string.settings_live_tv, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_live_summary,
        MobileIcons.LiveTv,
        listedInGroup = false,
    ),
    VP_LIVE_TUNING(
        SettingsGroup.PLAYBACK, "video-live-tuning",
        R.string.settings_vp_cat_live_tuning, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_live_tuning_summary,
        MobileIcons.Tune,
        listedInGroup = false,
    ),
    VP_CONTROLS(
        SettingsGroup.PLAYBACK, "video-controls",
        R.string.settings_vp_cat_controls, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_controls_summary,
        MobileIcons.FastForward,
        listedInGroup = false,
    ),
    VP_MULTIVIEW(
        SettingsGroup.PLAYBACK, "video-multiview",
        R.string.settings_multiview, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_multiview_summary_mobile,
        MobileIcons.GridView,
        listedInGroup = false,
    ),
    VP_SOUND(
        SettingsGroup.PLAYBACK, "video-sound",
        R.string.settings_vp_section_sound, R.string.settings_search_keywords_audio,
        R.string.settings_vp_cat_sound_summary,
        MobileIcons.VolumeUp,
        listedInGroup = false,
    ),
    VP_LANGUAGES(
        SettingsGroup.PLAYBACK, "video-languages",
        R.string.settings_vp_cat_languages, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_languages_summary,
        MobileIcons.Translate,
        listedInGroup = false,
    ),
    VP_RESUME(
        SettingsGroup.PLAYBACK, "video-resume",
        R.string.settings_vp_cat_resume, R.string.settings_search_keywords_video,
        R.string.settings_vp_cat_resume_summary,
        MobileIcons.SkipNext,
        listedInGroup = false,
    ),
    VP_DIAGNOSTICS(
        SettingsGroup.PLAYBACK, "video-diagnostics",
        R.string.settings_diagnostics, R.string.settings_search_keywords_video,
        R.string.settings_vp_section_diagnostics_summary,
        MobileIcons.Info,
        listedInGroup = false,
    ),
    SUBTITLE_APPEARANCE(
        SettingsGroup.PLAYBACK, "subtitles",
        R.string.settings_subtitle_appearance, R.string.settings_search_keywords_subtitle_appearance,
        R.string.settings_vp_section_subtitles_summary,
        MobileIcons.ClosedCaption,
        listedInGroup = false,
    ),
    CHANNEL_PLAYBACK(SettingsGroup.PLAYBACK, "channel-playback", R.string.own_channel_settings, R.string.settings_search_keywords_video, icon = MobileIcons.Tune),
    LIVE_CUSTOM(SettingsGroup.PLAYBACK, "live-custom", R.string.own_live_custom, R.string.settings_search_keywords_video, icon = MobileIcons.LiveTv),
    // Recording is a leaf under Playback, not a group of its own. A group holding exactly one page
    // still has to be opened to find out it holds one page, which is a press that tells nobody
    // anything — and the television puts it here too.
    RECORDING(
        SettingsGroup.PLAYBACK, "recording",
        R.string.recording_settings_group, R.string.settings_search_keywords_recording,
        R.string.recording_description,
        MobileIcons.LiveTv,
    ),

    HOME(
        SettingsGroup.LAYOUT, "home",
        R.string.settings_home_root, R.string.settings_search_keywords_home,
        R.string.settings_home_root_description,
        MobileIcons.Home,
    ),

    // Backup, Local sync and the error log used to be leaves here. They are not settings, so they
    // are More pages now — see `MoreLeaf`. Language is the only one of the App group's screens left.
    LANGUAGE(
        SettingsGroup.APP, "language",
        R.string.settings_language, R.string.settings_search_keywords_language,
        R.string.settings_language_description,
        MobileIcons.Translate,
    ),
    ;

    /** `settings/content/customize` — the group's own route with the leaf hung off it. */
    val route: String = "${group.route}/$slug"
}

/** The leaf a route names, or null when the route is a group page or not settings at all. */
fun settingsLeafOf(route: String?): SettingsLeaf? =
    SettingsLeaf.entries.firstOrNull { it.route == route }

/** The leaves of one group, in declaration order — what a group page lists at its head. */
fun leavesOf(group: SettingsGroup): List<SettingsLeaf> =
    SettingsLeaf.entries.filter { it.group == group && it.listedInGroup && it != SettingsLeaf.METADATA && it != SettingsLeaf.HOME }

/**
 * Video player's categories, in the order its page lists them. The television has the same ones plus
 * Frame rate, which on a phone is a single row and lives in Picture.
 */
val VIDEO_PLAYER_CATEGORIES: List<SettingsLeaf> = listOf(
    SettingsLeaf.VP_PLAYER, SettingsLeaf.VP_PICTURE, SettingsLeaf.VP_STREAMING, SettingsLeaf.VP_LIVE,
    SettingsLeaf.VP_LIVE_TUNING, SettingsLeaf.VP_CONTROLS, SettingsLeaf.VP_MULTIVIEW, SettingsLeaf.VP_SOUND,
    SettingsLeaf.VP_LANGUAGES, SettingsLeaf.VP_RESUME, SettingsLeaf.VP_DIAGNOSTICS,
)

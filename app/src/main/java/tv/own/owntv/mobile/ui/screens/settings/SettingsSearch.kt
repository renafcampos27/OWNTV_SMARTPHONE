package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import tv.own.owntv.mobile.R

/**
 * One searchable setting. The keyword strings are core's own — the same ones the TV app's settings
 * search matches on — so "cellular", "roaming" or "अंधेरा" find the row in whatever language the app
 * is running in, without a second list to keep in step.
 */
data class SettingsSearchEntry(
    val group: SettingsGroup,
    val leaf: SettingsLeaf?,
    val title: String,
    private val keywords: String,
) {
    val haystack: String = "$title $keywords".lowercase()

    /** Where tapping the result lands — the leaf that holds the row, or the group page. */
    val route: String = leaf?.route ?: group.route
}

/**
 * Every settings row worth finding by name.
 *
 * The leaves come from the registry rather than being listed twice, so a leaf added there is
 * searchable the moment it exists. Below them are the rows that sit directly on a group page, and
 * the rows deep inside a leaf that a user would still search for by name.
 */
@Composable
fun rememberSettingsSearchEntries(): List<SettingsSearchEntry> {
    // group, leaf (null when the row is on the group page itself), title, keywords
    fun rows(): List<SettingsRowEntry> = listOf(
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_decoder_queue, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_extra, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_group, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hide_categories, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hide_navigation, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hls_hint, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hls_only, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_priority, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_priority_hint, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_recovery, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_recovery_time, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_reserve, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_shift, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_shift_hint, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_software_audio, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_audio_delay, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_extra, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_format, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_hls_boundaries, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_hls_keyframes, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_hls_segments, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_latency, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_preroll, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_reserve, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_software_audio, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.own_select_folder, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.own_internal_quota, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.own_external_quota, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.own_archive_engine, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_reserve_mode, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_reserve_source, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_guide_delay, R.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_mini_player_style, R.string.settings_search_keywords_background_playback),
        // Settings on a page that search found only by the page's own name.
        SettingsRowEntry(SettingsGroup.SOURCES, SettingsLeaf.EPG_SOURCES, R.string.settings_epg_sources_auto_refresh_title, R.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_ambient_glow_pulse, R.string.settings_search_keywords_theme),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.FONTS, R.string.settings_font_size, R.string.settings_search_keywords_fonts),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.FONTS, R.string.settings_popup_font_size, R.string.settings_search_keywords_fonts),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_transparency_title, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_background_blur_title, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_background_image, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_highlight_title, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_depth_effects_short, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_full_transparency_short, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_shine_short, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, R.string.settings_glass_surface_all, R.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.WEATHER, R.string.settings_show_weather, R.string.settings_search_keywords_weather),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.WEATHER, R.string.settings_temperature_unit, R.string.settings_search_keywords_weather),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.WEATHER, R.string.settings_use_device_location, R.string.settings_search_keywords_weather),
        SettingsRowEntry(SettingsGroup.LAYOUT, null, R.string.settings_browsing_last_category, R.string.settings_search_keywords_browsing),
        SettingsRowEntry(SettingsGroup.LAYOUT, null, R.string.settings_browsing_last_item, R.string.settings_search_keywords_browsing),
        SettingsRowEntry(SettingsGroup.CONTENT, SettingsLeaf.OPEN_SUBTITLES, R.string.player_subtitles_search_language, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.CONTENT, SettingsLeaf.OPEN_SUBTITLES, R.string.player_subtitles_filter_title, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.CONTENT, SettingsLeaf.OPEN_SUBTITLES, R.string.player_subtitles_stay_signed_in, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.NETWORK, null, R.string.settings_use_proxy, R.string.settings_search_keywords_proxy),
        SettingsRowEntry(SettingsGroup.NETWORK, null, R.string.settings_dns_use_custom, R.string.settings_search_keywords_dns),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.content_epg_time_offset, R.string.settings_search_keywords_epg_offset),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.settings_epg_guide_days, R.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.settings_catchup, R.string.settings_search_keywords_catchup),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.settings_catchup_timezone_per_playlist, R.string.settings_search_keywords_catchup),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.settings_playlists, R.string.settings_search_keywords_playlists),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.settings_epg_sources, R.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.SOURCES, null, R.string.settings_epg_sources_use_logos, R.string.settings_search_keywords_logos),

        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_theme, R.string.settings_search_keywords_theme),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_accent, R.string.settings_search_keywords_accent),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_selection_highlight, R.string.settings_search_keywords_focus),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_ambient_glow, R.string.settings_search_keywords_theme),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_popup_size, R.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_ui_zoom, R.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, R.string.settings_animations, R.string.settings_search_keywords_animation),

        SettingsRowEntry(SettingsGroup.LAYOUT, null, R.string.settings_browsing_lists, R.string.settings_search_keywords_browsing),
        SettingsRowEntry(SettingsGroup.LAYOUT, null, R.string.settings_nav_bar_customization, R.string.settings_search_keywords_sidebar),

        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_quick_hdr, R.string.settings_search_keywords_hdr),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.settings_surround_sound, R.string.settings_search_keywords_surround),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, R.string.settings_live_latency, R.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, R.string.settings_live_preroll, R.string.settings_search_keywords_live_preroll),
        // The rest of the Video player page. Twenty-two rows had no entry at all, so searching for
        // "multiview", "engine" or "zoom" by name found nothing on a page that holds all three. They
        // share the generic video keyword set on purpose: each one's own title is the specific word a
        // user types, and the keywords are only there to catch the synonyms it does not contain.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_MULTIVIEW, R.string.settings_multiview, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_MULTIVIEW, R.string.settings_multiview_tiles_max, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.settings_live_tv_player, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.settings_live_engine_per_playlist, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.settings_reset_player_choices, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.settings_reset_live_player_choices, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.settings_forget_stream_fixes, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.settings_external_player, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_auto_frame_rate, R.string.settings_search_keywords_afr),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_default_zoom, R.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_reset_saved_zoom, R.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_CONTROLS, R.string.settings_seek_step, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_CONTROLS, R.string.settings_live_rewind_step, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, R.string.settings_live_tune_timeout, R.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, R.string.settings_live_tune_timeout_per_playlist, R.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, R.string.settings_live_latency_per_playlist, R.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, R.string.settings_live_preroll_per_playlist, R.string.settings_search_keywords_live_preroll),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.settings_audio_sync, R.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.settings_reset_saved_audio_delay, R.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LANGUAGES, R.string.settings_preferred_audio_language, R.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.settings_audio_passthrough, R.string.settings_search_keywords_surround),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_max_video_quality, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_mobile_data_quality, R.string.settings_search_keywords_data_saver),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LANGUAGES, R.string.settings_preferred_subtitle_language, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_RESUME, R.string.settings_resume_playback, R.string.settings_search_keywords_autoplay),
        // Switches drawn from the Quick registry rather than with a title of their own, which is how
        // they slipped past the coverage test.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.settings_hardware_decoding, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE, R.string.settings_channel_numbers, R.string.settings_search_keywords_channel_numbers),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, R.string.settings_measured_stats, R.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_RESUME, R.string.settings_autoplay_next, R.string.settings_search_keywords_autoplay),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, R.string.settings_detailed_playback_logging, R.string.settings_search_keywords_detailed_logging),
        // The rows inside the Recording and Subtitle appearance pages, by their own titles.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.settings_recording_reserve, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.settings_recording_mobile_data, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.settings_record_watching, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.settings_recording_pre_roll, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.settings_recording_post_roll, R.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, R.string.settings_subtitle_size, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, R.string.settings_subtitle_font, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, R.string.settings_subtitle_color_short, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, R.string.settings_subtitle_position_short, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, R.string.settings_subtitle_background_transparency, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, R.string.settings_subtitle_reset_all, R.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_background_playback, R.string.settings_search_keywords_background_playback),
        // The mobility layer. All seven reuse the three keyword sets they belong to rather than
        // adding their own: a user searching "floating window" or "battery" is asking about picture
        // in picture and background playback, which is exactly what those two lists already carry.
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_audio_on_screen_off, R.string.settings_search_keywords_background_playback),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_audio_on_mobile_data, R.string.settings_search_keywords_data_saver),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_audio_per_channel, R.string.settings_search_keywords_background_playback),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_data_saver, R.string.settings_search_keywords_data_saver),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, R.string.settings_gesture_sensitivity, R.string.settings_search_keywords_gestures),

        SettingsRowEntry(SettingsGroup.NETWORK, null, R.string.common_proxy, R.string.settings_search_keywords_proxy),
        // Custom DNS sits next to the proxy on the same page and the television has always indexed it.
        SettingsRowEntry(SettingsGroup.NETWORK, null, R.string.settings_dns, R.string.settings_search_keywords_dns),

        // No entries for the download folder, Wi-Fi-only, Clear history, Backup, Local sync, the
        // error log, About or Profiles: none of them is in Settings any more, and a result for
        // something that is not here is a lie about where it lives. The no-results state is left
        // exactly as it was — it must not gain a line explaining where anything went.
        SettingsRowEntry(SettingsGroup.APP, null, R.string.settings_app_icon, R.string.settings_app_icon_summary),
        SettingsRowEntry(SettingsGroup.APP, null, R.string.settings_app_startup, R.string.settings_search_keywords_startup),
        SettingsRowEntry(SettingsGroup.APP, null, R.string.settings_startup_specific_channel, R.string.settings_search_keywords_startup),
        // Detailed logging sits in the video player's own diagnostics block, not on the App page —
        // a result that lands somewhere the row is not is worse than no result at all.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, R.string.settings_diagnostics, R.string.settings_search_keywords_detailed_logging),
    )

    val leaves = SettingsLeaf.entries.filter { it != SettingsLeaf.METADATA && it != SettingsLeaf.HOME }.map {
        SettingsSearchEntry(it.group, it, stringResource(it.titleRes), stringResource(it.keywordsRes))
    }
    val resolved = leaves + rows().map {
        SettingsSearchEntry(
            it.group,
            it.leaf,
            stringResource(it.titleRes),
            stringResource(it.keywordsRes),
        )
    }
    return remember(resolved) { resolved }
}

/** A row's registration, before its strings are resolved. */
private class SettingsRowEntry(
    val group: SettingsGroup,
    val leaf: SettingsLeaf?,
    val titleRes: Int,
    val keywordsRes: Int,
)

/**
 * Every word must appear somewhere in the row, in any order — "wifi download" finds "Download over
 * Wi-Fi only" and so does "download wifi". A blank query matches nothing, because the root already
 * shows everything.
 */
fun List<SettingsSearchEntry>.matching(query: String): List<SettingsSearchEntry> {
    val tokens = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()
    return filter { entry -> tokens.all { entry.haystack.contains(it) } }
}

/**
 * "Playback › Video player › Live latency" — three levels when the row is inside a leaf, two when it
 * sits on the group page. Built by nesting the same two-part separator string rather than adding a
 * three-part one, so the separator can never differ between the halves.
 */
@Composable
fun settingsBreadcrumb(entry: SettingsSearchEntry): String {
    val group = stringResource(entry.group.titleRes)
    val leaf = entry.leaf ?: return stringResource(R.string.settings_breadcrumb, group, entry.title)
    if (leaf.titleRes == entry.group.titleRes) {
        return stringResource(R.string.settings_breadcrumb, group, entry.title)
    }
    val leafTitle = stringResource(leaf.titleRes)
    // A leaf's own result is already "Group › Leaf"; only a row inside one needs the third level.
    if (leafTitle == entry.title) {
        return stringResource(R.string.settings_breadcrumb, group, leafTitle)
    }
    val parent = stringResource(R.string.settings_breadcrumb, group, leafTitle)
    return stringResource(R.string.settings_breadcrumb, parent, entry.title)
}

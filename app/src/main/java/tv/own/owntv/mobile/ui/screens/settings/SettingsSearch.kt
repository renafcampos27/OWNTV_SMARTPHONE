package tv.own.owntv.mobile.ui.screens.settings

import tv.own.owntv.core.R as CoreR

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
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.own_audio_route, CoreR.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.own_output_sync, R.string.own_output_sync_hint),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.own_output_sync_offset, CoreR.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, R.string.own_audio_sync_total, R.string.own_audio_sync_total_hint),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, R.string.own_prefer_sdr, R.string.own_prefer_sdr_hint),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, R.string.own_video_recovery, R.string.own_video_recovery_hint),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_customized_only, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_decoder_queue, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_extra, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_group, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hide_categories, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hide_navigation, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hls_hint, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_hls_only, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_priority, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_priority_hint, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_recovery, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_recovery_time, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_reserve, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_shift, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_shift_hint, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_software_audio, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_audio_delay, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_extra, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_format, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_hls_boundaries, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_hls_keyframes, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_hls_segments, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_latency, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_preroll, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_reserve, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.CHANNEL_PLAYBACK, R.string.own_software_audio, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.own_select_folder, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.own_internal_quota, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, R.string.own_external_quota, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, R.string.own_archive_engine, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_reserve_mode, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_reserve_source, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.LIVE_CUSTOM, R.string.own_guide_delay, CoreR.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_mini_player_style, CoreR.string.settings_search_keywords_background_playback),
        // Settings on a page that search found only by the page's own name.
        SettingsRowEntry(SettingsGroup.SOURCES, SettingsLeaf.EPG_SOURCES, CoreR.string.settings_epg_sources_auto_refresh_title, CoreR.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_ambient_glow_pulse, CoreR.string.settings_search_keywords_theme),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.FONTS, CoreR.string.settings_font_size, CoreR.string.settings_search_keywords_fonts),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.FONTS, CoreR.string.settings_popup_font_size, CoreR.string.settings_search_keywords_fonts),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_transparency_title, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_background_blur_title, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_background_image, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_highlight_title, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_depth_effects_short, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_full_transparency_short, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_shine_short, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.APPEARANCE, SettingsLeaf.GLASS_EFFECT, CoreR.string.settings_glass_surface_all, CoreR.string.settings_search_keywords_glass),
        SettingsRowEntry(SettingsGroup.LAYOUT, null, CoreR.string.settings_browsing_last_category, CoreR.string.settings_search_keywords_browsing),
        SettingsRowEntry(SettingsGroup.LAYOUT, null, CoreR.string.settings_browsing_last_item, CoreR.string.settings_search_keywords_browsing),
        SettingsRowEntry(SettingsGroup.NETWORK, null, CoreR.string.settings_use_proxy, CoreR.string.settings_search_keywords_proxy),
        SettingsRowEntry(SettingsGroup.NETWORK, null, CoreR.string.settings_dns_use_custom, CoreR.string.settings_search_keywords_dns),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.content_epg_time_offset, CoreR.string.settings_search_keywords_epg_offset),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.settings_epg_guide_days, CoreR.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.settings_catchup, CoreR.string.settings_search_keywords_catchup),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.settings_catchup_timezone_per_playlist, CoreR.string.settings_search_keywords_catchup),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.settings_playlists, CoreR.string.settings_search_keywords_playlists),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.settings_epg_sources, CoreR.string.settings_search_keywords_epg),
        SettingsRowEntry(SettingsGroup.SOURCES, null, CoreR.string.settings_epg_sources_use_logos, CoreR.string.settings_search_keywords_logos),

        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_theme, CoreR.string.settings_search_keywords_theme),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_accent, CoreR.string.settings_search_keywords_accent),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_selection_highlight, CoreR.string.settings_search_keywords_focus),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_ambient_glow, CoreR.string.settings_search_keywords_theme),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_popup_size, CoreR.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.APPEARANCE, null, CoreR.string.settings_ui_zoom, CoreR.string.settings_search_keywords_zoom),

        SettingsRowEntry(SettingsGroup.LAYOUT, null, CoreR.string.settings_browsing_lists, CoreR.string.settings_search_keywords_browsing),
        SettingsRowEntry(SettingsGroup.LAYOUT, null, CoreR.string.settings_nav_bar_customization, CoreR.string.settings_search_keywords_sidebar),

        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_quick_hdr, CoreR.string.settings_search_keywords_hdr),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, CoreR.string.settings_surround_sound, CoreR.string.settings_search_keywords_surround),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, CoreR.string.settings_live_latency, CoreR.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, CoreR.string.settings_live_preroll, CoreR.string.settings_search_keywords_live_preroll),
        // The rest of the Video player page. Twenty-two rows had no entry at all, so searching for
        // "multiview", "engine" or "zoom" by name found nothing on a page that holds all three. They
        // share the generic video keyword set on purpose: each one's own title is the specific word a
        // user types, and the keywords are only there to catch the synonyms it does not contain.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_MULTIVIEW, CoreR.string.settings_multiview, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_MULTIVIEW, CoreR.string.settings_multiview_tiles_max, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, CoreR.string.settings_live_tv_player, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, CoreR.string.settings_live_engine_per_playlist, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, CoreR.string.settings_reset_player_choices, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, CoreR.string.settings_reset_live_player_choices, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PLAYER, CoreR.string.settings_external_player, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_auto_frame_rate, CoreR.string.settings_search_keywords_afr),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_default_zoom, CoreR.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_reset_saved_zoom, CoreR.string.settings_search_keywords_zoom),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_CONTROLS, CoreR.string.settings_seek_step, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_CONTROLS, CoreR.string.settings_live_rewind_step, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, CoreR.string.settings_live_tune_timeout, CoreR.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, CoreR.string.settings_live_tune_timeout_per_playlist, CoreR.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, CoreR.string.settings_live_latency_per_playlist, CoreR.string.settings_search_keywords_latency),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE_TUNING, CoreR.string.settings_live_preroll_per_playlist, CoreR.string.settings_search_keywords_live_preroll),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, CoreR.string.settings_audio_sync, CoreR.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, CoreR.string.settings_reset_saved_audio_delay, CoreR.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LANGUAGES, CoreR.string.settings_preferred_audio_language, CoreR.string.settings_search_keywords_audio),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_SOUND, CoreR.string.settings_audio_passthrough, CoreR.string.settings_search_keywords_surround),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_max_video_quality, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_mobile_data_quality, CoreR.string.settings_search_keywords_data_saver),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LANGUAGES, CoreR.string.settings_preferred_subtitle_language, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_RESUME, CoreR.string.settings_resume_playback, CoreR.string.settings_search_keywords_autoplay),
        // Switches drawn from the Quick registry rather than with a title of their own, which is how
        // they slipped past the coverage test.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_PICTURE, CoreR.string.settings_hardware_decoding, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_LIVE, CoreR.string.settings_channel_numbers, CoreR.string.settings_search_keywords_channel_numbers),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, CoreR.string.settings_measured_stats, CoreR.string.settings_search_keywords_video),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_RESUME, CoreR.string.settings_autoplay_next, CoreR.string.settings_search_keywords_autoplay),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, CoreR.string.settings_detailed_playback_logging, CoreR.string.settings_search_keywords_detailed_logging),
        // The rows inside the Recording and Subtitle appearance pages, by their own titles.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, CoreR.string.settings_recording_reserve, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, CoreR.string.settings_recording_mobile_data, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, CoreR.string.settings_record_watching, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, CoreR.string.settings_recording_pre_roll, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.RECORDING, CoreR.string.settings_recording_post_roll, CoreR.string.settings_search_keywords_recording),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, CoreR.string.settings_subtitle_size, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, CoreR.string.settings_subtitle_font, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, CoreR.string.settings_subtitle_color_short, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, CoreR.string.settings_subtitle_position_short, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, CoreR.string.settings_subtitle_background_transparency, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.SUBTITLE_APPEARANCE, CoreR.string.settings_subtitle_reset_all, CoreR.string.settings_search_keywords_subtitle_appearance),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_background_playback, CoreR.string.settings_search_keywords_background_playback),
        // The mobility layer. All seven reuse the three keyword sets they belong to rather than
        // adding their own: a user searching "floating window" or "battery" is asking about picture
        // in picture and background playback, which is exactly what those two lists already carry.
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_audio_on_screen_off, CoreR.string.settings_search_keywords_background_playback),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_audio_on_mobile_data, CoreR.string.settings_search_keywords_data_saver),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_audio_per_channel, CoreR.string.settings_search_keywords_background_playback),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_data_saver, CoreR.string.settings_search_keywords_data_saver),
        SettingsRowEntry(SettingsGroup.PLAYBACK, null, CoreR.string.settings_gesture_sensitivity, CoreR.string.settings_search_keywords_gestures),

        SettingsRowEntry(SettingsGroup.NETWORK, null, CoreR.string.common_proxy, CoreR.string.settings_search_keywords_proxy),
        // Custom DNS sits next to the proxy on the same page and the television has always indexed it.
        SettingsRowEntry(SettingsGroup.NETWORK, null, CoreR.string.settings_dns, CoreR.string.settings_search_keywords_dns),

        // No entries for the download folder, Wi-Fi-only, Clear history, Backup, Local sync, the
        // error log, About or Profiles: none of them is in Settings any more, and a result for
        // something that is not here is a lie about where it lives. The no-results state is left
        // exactly as it was — it must not gain a line explaining where anything went.
        SettingsRowEntry(SettingsGroup.APP, null, R.string.own_updates_check, CoreR.string.settings_search_keywords_updates),
        SettingsRowEntry(SettingsGroup.APP, null, R.string.own_updates_auto, CoreR.string.settings_search_keywords_update_auto),
        SettingsRowEntry(SettingsGroup.APP, null, CoreR.string.settings_app_icon, CoreR.string.settings_app_icon_summary),
        SettingsRowEntry(SettingsGroup.APP, null, CoreR.string.settings_app_startup, CoreR.string.settings_search_keywords_startup),
        SettingsRowEntry(SettingsGroup.APP, null, CoreR.string.settings_startup_specific_channel, CoreR.string.settings_search_keywords_startup),
        // Detailed logging sits in the video player's own diagnostics block, not on the App page —
        // a result that lands somewhere the row is not is worse than no result at all.
        SettingsRowEntry(SettingsGroup.PLAYBACK, SettingsLeaf.VP_DIAGNOSTICS, CoreR.string.settings_diagnostics, CoreR.string.settings_search_keywords_detailed_logging),
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
    val leaf = entry.leaf ?: return stringResource(CoreR.string.settings_breadcrumb, group, entry.title)
    if (leaf.titleRes == entry.group.titleRes) {
        return stringResource(CoreR.string.settings_breadcrumb, group, entry.title)
    }
    val leafTitle = stringResource(leaf.titleRes)
    // A leaf's own result is already "Group › Leaf"; only a row inside one needs the third level.
    if (leafTitle == entry.title) {
        return stringResource(CoreR.string.settings_breadcrumb, group, leafTitle)
    }
    val parent = stringResource(CoreR.string.settings_breadcrumb, group, leafTitle)
    return stringResource(CoreR.string.settings_breadcrumb, parent, entry.title)
}

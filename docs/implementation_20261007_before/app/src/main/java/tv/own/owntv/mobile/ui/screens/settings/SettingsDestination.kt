package tv.own.owntv.mobile.ui.screens.settings

import androidx.compose.runtime.Composable

/**
 * One settings page, chosen by its route.
 *
 * It exists because settings are reached two ways: as a route of their own on a phone, and inside
 * the right-hand pane on a tablet, where there is no back stack to navigate. Both go through this,
 * so a page can never be wired into one and forgotten in the other.
 *
 * [onOpenRoute] is what a row that leads somewhere calls — navigation on a phone, and a push onto
 * the pane's own stack on a tablet.
 */
@Composable
fun SettingsGroupPage(group: SettingsGroup, onOpenRoute: (String) -> Unit) {
    val openLeaf: (SettingsLeaf) -> Unit = { onOpenRoute(it.route) }
    when (group) {
        SettingsGroup.SOURCES -> SettingsSourcesPage(onOpenLeaf = openLeaf)
        SettingsGroup.APPEARANCE -> SettingsAppearancePage(onOpenLeaf = openLeaf)
        SettingsGroup.LAYOUT -> SettingsLayoutPage(onOpenLeaf = openLeaf)
        SettingsGroup.CONTENT -> SettingsContentPage(onOpenLeaf = openLeaf)
        SettingsGroup.PLAYBACK -> SettingsPlaybackPage(onOpenLeaf = openLeaf)
        SettingsGroup.NETWORK -> SettingsNetworkPage()
        SettingsGroup.APP -> SettingsAppPage(
            onOpenLanguage = { onOpenRoute(SettingsLeaf.LANGUAGE.route) },
        )
    }
}

/**
 * One leaf page. [onAddSource] is the setup flow, which is a route outside settings altogether and
 * so stays the caller's business in both layouts.
 */
@Composable
fun SettingsLeafPage(leaf: SettingsLeaf, onOpenRoute: (String) -> Unit, onAddSource: () -> Unit) {
    when (leaf) {
        SettingsLeaf.CHANNEL_PLAYBACK -> SettingsChannelPlaybackPage()
        SettingsLeaf.LIVE_CUSTOM -> SettingsLiveCustomPage()
        SettingsLeaf.PLAYLISTS -> SettingsPlaylistsPage(onAddSource = onAddSource)
        SettingsLeaf.EPG_SOURCES -> SettingsEpgSourcesPage()
        SettingsLeaf.GLASS_EFFECT -> SettingsGlassPage()
        SettingsLeaf.FONTS -> SettingsFontsPage()
        SettingsLeaf.WEATHER -> SettingsWeatherPage()
        SettingsLeaf.CUSTOMIZE -> SettingsCustomizePage()
        SettingsLeaf.METADATA -> SettingsMetadataPage()
        SettingsLeaf.OPEN_SUBTITLES -> SettingsOpenSubtitlesPage()
        SettingsLeaf.VIDEO_PLAYER -> SettingsVideoPlayerPage(
            onOpenLeaf = { target -> onOpenRoute(target.route) },
        )
        SettingsLeaf.VP_PLAYER, SettingsLeaf.VP_PICTURE, SettingsLeaf.VP_STREAMING, SettingsLeaf.VP_LIVE,
        SettingsLeaf.VP_LIVE_TUNING, SettingsLeaf.VP_CONTROLS, SettingsLeaf.VP_MULTIVIEW, SettingsLeaf.VP_SOUND,
        SettingsLeaf.VP_LANGUAGES, SettingsLeaf.VP_RESUME, SettingsLeaf.VP_DIAGNOSTICS -> SettingsVideoPlayerPage(
            onOpenLeaf = { target -> onOpenRoute(target.route) },
            category = leaf,
        )
        SettingsLeaf.SUBTITLE_APPEARANCE -> SettingsSubtitleAppearancePage()
        SettingsLeaf.RECORDING -> SettingsRecordingPage()
        SettingsLeaf.HOME -> SettingsHomePage()
        SettingsLeaf.LANGUAGE -> SettingsLanguagePage()
    }
}

/** Whichever of the two a route names, or nothing when it is not a settings route at all. */
@Composable
fun SettingsRoutePage(route: String, onOpenRoute: (String) -> Unit, onAddSource: () -> Unit) {
    SettingsGroup.entries.firstOrNull { it.route == route }?.let {
        SettingsGroupPage(it, onOpenRoute)
        return
    }
    SettingsLeaf.entries.firstOrNull { it.route == route }?.let {
        SettingsLeafPage(it, onOpenRoute, onAddSource)
    }
}

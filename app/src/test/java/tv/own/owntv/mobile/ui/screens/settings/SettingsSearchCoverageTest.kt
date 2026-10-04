package tv.own.owntv.mobile.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Settings search keeps a hand-written list of rows beside the real pages, and it had drifted: the
 * whole Video player page except six rows was unfindable by name — Multiview, both engine pickers,
 * the zoom, seek and volume steps, the language preferences — and so were custom DNS and the nav bar.
 *
 * Pages cannot drift, because they come from `SettingsLeaf.entries`. Rows can, and this is what holds
 * them: every row title drawn on the Video player page must appear in the search list.
 *
 * Source-scanned rather than executed — these are Compose pages needing a full Android graph, and the
 * thing worth checking is the text itself.
 */
class SettingsSearchCoverageTest {

    private fun read(name: String): String {
        val file = File("src/main/java/tv/own/owntv/mobile/ui/screens/settings/$name")
        assertTrue("expected to run from the app module, cwd=${File(".").absolutePath}", file.isFile)
        return file.readText()
    }

    private val search: String by lazy { read("SettingsSearch.kt") }

    /**
     * Titles search can match, by string resource name — the hand-listed rows *and* the leaf registry,
     * since a row that opens a whole page is already findable under that page's own name.
     */
    private val indexed: Set<String> by lazy {
        Regex("""R\.string\.([a-z_0-9]+)""")
            .findAll(search + read("SettingsLeaf.kt"))
            .map { it.groupValues[1] }
            .toSet()
    }

    /**
     * Rows on the page that are deliberately not indexed on their own: the picker labels and "Custom"
     * chips that are parts of a row rather than rows, and the three nav titles reused as section heads.
     */
    private val notRows = setOf(
        "common_nav_live_tv", "common_nav_movies", "common_nav_series",
        "settings_live_latency_custom", "settings_live_preroll_playlist_picker",
    )

    @Test
    fun `every video player row can be found by search`() {
        val page = read("SettingsPlaybackPage.kt")
        val titles = Regex("""title = stringResource\(R\.string\.([a-z_0-9]+)""")
            .findAll(page).map { it.groupValues[1] }
            .filterNot { it.endsWith("_description") || it.endsWith("_description_mobile") || it in notRows }
            .toSortedSet()
        assertTrue("no rows found on the Video player page — has the file changed shape?", titles.size > 20)
        assertEquals(
            "Video player rows missing from the settings search index — unfindable by name",
            emptyList<String>(),
            titles.filterNot { it in indexed }.sorted(),
        )
    }

    @Test
    fun `every quick switch on the video player page can be found by search`() {
        // These rows take their title from the Quick registry, not from a `title =` of their own, so
        // the test above never saw them — Hardware decoding, Channel numbers and Measured stats were
        // unfindable by name.
        val quick = read("SettingsQuick.kt")
        val titleOf = Regex("""QuickToggle\(\s*"([a-z_]+)",\s*R\.string\.([a-z_0-9]+)""")
            .findAll(quick).associate { it.groupValues[1] to it.groupValues[2] }
        val keys = Regex("""quickToggle\("([a-z_]+)"\)""")
            .findAll(read("SettingsPlaybackPage.kt")).map { it.groupValues[1] }.toList()
        assertTrue("no quick switches found on the Video player page", keys.size > 3)
        assertEquals(
            "Video player switches missing from the settings search index",
            emptyList<String>(),
            keys.map { titleOf.getValue(it) }.filterNot { it in indexed }.sorted(),
        )
    }

    @Test
    fun `every recording and subtitle appearance row can be found by search`() {
        // Dialog titles and picker options on the subtitle page are parts of a row, not rows. The
        // page's master switch is the page's own title, indexed with the leaf.
        val notRows = setOf("settings_subtitle_color", "settings_subtitle_default", "settings_color_picker")
        val titles = listOf("SettingsRecordingPage.kt", "SettingsSubtitleAppearancePage.kt").flatMap { page ->
            Regex("""(?<![a-z])title = stringResource\(R\.string\.([a-z_0-9]+)""").findAll(read(page)).map { it.groupValues[1] }
        }.filterNot { it in notRows }.toSortedSet()
        assertTrue(titles.size >= 10)
        assertEquals(emptyList<String>(), titles.filterNot { it in indexed })
    }

    @Test
    fun `every settings page is indexed automatically`() {
        // The leaves are mapped from the registry rather than listed again; if that ever becomes a
        // hand-written list, pages start going missing the way the rows did.
        assertTrue(
            "SettingsSearch no longer derives its page entries from SettingsLeaf.entries",
            search.contains("SettingsLeaf.entries"),
        )
    }

    @Test
    fun `search entries name a real string resource`() {
        // A typo'd resource would not compile, but a row entry pointing at a *description* string reads
        // as a sentence in the results instead of a title.
        assertEquals(
            "search entries should use a row's title, not its description",
            emptyList<String>(),
            Regex("""SettingsRowEntry\([^)]*R\.string\.([a-z_0-9]*_description)""")
                .findAll(search).map { it.groupValues[1] }.toList(),
        )
    }

    /**
     * Every setting on every settings page, not only the Video player's. Sheet titles, picker options,
     * one-off actions and status lines are not settings and are listed here instead; Backup and Local
     * sync are More pages, not settings.
     */
    @Test
    fun `every setting on every settings page can be found by search`() {
        val notSettings = setOf(
            "settings_about", "settings_about_version", "settings_app_startup_dialog", "settings_join_telegram", "settings_color_picker",
            "settings_focus_thickness", "settings_epg_sources_add", "settings_epg_sources_fill_playlist",
            "settings_sources_delete", "settings_sources_edit", "settings_sources_refresh_days_title",
            "common_clear", "settings_glass_effect_title", "settings_glass_preset_custom", "settings_glass_reset_balanced",
            "settings_mode", "settings_language_help_translate", "settings_size", "settings_metadata_active_source",
            "settings_metadata_clear_advanced_title", "player_subtitles_connected_as", "player_subtitles_delete_action",
            "player_subtitles_downloads", "player_subtitles_resets", "player_subtitles_sign_in", "player_subtitles_sign_out",
            "settings_sources_add", "settings_sources_cancel", "settings_sources_info", "settings_sources_resync_now_full",
            "settings_sources_resync_remove_full", "settings_sources_test_title", "setup_auto_refresh",
            "setup_auto_refresh_title", "setup_default_playlist", "profiles_add_button", "profiles_delete_title",
            "settings_catchup_timezone_device", "settings_subtitle_color", "settings_subtitle_default",
            "own_save", "own_reset", "own_cancel", "own_choose_playlist",
        ) + notRows
        val quick = read("SettingsQuick.kt")
        val quickTitle = Regex("""QuickToggle\(\s*"([a-z_]+)",\s*R\.string\.([a-z_0-9]+)""")
            .findAll(quick).associate { it.groupValues[1] to it.groupValues[2] }
        val skipped = setOf("SettingsBackupPage.kt", "SettingsLocalSyncPage.kt", "SettingsHomePage.kt", "SettingsMetadataPage.kt")
        val pages = File("src/main/java/tv/own/owntv/mobile/ui/screens/settings").listFiles { f ->
            f.name.startsWith("Settings") && f.name.contains("Page") && f.name !in skipped
        }.orEmpty()
        assertTrue("no settings pages found", pages.size > 15)
        val missing = pages.flatMap { f ->
            val text = f.readText()
            val titles = Regex("""(?<![a-z])title = stringResource\(R\.string\.([a-z_0-9]+)""").findAll(text).map { it.groupValues[1] } +
                Regex("""quickToggle\("([a-z_]+)"\)""").findAll(text).mapNotNull { quickTitle[it.groupValues[1]] }
            titles.filterNot { it.endsWith("_description") || it in notSettings || it in indexed }
                .map { "${f.name}: $it" }.toList()
        }.distinct().sorted()
        assertEquals("settings missing from the settings search index", emptyList<String>(), missing)
    }
}

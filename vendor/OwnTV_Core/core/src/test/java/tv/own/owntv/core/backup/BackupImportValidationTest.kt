package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.ProfileEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.HlsSupport
import tv.own.owntv.core.model.SourceType

class BackupImportValidationTest {
    private fun settings(input: JSONObject) = SettingsBackupValidation.validate(input,
        setOf("live_reserve_mode"), setOf("live_reserve_custom_secs", "live_preroll_secs"),
        setOf("live_hls_only"), setOf("subtitle_scale"), setOf("nav_menu_hidden"),
        mapOf("live_reserve_custom_secs" to 1L..60L, "live_preroll_secs" to 0L..30L))

    private fun source() = JSONObject().put("id", 1).put("name", "Test").put("type", "M3U")
        .put("url", "https://example.invalid/list").put("liveReserveMode", JSONObject.NULL)
        .put("liveReserveCustomSecs", -1).put("liveReserveExtraSecs", -1)

    private fun reject(block: () -> Unit) {
        assertTrue(runCatching(block).isFailure)
    }

    @Test fun `invalid selected settings fail before any caller can begin writes`() {
        val root = JSONObject().put("sources", JSONArray().put(source()))
            .put("settings", JSONObject().put("live_reserve_custom_secs", "abc"))
        var writesStarted = false
        reject {
            BackupImportValidation.validate(root, setOf(BackupManager.Section.SOURCES, BackupManager.Section.SETTINGS), ::settings)
            writesStarted = true
        }
        assertFalse(writesStarted)
    }

    @Test fun `unselected malformed settings do not block a source-only restore`() {
        val root = JSONObject().put("sources", JSONArray().put(source())).put("settings", "not an object")
        BackupImportValidation.validate(root, setOf(BackupManager.Section.SOURCES)) { error("Unselected settings") }
    }

    @Test fun `legacy omitted fields plaintext secrets and thirty second initial remain accepted`() {
        val row = source().put("password", "legacy-password").put("livePrerollSecs", 30).put("hlsSupported", 2)
        val root = JSONObject().put("version", 5).put("sources", JSONArray().put(row))
            .put("profiles", JSONArray().put(JSONObject().put("id", 1).put("name", "Main").put("avatarColor", -1)))
            .put("settings", JSONObject().put("live_preroll_secs", 30).put("live_hls_only", false))
        BackupImportValidation.validate(root, BackupManager.Section.entries.toSet(), ::settings)
    }

    @Test fun `partial source override and encrypted fields validate without changing JSON`() {
        val row = source().put("liveReserveMode", "CUSTOM").put("password", JSONObject().put("iv", "encoded").put("ct", "encoded"))
        row.remove("liveReserveCustomSecs")
        val root = JSONObject().put("sources", JSONArray().put(row))
        val before = root.toString()
        BackupImportValidation.validate(root, setOf(BackupManager.Section.SOURCES), ::settings)
        assertEquals(before, root.toString())
    }

    @Test fun `invalid selected arrays maps links and reserve limits are rejected`() {
        reject { BackupImportValidation.validate(JSONObject().put("sources", "bad"), setOf(BackupManager.Section.SOURCES), ::settings) }
        reject { BackupImportValidation.validate(JSONObject().put("links", JSONArray().put(JSONObject().put("profileId", true).put("sourceId", 1))), setOf(BackupManager.Section.SOURCES), ::settings) }
        reject { BackupImportValidation.validate(JSONObject().put("hideNewCategories", JSONObject().put("1", "true")), setOf(BackupManager.Section.CUSTOMIZE), ::settings) }
        reject { BackupImportValidation.validate(JSONObject().put("sources", JSONArray().put(source().put("liveReserveExtraSecs", 11))), setOf(BackupManager.Section.SOURCES), ::settings) }
    }

    @Test fun `integer storage rejects fractions overflow and out of range while unknown keys survive`() {
        for (value in listOf<Any>(8.5, Long.MAX_VALUE, 61, -1)) reject { settings(JSONObject().put("live_reserve_custom_secs", value)) }
        reject { settings(JSONObject().put("nav_menu_hidden", JSONArray().put(true))) }
        settings(JSONObject().put("future_setting", JSONObject().put("unknown", true)).put("live_reserve_custom_secs", 8))
    }

    @Test fun `selected manual ordering includes validation of member and sort rows`() {
        val row = JSONObject().put("kind", "member").put("t", "LIVE").put("p", true).put("src", 1)
        reject { BackupImportValidation.validate(JSONObject().put("userData", JSONArray().put(row)), setOf(BackupManager.Section.MANUAL_REORDER), ::settings) }
    }

    @Test fun `validation errors never include legacy URL map keys or secret values`() {
        val credentialUrl = "https://user:secret@example.invalid/live"
        val root = JSONObject().put("customizations", JSONObject().put("cust_1_LIVE",
            JSONObject().put("hiddenItems", JSONObject().put(credentialUrl, true)).toString()))
        val error = runCatching { BackupImportValidation.validate(root, setOf(BackupManager.Section.CUSTOMIZE), ::settings) }.exceptionOrNull()
        assertNotNull(error)
        assertFalse(error!!.message.orEmpty().contains("secret"))
        assertFalse(error.message.orEmpty().contains("example.invalid"))
    }

    @Test fun `actual profile and source encoders plus complete portable sections remain valid`() {
        val root = JSONObject().put("version", 25)
        val profile = ProfileEntity(id = 1, name = "Main", avatarColor = -1, createdAt = -1)
        val seal: (String) -> JSONObject = { JSONObject().put("iv", "encoded").put("ct", "encoded") }
        root.put("profiles", JSONArray().put(BackupManager.encodeProfile(profile, seal)))
        val sources = JSONArray()
        for (type in SourceType.entries) sources.put(BackupManager.encodeSource(SourceEntity(
            id = type.ordinal.toLong() + 1, name = type.name, type = type, url = "https://example.invalid/list",
            username = "test", password = "secret", livePrerollSecs = 30, liveReserveMode = "CUSTOM",
            liveReserveCustomSecs = 18, liveReserveExtraSecs = 4, hlsSupported = HlsSupport.UNSUPPORTED,
            createdAt = -1, lastSyncAt = null), seal))
        root.put("sources", sources)
        root.put("links", JSONArray().put(JSONObject().put("profileId", 1).put("sourceId", 1)))
        root.put("epgSources", JSONArray().put(JSONObject().put("id", -1).put("name", "Guide")
            .put("url", "https://example.invalid/guide").put("at", -1)).toString())
        root.put("epgUseLogos", JSONObject().put("-1", "true"))
        root.put("playlistAutoRefresh", JSONObject().put("1", "OFF"))
        root.put("epgAutoRefresh", JSONObject().put("-1", "MANUAL:10"))
        val custom = JSONObject().put("hiddenCats", JSONArray().put("1:category"))
            .put("hiddenItems", JSONObject().put("1:channel", "Channel"))
            .put("epgShift", JSONObject().put("1:channel", "-60"))
            .put("customCats", JSONArray().put(JSONObject().put("id", "custom:id").put("name", "Custom")))
        root.put("customizations", JSONObject().put("cust_1_LIVE", custom.toString()))
        root.put("homeConfigs", JSONObject().put("1", JSONObject().put("futureField", true)))
        root.put("hideNewCategories", JSONObject().put("1", false))
        root.put("tmdbOverrides", JSONObject().put("movie:1:test", JSONObject().put("t", "Title").put("y", 2026)).toString())
        root.put("startupModes", JSONObject().put("1", "SPECIFIC_CHANNEL"))
        root.put("startupChannels", JSONObject().put("1", JSONObject().put("sourceId", 1).put("name", "Channel").put("itemId", -1)))
        root.put("customizePins", JSONObject().put("1", seal("pin")))
        val records = JSONArray()
        for (kind in listOf("fav", "his", "prog", "order", "sort", "member")) records.put(JSONObject()
            .put("kind", kind).put("t", "LIVE").put("p", 1).put("src", 1).put("name", "Channel")
            .put("oid", 1).put("at", -1).put("pos", 0).put("dur", 0).put("ctx", "ALL")
            .put("sdesc", false).put("edesc", true))
        root.put("userData", records).put("tombstones", JSONArray())
        root.put("settings", JSONObject().put("live_reserve_mode", "CUSTOM").put("live_reserve_custom_secs", 18)
            .put("live_hls_only", true).put("proxy_pass_enc", seal("proxy")))
        root.put("compatMode", JSONObject().put("liveMpvUrls", JSONArray().put("1:LIVE:channel")))
        root.put("playbackPrefs", JSONArray().put(JSONObject().put("p", 1).put("k", "1:LIVE:channel")
            .put("v", 150).put("d", -5000).put("a", JSONObject.NULL).put("s", JSONObject.NULL)))
        root.put("playbackQuirks", JSONArray().put(JSONObject().put("k", "1:LIVE:channel").put("src", 1)
            .put("type", "LIVE").put("engine", JSONObject.NULL).put("delay", JSONObject.NULL).put("audioOnly", true)))
        root.put("openSubtitles", JSONArray().put(JSONObject().put("p", 1).put("session", seal("session"))))
        root.put("subtitles", JSONObject().put("cache", JSONArray().put(JSONObject().put("id", 1)
            .put("fileName", "test.srt").put("file", "1_test.srt").put("hearingImpaired", false)))
            .put("selections", JSONArray().put(JSONObject().put("p", 1).put("k", "live:1:channel").put("off", true)))
            .put("timings", JSONArray().put(JSONObject().put("p", 1).put("k", "live:1:channel").put("s", "local:1").put("o", -400))))
        BackupImportValidation.validate(root, BackupManager.Section.entries.toSet(), ::settings)
    }

    @Test fun `authentication checks a second selected ciphertext before caller writes`() {
        val sources = JSONArray().put(source().put("password", JSONObject().put("iv", "iv").put("ct", "first")))
            .put(source().put("id", 2).put("password", JSONObject().put("iv", "iv").put("ct", "bad-second")))
        val root = JSONObject().put("sources", sources)
        val checked = mutableListOf<String>()
        var writesStarted = false
        reject {
            BackupImportValidation.validateSecrets(root, setOf(BackupManager.Section.SOURCES)) {
                val cipher = it.getString("ct")
                checked += cipher
                require(cipher != "bad-second")
                "plain"
            }
            writesStarted = true
        }
        assertEquals(listOf("first", "bad-second"), checked)
        assertFalse(writesStarted)
    }

    @Test fun `unselected source secret and settings fields keep skip semantics`() {
        val root = JSONObject().put("version", 25).put("sources", JSONArray().put(source()
            .put("password", JSONObject().put("iv", false)).put("liveReserveExtraSecs", 999)))
        BackupImportValidation.validate(root, setOf(BackupManager.Section.SETTINGS), ::settings)
        BackupImportValidation.validateSecrets(root, setOf(BackupManager.Section.SETTINGS)) { error("Unselected source secret") }
    }

    @Test fun `unknown provider fields remain skipped rather than being reinterpreted`() {
        val row = JSONObject().put("type", "FUTURE_PROVIDER").put("liveReserveExtraSecs", 999)
        BackupImportValidation.validate(JSONObject().put("sources", JSONArray().put(row)),
            setOf(BackupManager.Section.SOURCES), ::settings)
    }
}

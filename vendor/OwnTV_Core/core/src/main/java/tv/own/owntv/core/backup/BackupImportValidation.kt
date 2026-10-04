package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject

/** Validate selected portable data before the restore marker or any database/preference write. */
internal object BackupImportValidation {
    private val v = BackupValueValidator

    fun validate(root: JSONObject, sections: Set<BackupManager.Section>, settings: (JSONObject) -> Unit) {
        v.optionalWhole(root, "version", "backup", 0, Int.MAX_VALUE.toLong())
        if (root.has("sections")) stringArray(root.get("sections"), "sections")
        if (root.has("crypto")) {
            val crypto = v.obj(root.get("crypto"), "crypto")
            listOf("scheme", "kdf", "salt").forEach { v.optionalText(crypto, it, "crypto") }
            v.optionalWhole(crypto, "iterations", "crypto", 1, Int.MAX_VALUE.toLong())
        }
        // These rows are also read to remap IDs when their section is not selected.
        val applySources = BackupManager.Section.SOURCES in sections
        v.rows(root, "profiles") { row, path ->
            v.whole(row.get("id"), "$path.id", 0)
            v.text(row.get("name"), "$path.name")
            v.whole(row.get("avatarColor"), "$path.avatarColor", Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
            if (applySources) {
                v.optionalWhole(row, "avatarId", path, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                v.optionalWhole(row, "createdAt", path)
                v.optionalFlag(row, "isKids", path)
                v.optionalText(row, "avatarFile", path)
                secret(row, "pinHash", path)
            }
        }
        v.rows(root, "sources") { row, path ->
            v.text(row.get("type"), "$path.type")
            // Unknown providers retain the transport's existing skip-and-report policy.
            if (BackupManager.parseSourceType(row.getString("type")) != null) validateSource(row, path, applySources)
        }
        if (BackupManager.Section.SOURCES in sections) {
            v.rows(root, "links") { row, path ->
                v.whole(row.get("profileId"), "$path.profileId", 0)
                v.whole(row.get("sourceId"), "$path.sourceId", 0)
            }
            v.optionalWhole(root, "defaultSourceId", "backup", 0)
            v.optionalWhole(root, "activeProfileId", "backup", -1)
            if (root.has("epgSources")) {
                val raw = v.text(root.get("epgSources"), "epgSources")!!
                val rows = runCatching { JSONArray(raw) }.getOrNull() ?: v.invalid("epgSources")
                for (i in 0 until rows.length()) {
                    val path = "epgSources[$i]"
                    val row = v.obj(rows.get(i), path)
                    v.whole(row.get("id"), "$path.id")
                    listOf("name", "url").forEach { v.text(row.get(it), "$path.$it") }
                    listOf("ua", "err").forEach { v.optionalText(row, it, path) }
                    v.optionalWhole(row, "at", path)
                }
            }
            listOf("playlistAutoRefresh", "epgAutoRefresh", "epgUseLogos").forEach { key ->
                v.map(root, key) { value, path -> v.text(value, path) }
            }
        }
        if (BackupManager.Section.CUSTOMIZE in sections) {
            v.map(root, "customizations") { value, path -> validateCustomization(v.text(value, path)!!, path) }
            v.map(root, "homeConfigs") { value, path -> v.obj(value, path) }
            v.map(root, "hideNewCategories") { value, path -> v.flag(value, path) }
            if (root.has("tmdbOverrides")) {
                val raw = v.text(root.get("tmdbOverrides"), "tmdbOverrides")!!
                if (raw.isNotBlank()) runCatching { JSONObject(raw) }.getOrNull() ?: v.invalid("tmdbOverrides")
            }
        }
        val kinds = buildSet {
            if (BackupManager.Section.FAVORITES in sections) add("fav")
            if (BackupManager.Section.HISTORY in sections) add("his")
            if (BackupManager.Section.RESUME in sections) add("prog")
            if (BackupManager.Section.MANUAL_REORDER in sections) { add("order"); add("member"); add("sort") }
        }
        if (kinds.isNotEmpty()) listOf("userData", "tombstones").forEach { key ->
            v.rows(root, key) { row, path -> if (row.optString("kind") in kinds) validateUserData(row, path) }
        }
        val oldSourcesSettings = BackupManager.Section.SOURCES in sections && root.optInt("version") < 17
        if (BackupManager.Section.SETTINGS in sections || oldSourcesSettings) {
            v.map(root, "startupModes") { value, path -> v.text(value, path) }
            v.map(root, "customizePins") { value, path -> secretValue(value, path) }
        }
        if (BackupManager.Section.SETTINGS in sections) validateSettingsSection(root, settings)
    }

    private fun validateSource(row: JSONObject, path: String, selected: Boolean) {
        v.whole(row.get("id"), "$path.id", 0)
        listOf("name", "url").forEach { v.text(row.get(it), "$path.$it") }
        if (!selected) return
        listOf("username", "userAgent", "epgUrl", "liveEnginePreference", "liveLatencyMode", "liveReserveMode",
            "catchupTimezone", "vodEnginePreference").forEach { v.optionalText(row, it, path, true) }
        listOf("password", "mac", "stalkerSerialNumber", "stalkerDeviceId", "stalkerDeviceId2", "stalkerSignature", "httpReferer")
            .forEach { secret(row, it, path) }
        listOf("syncLive", "syncMovies", "syncSeries", "importPortalEpg", "preferHls").forEach { v.optionalFlag(row, it, path) }
        v.optionalWhole(row, "livePrerollSecs", path, -1, 30)
        listOf("liveLatencyCustomSecs", "liveReserveCustomSecs").forEach { v.optionalWhole(row, it, path, -1, 60) }
        v.optionalWhole(row, "liveReserveExtraSecs", path, -1, 10)
        v.optionalWhole(row, "liveTuneTimeoutSecs", path, 0, 60, true)
        v.optionalWhole(row, "catchupOffsetMin", path, -840, 840, true)
        // Provider hints and enum codes remain tolerant of older/future sentinel values.
        v.optionalWhole(row, "maxConnections", path, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        v.optionalWhole(row, "hlsSupported", path, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        listOf("maxConnectionsProbedAt", "createdAt", "lastSyncAt").forEach { v.optionalWhole(row, it, path, nullable = true) }
    }

    private fun validateCustomization(raw: String, path: String) {
        val row = runCatching { JSONObject(raw) }.getOrNull() ?: v.invalid(path)
        listOf("hiddenCats", "catOrder").forEach { if (row.has(it)) stringArray(row.get(it), "$path.$it") }
        listOf("hiddenItems", "catNames", "itemNames", "epgMatch", "epgShift", "movedFrom", "channelVersionOrders", "channelVersionGroups").forEach { key ->
            v.map(row, key) { value, field -> v.text(value, "$path.$field") }
        }
        v.optionalFlag(row, "groupChannelVersions", path)
        v.optionalFlag(row, "prioritizeChannelVersions", path)
        row.optJSONObject("channelVersionOrders")?.let { orders ->
            orders.keys().forEach { key ->
                val list = runCatching { JSONArray(orders.getString(key)) }.getOrNull() ?: v.invalid(path)
                stringArray(list, "$path.channelVersionOrders.$key")
            }
        }
        v.rows(row, "customCats") { category, field ->
            listOf("id", "name", "icon").forEach { v.optionalText(category, it, "$path.$field") }
        }
    }

    private fun validateUserData(row: JSONObject, path: String) {
        listOf("kind", "t").forEach { v.text(row.get(it), "$path.$it") }
        v.whole(row.get("p"), "$path.p", 0)
        v.whole(row.get("src"), "$path.src", -1)
        listOf("rid", "srid", "name", "sname", "ctx").forEach { v.optionalText(row, it, path, true) }
        v.optionalWhole(row, "at", path)
        listOf("oid", "pos", "dur", "season", "ep").forEach { v.optionalWhole(row, it, path, 0) }
        listOf("sdesc", "edesc").forEach { v.optionalFlag(row, it, path) }
    }

    private fun validateSettingsSection(root: JSONObject, settings: (JSONObject) -> Unit) {
        if (root.has("settings")) {
            val row = v.obj(root.get("settings"), "settings")
            settings(row)
            listOf("proxy_pass_enc", "tmdb_key_enc", "opensub_api_key_enc").forEach { secret(row, it, "settings") }
        }
        v.map(root, "startupChannels") { value, path ->
            val row = v.obj(value, path)
            v.whole(row.get("sourceId"), "$path.sourceId", 0)
            v.text(row.get("name"), "$path.name")
            v.optionalText(row, "remoteId", path, true)
            v.optionalWhole(row, "itemId", path, -1)
        }
        if (root.has("compatMode")) {
            val row = v.obj(root.get("compatMode"), "compatMode")
            listOf("liveMpvUrls", "liveExoUrls", "vodMpvUrls", "vodExoUrls").forEach {
                if (row.has(it)) stringArray(row.get(it), "compatMode.$it")
            }
        }
        listOf("playbackPrefs", "playbackQuirks").forEach { key ->
            v.rows(root, key) { row, path ->
                v.optionalWhole(row, "p", path, 0)
                v.optionalWhole(row, "src", path, -1)
                listOf("k", "z", "a", "s", "type", "engine").forEach { v.optionalText(row, it, path, true) }
                v.optionalWhole(row, "v", path, 0, 150)
                listOf("d", "delay").forEach { v.optionalWhole(row, it, path, -5000, 5000, true) }
                listOf("updatedAt", "u").forEach { v.optionalWhole(row, it, path) }
                if (row.has("audioOnly") && !row.isNull("audioOnly")) v.flag(row.get("audioOnly"), "$path.audioOnly")
            }
        }
        v.rows(root, "openSubtitles") { row, path ->
            v.optionalWhole(row, "p", path, 0)
            secret(row, "session", path)
        }
        v.optionalText(root, "wallpaper", "backup")
        if (root.has("subtitles")) {
            val block = v.obj(root.get("subtitles"), "subtitles")
            listOf("cache", "selections", "timings", "links").forEach { key ->
                v.rows(block, key) { row, field ->
                    val path = "subtitles.$field"
                    listOf("id", "openSubFileId", "p", "c").forEach { v.optionalWhole(row, it, path, 0) }
                    listOf("lastUsedAt", "u", "a").forEach { v.optionalWhole(row, it, path) }
                    v.optionalWhole(row, "o", path, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                    listOf("source", "language", "languageName", "releaseName", "format", "fileName", "file", "k", "s", "t", "n")
                        .forEach { v.optionalText(row, it, path, true) }
                    listOf("hearingImpaired", "off").forEach { v.optionalFlag(row, it, path) }
                }
            }
        }
    }

    private fun stringArray(value: Any, path: String) {
        val rows = v.array(value, path)
        for (i in 0 until rows.length()) v.text(rows.get(i), "$path[$i]")
    }

    private fun secret(row: JSONObject, key: String, path: String) {
        if (row.has(key)) secretValue(row.get(key), "$path.$key")
    }

    private fun secretValue(value: Any, path: String) {
        if (value == JSONObject.NULL || value is String) return // legacy plaintext remains readable
        val row = v.obj(value, path)
        listOf("iv", "ct").forEach { v.text(row.get(it), "$path.$it") }
    }

    /** Authenticate every selected encrypted field, not just the first password in the file. */
    fun validateSecrets(root: JSONObject, sections: Set<BackupManager.Section>, decrypt: (JSONObject) -> String) {
        fun read(value: Any?): String? = when {
            BackupCrypto.isEncrypted(value) -> decrypt(value as JSONObject)
            value is String -> value
            else -> null
        }
        if (BackupManager.Section.SOURCES in sections) {
            v.rows(root, "profiles") { row, _ -> read(row.opt("pinHash")) }
            v.rows(root, "sources") { row, _ ->
                if (BackupManager.parseSourceType(row.optString("type")) != null) {
                    listOf("password", "mac", "stalkerSerialNumber", "stalkerDeviceId", "stalkerDeviceId2", "stalkerSignature", "httpReferer")
                        .forEach { read(row.opt(it)) }
                }
            }
        }
        val oldSourcePins = BackupManager.Section.SOURCES in sections && root.optInt("version") < 17
        if (BackupManager.Section.SETTINGS in sections || oldSourcePins) {
            v.map(root, "customizePins") { value, _ -> read(value) }
        }
        if (BackupManager.Section.SETTINGS in sections) {
            root.optJSONObject("settings")?.let { row ->
                listOf("proxy_pass_enc", "tmdb_key_enc", "opensub_api_key_enc").forEach { read(row.opt(it)) }
            }
            v.rows(root, "openSubtitles") { row, path ->
                read(row.opt("session"))?.let { plain ->
                    runCatching { JSONObject(plain) }.getOrNull() ?: v.invalid("$path.session")
                }
            }
        }
    }
}

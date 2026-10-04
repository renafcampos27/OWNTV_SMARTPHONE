package tv.own.owntv.core.backup

import org.json.JSONObject
import tv.own.owntv.core.database.entity.PlaybackQuirkEntity
import tv.own.owntv.core.database.entity.SourceEntity

/** Optional v24 backup fields. Old backups retain the target device's absent overrides. */
internal object PlaybackDatabaseBackup {
    fun writeSource(out: JSONObject, source: SourceEntity, seal: ((String) -> JSONObject)?) {
        out.put("catchupTimezone", source.catchupTimezone ?: JSONObject.NULL)
        out.put("catchupOffsetMin", source.catchupOffsetMin ?: JSONObject.NULL)
        out.put("vodEnginePreference", source.vodEnginePreference ?: JSONObject.NULL)
        out.put("liveTuneTimeoutSecs", source.liveTuneTimeoutSecs ?: JSONObject.NULL)
        // Referer may contain credentials or a signed token; plaintext exports omit it.
        if (seal != null) out.put("httpReferer", source.httpReferer?.let(seal) ?: JSONObject.NULL)
    }

    fun readSource(input: JSONObject, base: SourceEntity, unseal: (Any?) -> String?): SourceEntity = base.copy(
        catchupTimezone = if (input.has("catchupTimezone")) input.textOrNull("catchupTimezone") else base.catchupTimezone,
        catchupOffsetMin = if (input.has("catchupOffsetMin")) input.intOrNull("catchupOffsetMin")?.coerceIn(-840, 840) else base.catchupOffsetMin,
        vodEnginePreference = if (input.has("vodEnginePreference")) input.textOrNull("vodEnginePreference") else base.vodEnginePreference,
        liveTuneTimeoutSecs = if (input.has("liveTuneTimeoutSecs")) input.intOrNull("liveTuneTimeoutSecs")?.coerceIn(0, 60) else base.liveTuneTimeoutSecs,
        httpReferer = if (input.has("httpReferer")) {
            if (input.isNull("httpReferer")) null else unseal(input.opt("httpReferer")) ?: base.httpReferer
        } else base.httpReferer,
    )

    fun encode(row: PlaybackQuirkEntity): JSONObject = JSONObject().apply {
        put("k", row.contentKey); put("src", row.sourceId); put("type", row.mediaType)
        put("engine", row.enginePin ?: JSONObject.NULL)
        put("audioOnly", row.audioOnly ?: JSONObject.NULL)
        put("delay", row.audioDelayMs ?: JSONObject.NULL)
        put("updatedAt", row.updatedAt)
    }

    fun decode(input: JSONObject, sources: Map<Long, Long>, deviceSources: Set<Long>): PlaybackQuirkEntity? {
        val key = input.textOrNull("k") ?: return null
        val keySource = key.substringBefore(':').toLongOrNull()
        val oldSource = keySource ?: input.optLong("src", -1)
        val newSource = sources[oldSource] ?: oldSource
        if (newSource >= 0 && newSource !in deviceSources) return null
        val type = input.textOrNull("type")?.takeIf { it in setOf("LIVE", "MOVIE", "EPISODE") } ?: return null
        if (keySource != null && key.substringAfter(':').substringBefore(':') != type) return null
        val engine = input.textOrNull("engine")?.takeIf { it == "MPV" || it == "EXO" }
        val audioOnly = if (input.has("audioOnly") && !input.isNull("audioOnly")) input.optBoolean("audioOnly") else null
        val delay = input.intOrNull("delay")?.coerceIn(-5000, 5000)
        if (engine == null && audioOnly == null && delay == null) return null
        return PlaybackQuirkEntity(
            contentKey = if (keySource != null) "$newSource:${key.substringAfter(':')}" else key,
            sourceId = newSource, mediaType = type, enginePin = engine, audioOnly = audioOnly,
            audioDelayMs = delay, updatedAt = input.optLong("updatedAt", 0).coerceAtLeast(0),
        )
    }

    private fun JSONObject.textOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    private fun JSONObject.intOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else opt(key)?.toString()?.toIntOrNull()
}

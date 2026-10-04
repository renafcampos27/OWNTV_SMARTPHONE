package tv.own.owntv.core.settings

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.player.EnginePreference

enum class ChannelStreamFormat { HLS, TS, AUTO }

/** Null means inherit the existing playlist/global setting, including remembered audio sync. */
data class ChannelPlaybackOptions(
    val engine: EnginePreference? = null,
    val format: ChannelStreamFormat? = null,
    val reserveSecs: Int? = null,
    val extraSecs: Int? = null,
    val prerollSecs: Int? = null,
    val latencySecs: Int? = null,
    val softwareAudio: Boolean? = null,
    val audioDelayMs: Int? = null,
    val hlsDetectAccessUnits: Boolean? = null,
    val hlsAllowNonIdrKeyframes: Boolean? = null,
    val hlsPrepareFromSegments: Boolean? = null,
) {
    val isDefault: Boolean get() = this == ChannelPlaybackOptions()
    fun directTs(legacyTs: Boolean = false): Boolean = when (format) {
        ChannelStreamFormat.TS -> true
        ChannelStreamFormat.HLS, ChannelStreamFormat.AUTO -> false
        null -> legacyTs
    }
    fun hlsOnly(global: Boolean, legacyTs: Boolean = false): Boolean = when (format) {
        ChannelStreamFormat.HLS -> true
        ChannelStreamFormat.TS, ChannelStreamFormat.AUTO -> false
        null -> global && !legacyTs
    }
    fun effectiveEngine(global: EnginePreference, strictHls: Boolean, directTs: Boolean): EnginePreference =
        if (strictHls) {
            // Only an explicit channel choice may opt into mpv; inherited defaults stay unchanged.
            if (engine == EnginePreference.MPV_ONLY) EnginePreference.MPV_ONLY else EnginePreference.EXO_ONLY
        } else engine ?: if (directTs) EnginePreference.EXO_ONLY else global

    fun normalized() = copy(
        reserveSecs = reserveSecs?.coerceIn(1, 60), extraSecs = extraSecs?.coerceIn(0, 10),
        prerollSecs = prerollSecs?.coerceIn(0, 10), latencySecs = latencySecs?.coerceIn(1, 60),
        audioDelayMs = audioDelayMs?.coerceIn(-5000, 5000),
    )
}

data class ChannelPlaybackConfig(val channel: ManualTsChannel, val options: ChannelPlaybackOptions) {
    companion object {
        fun decode(raw: String?): List<ChannelPlaybackConfig> = runCatching {
            val array = JSONArray(raw ?: return emptyList())
            (0 until array.length().coerceAtMost(1000)).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val channel = ManualTsChannel.decode(JSONArray().put(o.optJSONObject("channel")).toString()).firstOrNull()
                    ?: return@mapNotNull null
                val p = o.optJSONObject("options") ?: return@mapNotNull null
                fun integer(key: String): Int? = (p.opt(key) as? Number)?.toInt()
                val options = ChannelPlaybackOptions(
                    engine = EnginePreference.entries.firstOrNull { it.name == p.optString("engine") },
                    format = ChannelStreamFormat.entries.firstOrNull { it.name == p.optString("format") },
                    reserveSecs = integer("reserveSecs"), extraSecs = integer("extraSecs"),
                    prerollSecs = integer("prerollSecs"), latencySecs = integer("latencySecs"),
                    softwareAudio = p.opt("softwareAudio") as? Boolean, audioDelayMs = integer("audioDelayMs"),
                    hlsDetectAccessUnits = p.opt("hlsDetectAccessUnits") as? Boolean,
                    hlsAllowNonIdrKeyframes = p.opt("hlsAllowNonIdrKeyframes") as? Boolean,
                    hlsPrepareFromSegments = p.opt("hlsPrepareFromSegments") as? Boolean,
                ).normalized()
                ChannelPlaybackConfig(channel, options).takeUnless { options.isDefault }
            }.distinctBy { it.channel.sourceId to (it.channel.remoteId ?: it.channel.name) }
        }.getOrDefault(emptyList())

        fun encode(rows: List<ChannelPlaybackConfig>): String = JSONArray().apply {
            rows.filterNot { it.options.isDefault }.forEach { row ->
                val o = row.options.normalized()
                val ref = JSONArray(ManualTsChannel.encode(listOf(row.channel))).getJSONObject(0)
                put(JSONObject().put("channel", ref).put("options", JSONObject()
                    .putOpt("engine", o.engine?.name).putOpt("format", o.format?.name)
                    .putOpt("reserveSecs", o.reserveSecs).putOpt("extraSecs", o.extraSecs)
                    .putOpt("prerollSecs", o.prerollSecs).putOpt("latencySecs", o.latencySecs)
                    .putOpt("softwareAudio", o.softwareAudio).putOpt("audioDelayMs", o.audioDelayMs)
                    .putOpt("hlsDetectAccessUnits", o.hlsDetectAccessUnits)
                    .putOpt("hlsAllowNonIdrKeyframes", o.hlsAllowNonIdrKeyframes)
                    .putOpt("hlsPrepareFromSegments", o.hlsPrepareFromSegments)))
            }
        }.toString()

        fun includeLegacy(rows: List<ChannelPlaybackConfig>, legacy: List<ManualTsChannel>): List<ChannelPlaybackConfig> = rows.map { row ->
            if (row.options.format == null && legacy.any { it.sourceId == row.channel.sourceId && (it.remoteId ?: it.name) == (row.channel.remoteId ?: row.channel.name) })
                row.copy(options = row.options.copy(format = ChannelStreamFormat.TS, engine = row.options.engine ?: EnginePreference.EXO_ONLY))
            else row
        } +
            legacy.filterNot { ref -> rows.any { it.channel.sourceId == ref.sourceId && (it.channel.remoteId ?: it.channel.name) == (ref.remoteId ?: ref.name) } }
                .map { ChannelPlaybackConfig(it, ChannelPlaybackOptions(engine = EnginePreference.EXO_ONLY, format = ChannelStreamFormat.TS)) }

        fun remap(rows: List<ChannelPlaybackConfig>, sources: Map<Long, Long>): List<ChannelPlaybackConfig> =
            rows.mapNotNull { row -> sources[row.channel.sourceId]?.let { row.copy(channel = row.channel.copy(sourceId = it)) } }
    }
}

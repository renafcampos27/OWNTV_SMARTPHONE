package tv.own.owntv.core.settings

import org.json.JSONArray
import org.json.JSONObject
import tv.own.owntv.core.database.entity.ChannelEntity

/** Explicit user choices only. No URL or session token is persisted. */
data class ManualTsChannel(val sourceId: Long, val remoteId: String?, val name: String) {
    fun matches(channel: ChannelEntity): Boolean = sourceId == channel.sourceId &&
        if (remoteId != null) remoteId == channel.remoteId else channel.remoteId.isNullOrBlank() && name == channel.name

    companion object {
        fun of(channel: ChannelEntity) = ManualTsChannel(channel.sourceId, channel.remoteId?.takeIf { it.isNotBlank() }, channel.name)
        fun decode(raw: String?): List<ManualTsChannel> = runCatching {
            val array = JSONArray(raw ?: return emptyList())
            (0 until array.length().coerceAtMost(1000)).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val source = o.optLong("sourceId", -1L)
                val name = o.optString("name")
                if (source <= 0 || name.isBlank()) null else ManualTsChannel(source, o.optString("remoteId").takeIf { it.isNotBlank() }, name)
            }.distinctBy { it.sourceId to (it.remoteId ?: it.name) }
        }.getOrDefault(emptyList())

        fun encode(rows: List<ManualTsChannel>): String = JSONArray().apply {
            rows.forEach { row -> put(JSONObject().put("sourceId", row.sourceId).putOpt("remoteId", row.remoteId).put("name", row.name)) }
        }.toString()

        fun remap(rows: List<ManualTsChannel>, sources: Map<Long, Long>): List<ManualTsChannel> =
            rows.mapNotNull { row -> sources[row.sourceId]?.let { row.copy(sourceId = it) } }
    }
}

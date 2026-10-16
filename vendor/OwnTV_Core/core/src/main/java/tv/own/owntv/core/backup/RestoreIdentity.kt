package tv.own.owntv.core.backup

import org.json.JSONObject
import org.json.JSONArray
import tv.own.owntv.core.database.entity.SourceEntity

/** Device counters are not identities. Only resolved foreign keys may enter a restore. */
internal object RestoreIdentity {
    /** Read-only matching metadata never discloses account URLs, usernames or a portal MAC. */
    fun sourceFingerprint(source: SourceEntity): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(sourceMatchKey(source.type.name, source.url, source.username, source.mac).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(java.util.Locale.ROOT, it) }

    fun sources(input: JSONArray, deviceSources: List<SourceEntity>): Map<Long, Long> {
        val candidates = deviceSources.groupBy(::sourceFingerprint)
        val claimed = hashSetOf<Long>()
        return buildMap {
            for (index in 0 until input.length()) {
                val row = input.optJSONObject(index) ?: continue
                val oldId = row.optLong("id", -1).takeIf { it > 0 } ?: continue
                val target = candidates[row.optString("identity")]?.singleOrNull() ?: continue
                if (oldId !in this && claimed.add(target.id)) put(oldId, target.id)
            }
        }
    }

    fun keys(input: JSONObject, mapping: Map<Long, Long>): JSONObject = JSONObject().apply {
        input.keys().forEach { key ->
            val target = key.toLongOrNull()?.let(mapping::get) ?: return@forEach
            put(target.toString(), input.get(key))
        }
    }

    /** Legacy URL and local namespaces carry no foreign source identity. */
    fun playerKey(key: String, sources: Map<Long, Long>): String? {
        val source = key.substringBefore(':').toLongOrNull() ?: return key
        if (source < 0) return key
        val mapped = sources[source] ?: return null
        return "$mapped:${key.substringAfter(':')}"
    }
}

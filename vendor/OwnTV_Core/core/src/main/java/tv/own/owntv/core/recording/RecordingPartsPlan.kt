package tv.own.owntv.core.recording

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Parts contain whole HLS segments; no arbitrary byte cuts or progressive MP4 splitting. */
internal object RecordingPartsPlan {
    const val MAX_PART_BYTES = 64L * 1024 * 1024
    const val TARGET_PART_MS = 30_000L
    const val INDEX_NAME = "index.owntv.json"
    data class Part(val name: String, val bytes: Long, val durationMs: Long, val discontinuity: Boolean)
    data class Index(val identity: String, val container: String, val initBytes: Long, val parts: List<Part>) {
        val bytes: Long get() = initBytes + parts.sumOf { it.bytes }
        fun playlist(): String = buildString {
            val target = (parts.maxOf { it.durationMs } + 999) / 1000
            append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-PLAYLIST-TYPE:VOD\n#EXT-X-TARGETDURATION:$target\n#EXT-X-MEDIA-SEQUENCE:0\n")
            if (container == "fmp4") append("#EXT-X-MAP:URI=\"-1.mp4\"\n")
            parts.forEachIndexed { index, part ->
                if (part.discontinuity) append("#EXT-X-DISCONTINUITY\n")
                append("#EXTINF:${String.format(Locale.ROOT, "%.3f", part.durationMs / 1000.0)},\n$index.${if (container == "fmp4") "m4s" else "ts"}\n")
            }
            append("#EXT-X-ENDLIST\n")
        }
        fun encode(): String = JSONObject().put("v", 1).put("identity", identity).put("container", container)
            .put("initBytes", initBytes).put("parts", JSONArray().apply { parts.forEach {
                put(JSONObject().put("name", it.name).put("bytes", it.bytes).put("ms", it.durationMs).put("dc", it.discontinuity))
            } }).toString()
    }

    fun groups(entries: List<HlsCaptureSession.Entry>, maxBytes: Long = MAX_PART_BYTES): List<List<HlsCaptureSession.Entry>> {
        require(maxBytes in 1..MAX_PART_BYTES)
        val groups = mutableListOf<MutableList<HlsCaptureSession.Entry>>()
        var bytes = 0L
        var duration = 0L
        entries.forEach { entry ->
            require(entry.bytes in 1..maxBytes && entry.durationMs in 1..86_400_000)
            val current = groups.lastOrNull()
            val previous = current?.lastOrNull()
            if (current == null || bytes + entry.bytes > maxBytes || duration + entry.durationMs > TARGET_PART_MS ||
                previous?.let { entry.sequence != it.sequence + 1 || entry.discontinuity != it.discontinuity } == true) {
                groups += mutableListOf(entry)
                bytes = 0; duration = 0
            } else current += entry
            bytes += entry.bytes; duration += entry.durationMs
        }
        return groups
    }

    fun decode(raw: String): Index {
        require(raw.length <= 4 * 1024 * 1024)
        val json = JSONObject(raw)
        require(json.getInt("v") == 1)
        val container = json.getString("container")
        require(container in listOf("ts", "fmp4"))
        val init = json.getLong("initBytes")
        require(if (container == "fmp4") init in 1..MAX_PART_BYTES else init == 0L)
        val rows = json.getJSONArray("parts")
        require(rows.length() in 1..100_000)
        val parts = (0 until rows.length()).map { index ->
            val item = rows.getJSONObject(index)
            val expected = "part-${index.toString().padStart(6, '0')}.${if (container == "fmp4") "m4s" else "ts"}"
            require(item.getString("name") == expected)
            val bytes = item.getLong("bytes"); val duration = item.getLong("ms")
            require(bytes in 1..MAX_PART_BYTES && duration in 1..86_400_000)
            Part(expected, bytes, duration, item.getBoolean("dc"))
        }
        val identity = json.getString("identity")
        require(identity.matches(Regex("[a-f0-9]{64}")))
        return Index(identity, container, init, parts)
    }
}

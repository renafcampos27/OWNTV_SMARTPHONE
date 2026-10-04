package tv.own.owntv.core.recording

/** Recording metadata only; this is distinct from the channel-list M3U parser. */
data class HlsMediaPlaylist(
    val segments: List<Segment>,
    val mediaSequence: Long,
    val targetDurationSecs: Double,
    val endList: Boolean,
    val encryptionMethod: String?,
    val variants: List<Variant> = emptyList(),
    val audioRenditions: List<AudioRendition> = emptyList(),
    val invalid: Boolean = false,
) {
    data class ByteRange(val length: Long, val offset: Long) {
        val header: String get() = "bytes=$offset-${offset + length - 1}"
    }
    data class Init(val uri: String, val range: ByteRange? = null)
    data class Segment(
        val uri: String, val durationSecs: Double, val sequence: Long,
        val range: ByteRange? = null, val init: Init? = null,
        val discontinuity: Long = 0, val gap: Boolean = false,
    )
    data class Variant(val uri: String, val bandwidth: Long, val audioGroup: String?, val codecs: String?,
                       val width: Int? = null, val height: Int? = null)
    data class AudioRendition(val group: String, val uri: String?, val default: Boolean)
    val isEncrypted: Boolean get() = encryptionMethod != null && !encryptionMethod.equals("NONE", true)
    val isMaster: Boolean get() = variants.isNotEmpty()
    val pollIntervalMs: Long get() = ((targetDurationSecs * 1000).toLong() / 2).coerceIn(1_000, 10_000)

    companion object {
        fun looksLikePlaylist(contentType: String?, body: String): Boolean =
            body.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith("#EXTM3U") ||
                contentType?.lowercase()?.let { it.contains("mpegurl") || it.contains("m3u") } == true

        fun parse(text: String): HlsMediaPlaylist {
            var sequence = 0L
            var target = 6.0
            var ended = false
            var encryption: String? = null
            var duration: Double? = null
            var pendingVariant: String? = null
            var pendingRange: String? = null
            var lastRange: ByteRange? = null
            var lastRangeUri: String? = null
            var init: Init? = null
            var discontinuity = 0L
            var gap = false
            var invalid = !text.trimStart('\uFEFF', ' ', '\r', '\n', '\t').startsWith("#EXTM3U")
            val segments = mutableListOf<Segment>()
            val variants = mutableListOf<Variant>()
            val audios = mutableListOf<AudioRendition>()
            text.lineSequence().forEach { raw ->
                val line = raw.trim().trimStart('\uFEFF')
                when {
                    line.isEmpty() -> Unit
                    line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> sequence = line.substringAfter(':').toLongOrNull()?.takeIf { it >= 0 } ?: run { invalid = true; 0 }
                    line.startsWith("#EXT-X-DISCONTINUITY-SEQUENCE:") -> discontinuity = line.substringAfter(':').toLongOrNull()?.takeIf { it >= 0 } ?: run { invalid = true; 0 }
                    line == "#EXT-X-DISCONTINUITY" -> discontinuity++
                    line == "#EXT-X-GAP" -> gap = true
                    line.startsWith("#EXT-X-TARGETDURATION:") -> target = line.substringAfter(':').toDoubleOrNull()?.takeIf { it.isFinite() } ?: 6.0
                    line == "#EXT-X-ENDLIST" -> ended = true
                    line.startsWith("#EXT-X-KEY:") || line.startsWith("#EXT-X-SESSION-KEY:") -> {
                        val method = attribute(line.substringAfter(':'), "METHOD")
                        // Any encrypted segment makes the capture unsupported, even if later cleared.
                        if (method != null && (!method.equals("NONE", true) || encryption == null)) encryption = method
                    }
                    line.startsWith("#EXT-X-STREAM-INF:") -> pendingVariant = line.substringAfter(':')
                    line.startsWith("#EXT-X-MEDIA:") -> {
                        val a = line.substringAfter(':')
                        if (attribute(a, "TYPE") == "AUDIO") {
                            val group = attribute(a, "GROUP-ID")
                            if (group != null) audios += AudioRendition(group, attribute(a, "URI"), attribute(a, "DEFAULT") == "YES")
                        }
                    }
                    line.startsWith("#EXT-X-MAP:") -> {
                        val a = line.substringAfter(':')
                        val uri = attribute(a, "URI")
                        val rangeText = attribute(a, "BYTERANGE")
                        val range = rangeText?.let { byteRange(it, null) }
                        if (uri == null || (rangeText != null && range == null)) invalid = true
                        init = uri?.let { Init(it, range) }
                    }
                    line.startsWith("#EXT-X-BYTERANGE:") -> pendingRange = line.substringAfter(':')
                    line.startsWith("#EXTINF:") -> {
                        duration = line.substringAfter(':').substringBefore(',').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                        if (duration == null) invalid = true
                    }
                    line.startsWith("#") -> Unit
                    pendingVariant != null -> {
                        val a = pendingVariant
                        val resolution = attribute(a, "RESOLUTION")?.split('x')
                        val width = resolution?.takeIf { it.size == 2 }?.get(0)?.toIntOrNull()?.takeIf { it > 0 }
                        val height = resolution?.takeIf { it.size == 2 }?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
                        variants += Variant(line, attribute(a, "AVERAGE-BANDWIDTH")?.toLongOrNull()
                            ?: attribute(a, "BANDWIDTH")?.toLongOrNull() ?: 0L,
                            attribute(a, "AUDIO"), attribute(a, "CODECS"), width, height)
                        pendingVariant = null
                    }
                    duration != null -> {
                        val range = pendingRange?.let { byteRange(it, if (lastRangeUri == line) lastRange else null) }
                        if (pendingRange != null && range == null) invalid = true
                        segments += Segment(line, duration, sequence + segments.size, range, init, discontinuity, gap)
                        lastRange = range
                        lastRangeUri = line
                        duration = null
                        pendingRange = null
                        gap = false
                    }
                    else -> invalid = true // Never record a bare master URI as binary media.
                }
            }
            if (duration != null || pendingVariant != null || pendingRange != null) invalid = true
            return HlsMediaPlaylist(segments, sequence, target, ended, encryption, variants, audios, invalid)
        }

        internal fun byteRange(value: String, previous: ByteRange?): ByteRange? {
            val length = value.substringBefore('@').toLongOrNull()?.takeIf { it > 0 } ?: return null
            val offset = if ('@' in value) value.substringAfter('@').toLongOrNull()
                else previous?.let { it.offset + it.length }
            if (offset == null || offset < 0 || offset > Long.MAX_VALUE - length) return null
            return ByteRange(length, offset)
        }

        internal fun attribute(attributes: String, name: String): String? {
            val parts = mutableListOf<String>()
            val current = StringBuilder()
            var quoted = false
            attributes.forEach { c ->
                when {
                    c == '"' -> { quoted = !quoted; current.append(c) }
                    c == ',' && !quoted -> { parts += current.toString(); current.clear() }
                    else -> current.append(c)
                }
            }
            parts += current.toString()
            return parts.firstNotNullOfOrNull { part ->
                val key = part.substringBefore('=').trim()
                if (key.equals(name, true)) part.substringAfter('=', "").trim().trim('"') else null
            }
        }
    }
}

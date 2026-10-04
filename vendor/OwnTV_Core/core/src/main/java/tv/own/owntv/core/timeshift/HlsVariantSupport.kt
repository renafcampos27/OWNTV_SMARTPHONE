package tv.own.owntv.core.timeshift

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import tv.own.owntv.core.recording.HlsMediaPlaylist

/** Reject only an explicitly advertised codec family or size that no available decoder supports. */
internal object HlsVariantSupport {
    private val decoders: List<MediaCodecInfo>? by lazy {
        runCatching { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filterNot { it.isEncoder } }.getOrNull()
    }
    fun deviceSupports(variant: HlsMediaPlaylist.Variant): Boolean =
        runCatching { supports(variant, ::deviceCapability) }.getOrDefault(true)

    /** null capability means unavailable evidence, so incomplete metadata remains eligible. */
    fun supports(variant: HlsMediaPlaylist.Variant,
                 capability: (mime: String, width: Int?, height: Int?) -> Boolean?): Boolean =
        variant.codecs.orEmpty().split(',').mapNotNull { mime(it.trim()) }.all { type ->
            val video = type.startsWith("video/")
            capability(type, variant.width.takeIf { video }, variant.height.takeIf { video }) != false
        }

    private fun deviceCapability(mime: String, width: Int?, height: Int?): Boolean? {
        val known = decoders ?: return null
        val candidates = known.filter { codec -> codec.supportedTypes.any { it.equals(mime, ignoreCase = true) } }
        if (candidates.isEmpty()) return false
        if (width == null || height == null) return true
        val support = candidates.map { codec ->
            runCatching { codec.getCapabilitiesForType(mime).videoCapabilities?.isSizeSupported(width, height) }.getOrNull()
        }
        return if (support.any { it == true }) true else if (support.any { it == null }) null else false
    }

    private fun mime(codec: String): String? = when (codec.substringBefore('.').lowercase(java.util.Locale.ROOT)) {
        "avc1", "avc3" -> "video/avc"
        "hvc1", "hev1" -> "video/hevc"
        // Dolby Vision may use a base-layer decoder fallback; a MIME-only refusal is inconclusive.
        "dvh1", "dvhe", "dva1", "dvav" -> null
        "av01" -> "video/av01"
        "vp09", "vp9" -> "video/x-vnd.on2.vp9"
        "vp08", "vp8" -> "video/x-vnd.on2.vp8"
        "mp4a" -> if (codec.startsWith("mp4a.40.", ignoreCase = true)) "audio/mp4a-latm" else null
        "ac-3", "ac3" -> "audio/ac3"
        "ec-3", "eac3" -> "audio/eac3"
        "opus" -> "audio/opus"
        "vorbis" -> "audio/vorbis"
        else -> null
    }
}

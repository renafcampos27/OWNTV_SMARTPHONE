package tv.own.owntv.core.recording

import java.net.URI

/** A variant is fixed for a recording; signed query parameters are not its identity. */
internal object HlsRecordingPlan {
    fun selectVariant(playlist: HlsMediaPlaylist, fixedIdentity: String? = null,
                      supported: (HlsMediaPlaylist.Variant) -> Boolean = { true }): HlsMediaPlaylist.Variant? {
        val candidates = playlist.variants.filter { variant ->
            if (!supported(variant)) return@filter false
            val group = variant.audioGroup ?: return@filter true
            val renditions = playlist.audioRenditions.filter { it.group == group }
            renditions.isNotEmpty() && renditions.none { it.uri != null }
        }
        val selected = if (fixedIdentity == null) candidates.maxByOrNull { it.bandwidth }
            else candidates.firstOrNull { identity(it) == fixedIdentity }
        // Do not silently save a video-only variant when its audio lives in another playlist.
        return selected
    }

    fun identity(variant: HlsMediaPlaylist.Variant): String {
        val descriptor = listOf(pathIdentity(variant.uri), variant.bandwidth,
            variant.codecs.orEmpty(), variant.audioGroup.orEmpty()).joinToString("|")
        return java.security.MessageDigest.getInstance("SHA-256").digest(descriptor.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun pathIdentity(uri: String): String = runCatching { URI(uri).path.orEmpty() }.getOrDefault(uri.substringBefore('?'))

    fun durationMs(segment: HlsMediaPlaylist.Segment): Long =
        (segment.durationSecs * 1_000).toLong().coerceIn(1L, 86_400_000L)
}

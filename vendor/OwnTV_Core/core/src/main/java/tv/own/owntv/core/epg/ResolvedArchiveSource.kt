package tv.own.owntv.core.epg

import tv.own.owntv.core.player.ManifestType
import java.net.URI

/** Request evidence for the selected archive, never a guess based on the channel's live format. */
data class ResolvedArchiveSource(
    val url: String,
    val alternatives: List<String> = emptyList(),
    val manifestTypes: Map<String, String> = emptyMap(),
) {
    companion object {
        fun of(url: String): ResolvedArchiveSource {
            val path = runCatching { URI(url).path.orEmpty().lowercase(java.util.Locale.ROOT) }.getOrDefault("")
            val manifest = when { path.endsWith(".m3u8") -> ManifestType.HLS; path.endsWith(".mpd") -> ManifestType.MPD; else -> null }
            return ResolvedArchiveSource(url, manifestTypes = manifest?.let { mapOf(url to it.key) }.orEmpty())
        }
    }
}

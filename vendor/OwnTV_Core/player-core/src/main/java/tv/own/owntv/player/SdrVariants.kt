package tv.own.owntv.player

import android.net.Uri
import androidx.media3.common.util.UriUtil
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistParserFactory
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParserFactory
import androidx.media3.exoplayer.upstream.ParsingLoadable
import java.io.ByteArrayOutputStream
import java.io.FilterInputStream
import java.io.InputStream

/** Explicit mixed manifests only. Unknown/HDR-only media keep their original playback path. */
internal object SdrVariants {
    private val range = Regex("(?:^|,)VIDEO-RANGE=(SDR|PQ|HLG)(?:,|$)")
    fun sdrUris(text: String): Set<String>? {
        var pending: String? = null
        var hdr = false
        val sdr = linkedSetOf<String>()
        text.lineSequence().map(String::trim).forEach { line ->
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                pending = range.find(line.substringAfter(':'))?.groupValues?.get(1)
            } else if (line.isNotBlank() && !line.startsWith('#')) {
                when (pending) { "SDR" -> sdr.add(line); "PQ", "HLG" -> hdr = true }
                pending = null
            }
        }
        return sdr.takeIf { hdr && it.isNotEmpty() }
    }

    class ParserFactory : HlsPlaylistParserFactory {
        private val delegate = DefaultHlsPlaylistParserFactory()
        override fun createPlaylistParser(): ParsingLoadable.Parser<HlsPlaylist> {
            val parser = delegate.createPlaylistParser()
            return ParsingLoadable.Parser { uri, input ->
                val capture = BoundedManifestCapture(input)
                val parsed = parser.parse(uri, capture)
                if (parsed is HlsMultivariantPlaylist) filter(parsed, uri, capture.text()) else parsed
            }
        }
        // Segment playlists are refreshed repeatedly: no duplicate capture/parse/allocation here.
        override fun createPlaylistParser(master: HlsMultivariantPlaylist, previous: HlsMediaPlaylist?): ParsingLoadable.Parser<HlsPlaylist> =
            delegate.createPlaylistParser(master, previous)

        private fun filter(master: HlsMultivariantPlaylist, uri: Uri, text: String?): HlsMultivariantPlaylist {
            val allowed = text?.let(::sdrUris)?.map { UriUtil.resolveToUri(uri.toString(), it) }?.toSet() ?: return master
            val variants = master.variants.filter { it.url in allowed }
            if (variants.isEmpty() || variants.size == master.variants.size) return master
            return HlsMultivariantPlaylist(master.baseUri, master.tags, variants, master.videos,
                master.audios, master.subtitles, master.closedCaptions, master.muxedAudioFormat,
                master.muxedCaptionFormats, master.hasIndependentSegments, master.variableDefinitions,
                master.sessionKeyDrmInitData, master.contentSteeringInfo)
        }
    }
}

/** Observe the parser's normal reads; never issue another request or read the input a second time. */
internal class BoundedManifestCapture(input: InputStream, private val limit: Int = 512 * 1024) : FilterInputStream(input) {
    private val bytes = ByteArrayOutputStream(minOf(limit, 8192))
    private var complete = true
    private fun capture(data: ByteArray, offset: Int, count: Int) {
        if (!complete || count <= 0) return
        if (count > limit - bytes.size()) { complete = false; bytes.reset() }
        else bytes.write(data, offset, count)
    }
    override fun read(): Int = `in`.read().also { if (it >= 0) capture(byteArrayOf(it.toByte()), 0, 1) }
    override fun read(data: ByteArray, offset: Int, count: Int): Int =
        `in`.read(data, offset, count).also { capture(data, offset, it) }
    override fun skip(n: Long): Long { complete = false; bytes.reset(); return `in`.skip(n) }
    fun text(): String? = if (complete) bytes.toString(Charsets.UTF_8.name()) else null
}

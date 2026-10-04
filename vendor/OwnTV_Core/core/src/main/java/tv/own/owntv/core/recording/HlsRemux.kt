package tv.own.owntv.core.recording

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/** A single, stable fMP4 initialization followed by fragments; samples copied without re-encoding. */
internal object HlsRemux {
    fun mux(input: File, output: File, hasSpace: () -> Boolean = { true }): Boolean {
        val sources = mutableListOf<Pair<MediaExtractor, Int>>()
        var muxer: MediaMuxer? = null
        var started = false
        var completed = false
        try {
            val probe = MediaExtractor()
            val formats = try {
                probe.setDataSource(input.absolutePath)
                (0 until probe.trackCount).map { probe.getTrackFormat(it) }
            } finally { probe.release() }
            val mediaTracks = formats.withIndex().filter {
                val mime = it.value.getString(MediaFormat.KEY_MIME).orEmpty()
                mime.startsWith("audio/") || mime.startsWith("video/")
            }
            if (mediaTracks.isEmpty()) return false
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var maxSample = 1024 * 1024
            for ((index, format) in mediaTracks) {
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    maxSample = maxOf(maxSample, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
                }
                require(maxSample <= 16 * 1024 * 1024) { "HLS sample exceeds mux limit" }
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(input.absolutePath)
                    extractor.selectTrack(index)
                    require(extractor.sampleTrackIndex >= 0) { "HLS track has no samples" }
                    sources += extractor to muxer.addTrack(format)
                } catch (error: Exception) { extractor.release(); throw error }
            }
            muxer.start()
            started = true
            val buffer = ByteBuffer.allocate(maxSample)
            val info = MediaCodec.BufferInfo()
            val lastPts = LongArray(sources.size) { -1L }
            while (true) {
                val selected = sources.indices.filter { sources[it].first.sampleTrackIndex >= 0 }
                    .minByOrNull { sources[it].first.sampleTime } ?: break
                val (extractor, track) = sources[selected]
                if (!hasSpace()) return false
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                require(size > 0 && size <= maxSample) { "Invalid HLS sample" }
                val pts = extractor.sampleTime.coerceAtLeast(0L)
                require(pts >= lastPts[selected]) { "HLS timestamp discontinuity" }
                lastPts[selected] = pts
                info.set(0, size, pts, if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track, buffer, info)
                extractor.advance()
            }
            muxer.stop() // Completion is true only after the container index is finalized successfully.
            started = false
            completed = output.length() > 0
            return completed
        } catch (_: Exception) {
            return false
        } finally {
            sources.forEach { runCatching { it.first.release() } }
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            if (!completed) output.delete()
        }
    }
}

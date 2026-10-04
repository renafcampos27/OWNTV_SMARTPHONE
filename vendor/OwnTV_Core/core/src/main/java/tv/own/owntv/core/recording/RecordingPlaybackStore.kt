package tv.own.owntv.core.recording

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.concurrent.Executors
import tv.own.owntv.core.timeshift.LocalSegmentStore
import tv.own.owntv.core.timeshift.LocalTimeshiftServer

/** Read-only publication of committed capture files. A viewer never starts an upstream producer. */
internal class RecordingPlaybackStore(
    private val directory: File,
    private val onIdle: (RecordingPlaybackStore) -> Unit = {},
) {
    private val entries = mutableListOf<HlsCaptureSession.Entry>()
    private var container: String? = null
    private var hasInit = false
    private var targetDuration = 0L
    private var ended = false
    private var retired = false
    private var viewers = 0
    private var readers = 0
    private var cleanupQueued = false
    @Synchronized fun publish(entry: HlsCaptureSession.Entry, format: String?, init: Boolean) {
        check(!retired)
        if (ended) return
        val seconds = (entry.durationMs + 999) / 1000
        if (targetDuration > 0 && seconds > targetDuration) { finish(); return }
        if (entries.lastOrNull()?.sequence?.let { entry.sequence <= it } == true) return
        container = format
        hasInit = init
        entries += entry
    }
    @Synchronized fun configureTarget(seconds: Double) {
        if (targetDuration == 0L) targetDuration = maxOf(kotlin.math.ceil(seconds).toLong(),
            entries.maxOfOrNull { (it.durationMs + 999) / 1000 } ?: 1L)
    }
    @Synchronized fun hasViewers() = viewers > 0 || readers > 0
    @Synchronized fun canOpen() = !retired && !ended && entries.isNotEmpty() &&
        (container == "ts" || container == "fmp4" && hasInit)

    @Synchronized fun acquire(): Closeable? {
        if (!canOpen()) return null
        viewers++
        var closed = false
        return Closeable { synchronized(this) {
            if (!closed) { closed = true; viewers--; cleanupIfIdle() }
        } }
    }
    @Synchronized fun finish() { ended = true; cleanupIfIdle() }
    @Synchronized fun discard() { retired = true; ended = true; cleanupIfIdle(async = false) }
    private fun cleanupIfIdle(async: Boolean = true) {
        if (!ended || viewers != 0 || readers != 0) return
        onIdle(this)
        if (retired && !cleanupQueued) {
            cleanupQueued = true
            val remove = Runnable {
                directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                directory.delete()
            }
            if (async) cleanup.execute(remove) else remove.run()
        }
    }
    @Synchronized fun playlist(): String {
        check(entries.isNotEmpty())
        val target = targetDuration.takeIf { it > 0 } ?: ((entries.maxOf { it.durationMs } + 999) / 1000).coerceAtLeast(1)
        return buildString {
            append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-PLAYLIST-TYPE:EVENT\n")
            append("#EXT-X-TARGETDURATION:$target\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES\n")
            if (container == "fmp4") append("#EXT-X-MAP:URI=\"-1.mp4\"\n")
            entries.forEachIndexed { index, entry ->
                val previous = entries.getOrNull(index - 1)
                if (previous != null && (entry.sequence != previous.sequence + 1 || entry.discontinuity != previous.discontinuity))
                    append("#EXT-X-DISCONTINUITY\n")
                append("#EXTINF:${String.format(Locale.ROOT, "%.3f", entry.durationMs / 1000.0)},\n")
                append("$index.${if (container == "fmp4") "m4s" else "ts"}\n")
            }
            if (ended) append("#EXT-X-ENDLIST\n")
        }
    }
    @Synchronized fun open(index: Long): LocalSegmentStore.Reader? {
        if (viewers == 0) return null
        val entry = index.takeIf { it in 0 until entries.size.toLong() }?.let { entries[it.toInt()] }
        val file = if (index == -1L && hasInit) File(directory, "init.bin") else entry?.let { File(directory, it.name) } ?: return null
        if (!file.isFile) return null
        val input = runCatching { FileInputStream(file) }.getOrNull() ?: return null
        readers++
        return LocalSegmentStore.Reader(input, file.length(), if (container == "fmp4") "video/mp4" else "video/mp2t") {
            synchronized(this) { readers--; cleanupIfIdle() }
        }
    }
    fun playback(): RecordingPlayback? {
        val lease = acquire() ?: return null
        return try {
            val server = LocalTimeshiftServer(::playlist, ::open)
            RecordingPlayback(server.url) { server.close(); lease.close() }
        } catch (error: Exception) { lease.close(); throw error }
    }
    companion object {
        private val cleanup = Executors.newSingleThreadExecutor { task -> Thread(task, "recording-read-cleanup").apply { isDaemon = true } }
    }
}

class RecordingPlayback internal constructor(val url: String, private val release: () -> Unit) : Closeable {
    private var closed = false
    @Synchronized override fun close() { if (!closed) { closed = true; release() } }
}

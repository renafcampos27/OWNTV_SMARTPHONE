package tv.own.owntv.core.recording

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.Executors
import tv.own.owntv.core.timeshift.LocalSegmentStore
import tv.own.owntv.core.timeshift.LocalTimeshiftServer

/** Read-only publication of committed capture files. A viewer never starts an upstream producer. */
internal class RecordingPlaybackStore(
    private val directory: File,
    private val onIdle: (RecordingPlaybackStore) -> Unit = {},
) {
    private val entries = mutableListOf<HlsCaptureSession.Entry>()
    private val fragments = mutableListOf<String>()
    private var maxSegmentSeconds = 1L
    private var revision = 0L
    private data class CachedPlaylist(val revision: Long, val text: String)
    private var cachedPlaylist: CachedPlaylist? = null
    private data class PlaylistSnapshot(
        val revision: Long,
        val target: Long,
        val fragmentedMp4: Boolean,
        val hasInit: Boolean,
        val ended: Boolean,
        val fragments: List<String>,
    )

    private fun changed() {
        revision++
        cachedPlaylist = null
    }
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
        val previous = entries.lastOrNull()
        val discontinuity = previous != null &&
            (entry.sequence != previous.sequence + 1 || entry.discontinuity != previous.discontinuity)
        // Milliseconds already have exact three-digit precision; format once per committed segment.
        fragments += buildString {
            if (discontinuity) append("#EXT-X-DISCONTINUITY\n")
            append("#EXTINF:${entry.durationMs / 1000}.${(entry.durationMs % 1000).toString().padStart(3, '0')},\n")
        }
        container = format
        hasInit = init
        entries += entry
        maxSegmentSeconds = maxOf(maxSegmentSeconds, seconds)
        changed()
    }
    @Synchronized fun configureTarget(seconds: Double) {
        if (targetDuration == 0L) {
            targetDuration = maxOf(kotlin.math.ceil(seconds).toLong(), maxSegmentSeconds)
            changed()
        }
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
    @Synchronized fun finish() {
        if (!ended) { ended = true; changed() }
        cleanupIfIdle()
    }
    @Synchronized fun discard() {
        retired = true
        if (!ended) { ended = true; changed() }
        cleanupIfIdle(async = false)
    }
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
    fun playlist(): String {
        val snapshot = synchronized(this) {
            check(entries.isNotEmpty())
            cachedPlaylist?.takeIf { it.revision == revision }?.let { return it.text }
            PlaylistSnapshot(revision, targetDuration.takeIf { it > 0 } ?: maxSegmentSeconds,
                container == "fmp4", hasInit, ended, fragments.toList())
        }
        // Build outside the file-publication lock: a long EVENT timeline must not block commits/readers.
        val text = buildString {
            append("#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-PLAYLIST-TYPE:EVENT\n")
            append("#EXT-X-TARGETDURATION:${snapshot.target}\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-START:TIME-OFFSET=0,PRECISE=YES\n")
            if (snapshot.fragmentedMp4) append("#EXT-X-MAP:URI=\"-1.mp4\"\n")
            snapshot.fragments.forEachIndexed { index, fragment ->
                append(fragment)
                append("$index.${if (snapshot.fragmentedMp4) "m4s" else "ts"}\n")
            }
            if (snapshot.ended) append("#EXT-X-ENDLIST\n")
        }
        synchronized(this) {
            // A racing writer may have published a newer revision; never overwrite its cache.
            if (snapshot.revision == revision) cachedPlaylist = CachedPlaylist(snapshot.revision, text)
        }
        return text
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

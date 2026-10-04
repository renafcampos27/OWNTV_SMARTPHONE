package tv.own.owntv.core.timeshift

import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/** Only committed, complete media is published. Readers pin files until their response closes. */
class LocalSegmentStore(
    val directory: File,
    val maxBytes: Long = 256L * 1024 * 1024,
    val maxDurationMs: Long = 30L * 60 * 1000,
    private val reserveBytes: Long = 512L * 1024 * 1024,
    private val freeBytes: () -> Long = { directory.usableSpace },
) : Closeable {
    data class Segment(val id: Long, val durationMs: Long, val bytes: Long, val discontinuity: Boolean)
    private data class Entry(val segment: Segment, val file: File, var readers: Int = 0, var retired: Boolean = false)
    private val entries = linkedMapOf<Long, Entry>()
    private var nextId = 0L
    private var closed = false
    private var finished = false
    private var discontinuitiesRemoved = 0L
    init { require(maxBytes > 0 && maxDurationMs > 0); require(directory.mkdirs() || directory.isDirectory) }

    @Synchronized fun snapshot(): List<Segment> = entries.values.filterNot { it.retired }.map { it.segment }
    @Synchronized fun bytesOnDisk(): Long = entries.values.sumOf { it.segment.bytes }
    /** Reserve room before the download, counting files held by slow readers. */
    @Synchronized fun prepareIncoming(maxIncomingBytes: Long, writtenBytes: Long = 0L) {
        check(!closed)
        require(maxIncomingBytes in 1..maxBytes)
        require(writtenBytes in 0..maxIncomingBytes)
        while (bytesOnDisk() + maxIncomingBytes > maxBytes) {
            val oldest = entries.values.firstOrNull { !it.retired } ?: error("Timeshift quota exhausted")
            retire(oldest)
        }
        // Existing part bytes already reduced usableSpace; reserve only the remaining write.
        val remaining = maxIncomingBytes - writtenBytes
        val available = freeBytes()
        check(available >= reserveBytes && available - reserveBytes >= remaining)
    }
    @Synchronized fun commit(part: File, durationMs: Long, discontinuity: Boolean): Segment {
        check(!closed)
        require(part.parentFile?.canonicalFile == directory.canonicalFile && part.isFile)
        val bytes = part.length()
        require(bytes > 0 && bytes <= maxBytes && durationMs in 1..maxDurationMs)
        // Include the temporary file and pinned, retired files in the disk budget.
        while (bytesOnDisk() + bytes > maxBytes || snapshot().sumOf { it.durationMs } + durationMs > maxDurationMs) {
            val oldest = entries.values.firstOrNull { !it.retired } ?: error("Timeshift quota exhausted")
            retire(oldest)
        }
        check(freeBytes() >= reserveBytes)
        val id = nextId++
        val file = File(directory, "$id.ts")
        check(part.renameTo(file))
        val segment = Segment(id, durationMs, bytes, discontinuity)
        entries[id] = Entry(segment, file)
        return segment
    }
    private fun retire(entry: Entry) {
        entry.retired = true
        if (entry.segment.discontinuity) discontinuitiesRemoved++
        if (entry.readers == 0) remove(entry)
    }
    private fun remove(entry: Entry) {
        check(!entry.file.exists() || entry.file.delete())
        entries.remove(entry.segment.id)
        if (closed && entries.isEmpty()) directory.delete()
    }
    @Synchronized fun open(id: Long): Reader? {
        if (closed) return null
        val entry = entries[id]?.takeUnless { it.retired } ?: return null
        val input = FileInputStream(entry.file)
        entry.readers++
        return Reader(input, entry.segment.bytes) {
            synchronized(this) { entry.readers--; if (entry.retired && entry.readers == 0) remove(entry) }
        }
    }
    class Reader(val input: FileInputStream, val size: Long, val type: String = "video/mp2t", private val release: () -> Unit) : Closeable {
        private var closed = false
        @Synchronized override fun close() { if (!closed) { closed = true; try { input.close() } finally { release() } } }
    }
    @Synchronized fun finish() { finished = true }
    @Synchronized fun playlist(): String {
        val visible = snapshot()
        check(visible.isNotEmpty())
        val target = ((visible.maxOf { it.durationMs } + 999) / 1000).coerceAtLeast(1)
        return buildString {
            append("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:$target\n")
            append("#EXT-X-MEDIA-SEQUENCE:${visible.first().id}\n")
            append("#EXT-X-DISCONTINUITY-SEQUENCE:$discontinuitiesRemoved\n")
            visible.forEach {
                if (it.discontinuity) append("#EXT-X-DISCONTINUITY\n")
                append("#EXTINF:${String.format(Locale.ROOT, "%.3f", it.durationMs / 1000.0)},\n${it.id}.ts\n")
            }
            if (finished) append("#EXT-X-ENDLIST\n")
        }
    }
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        entries.values.toList().forEach { retire(it) }
        directory.listFiles()?.filter { it.extension == "part" }?.forEach { it.delete() }
        if (entries.isEmpty()) directory.delete()
    }
}

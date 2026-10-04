package tv.own.owntv.core.recording

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Durable complete segments plus small per-segment records. No growing journal is rewritten per tick. */
internal class HlsCaptureSession(val directory: File, val playback: RecordingPlaybackStore = RecordingPlaybackStore(directory)) {
    data class Entry(val sequence: Long, val durationMs: Long, val name: String, val hash: String, val bytes: Long, val discontinuity: Long = 0)
    val entries = mutableListOf<Entry>()
    var archiveIdentity: String? = null
    var archiveTimeline: String? = null
    var variantIdentity: String? = null
    var container: String? = null
    var discontinuity: Long? = null
    var initHash: String? = null
    var lastSequence: Long = -1
        private set
    var capturedDurationMs = 0L
        private set
    var missingDurationMs = 0L
        private set
    var gapCount = 0
        private set
    var capturedBytes = 0L
        private set
    val journal = File(directory, "checkpoint.json")
    val initFile = File(directory, "init.bin")
    val incomplete: Boolean get() = gapCount > 0

    init {
        check(directory.isDirectory || directory.mkdirs()) { "HLS spool unavailable" }
        if (journal.isFile) {
            val json = JSONObject(journal.readText())
            check(json.getInt("v") == 1) { "Unsupported HLS checkpoint" }
            archiveIdentity = json.optString("archive").takeIf { it.isNotEmpty() }
            archiveTimeline = json.optString("timeline").takeIf { it.isNotEmpty() }
            variantIdentity = json.optString("variant").takeIf { it.isNotEmpty() }
            container = json.optString("container").takeIf { it.isNotEmpty() }
            discontinuity = if (json.isNull("dc")) null else json.getLong("dc")
            initHash = json.optString("init").takeIf { it.isNotEmpty() }
        }
        // A segment record is authoritative even if the process died before the summary/Room update.
        val records = directory.listFiles()?.filter { it.name.startsWith("record-") && it.name.endsWith(".json") }.orEmpty()
            .map { file -> file to JSONObject(file.readText()) }.sortedBy { it.second.getLong("seq") }
        val known = mutableSetOf("checkpoint.json", "init.bin")
        for ((recordFile, json) in records) {
            val sequence = json.getLong("seq")
            check(recordFile.name == "record-$sequence.json" && sequence > lastSequence) { "Unsafe HLS record" }
            known += recordFile.name
            if (json.has("name")) {
                val name = json.getString("name")
                check(name == "segment-$sequence.bin") { "Unsafe HLS payload path" }
                val bytes = json.getLong("bytes")
                check(bytes > 0 && File(directory, name).length() == bytes) { "HLS payload missing or damaged" }
                val duration = json.getLong("ms").coerceAtLeast(0)
                entries += Entry(sequence, duration, name, json.getString("hash"), bytes, json.optLong("dc"))
                capturedBytes += bytes
                capturedDurationMs += duration
                known += name
            } else {
                missingDurationMs += json.getLong("missing").coerceAtLeast(0)
                gapCount = (gapCount.toLong() + json.getInt("gaps")).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
            lastSequence = sequence
        }
        directory.listFiles()?.filter { it.name !in known }?.forEach { it.delete() }
        // Constant-size verification of the tail rather than hashing hours of captured media again.
        entries.lastOrNull()?.let { check(sha256(File(directory, it.name)) == it.hash) { "HLS tail damaged" } }
        check(initHash == null || (initFile.isFile && sha256(initFile) == initHash)) { "HLS initialization missing or damaged" }
        entries.forEach { playback.publish(it, container, initHash != null) }
    }

    /** No URL, token, credentials or growing segment list enters the database. */
    fun checkpoint(): String = JSONObject().apply {
        put("archive", archiveIdentity); put("timeline", archiveTimeline)
        put("v", 1); put("last", lastSequence); put("captured", capturedDurationMs)
        put("missing", missingDurationMs); put("gaps", gapCount)
        put("variant", variantIdentity); put("container", container); put("dc", discontinuity); put("init", initHash)
    }.toString()

    fun persist() = writeAtomic(journal, checkpoint())

    fun commitInit(complete: File) {
        val hash = sha256(complete)
        check(initHash == null || initHash == hash) { "HLS initialization changed" }
        atomicMove(complete, initFile)
        initHash = hash
        persist()
    }

    /** No checkpoint advance before a complete, fsynced payload and its atomic record exist. */
    fun commit(segment: HlsMediaPlaylist.Segment, complete: File): Boolean {
        if (segment.sequence <= lastSequence) { complete.delete(); return false }
        val hash = sha256(complete)
        val name = "segment-${segment.sequence}.bin"
        val bytes = complete.length()
        val ms = HlsRecordingPlan.durationMs(segment)
        persist() // Fixed format/variant also survives a crash before the first media commit.
        atomicMove(complete, File(directory, name))
        writeAtomic(File(directory, "record-${segment.sequence}.json"), JSONObject()
            .put("seq", segment.sequence).put("dc", segment.discontinuity).put("ms", ms).put("name", name).put("hash", hash).put("bytes", bytes).toString())
        entries += Entry(segment.sequence, ms, name, hash, bytes, segment.discontinuity)
        lastSequence = segment.sequence
        capturedDurationMs += ms
        capturedBytes += bytes
        persist()
        playback.publish(entries.last(), container, initHash != null)
        return true
    }

    /** Only exhausted retries, explicit EXT-X-GAP or expired windows call this operation. */
    fun skip(sequence: Long, durationMs: Long, count: Int = 1) {
        if (sequence <= lastSequence) return
        val ms = durationMs.coerceAtLeast(0)
        val gaps = count.coerceAtLeast(1)
        writeAtomic(File(directory, "record-$sequence.json"), JSONObject().put("seq", sequence)
            .put("missing", ms).put("gaps", gaps).toString())
        lastSequence = sequence
        missingDurationMs += ms
        gapCount = (gapCount.toLong() + gaps).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        persist()
    }

    fun inputs(): List<File> = entries.map { File(directory, it.name) }
    fun discard() { playback.discard() }

    companion object {
        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(128 * 1024)
                while (true) { val read = input.read(buffer); if (read < 0) break; digest.update(buffer, 0, read) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
        private fun writeAtomic(destination: File, contents: String) {
            val temporary = File(destination.parentFile, "${destination.name}.tmp")
            FileOutputStream(temporary).use { output -> output.write(contents.toByteArray(Charsets.UTF_8)); output.fd.sync() }
            atomicMove(temporary, destination)
        }
        private fun atomicMove(from: File, to: File) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

package tv.own.owntv.core.recording

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import tv.own.owntv.core.database.entity.EpgProgrammeEntity
import tv.own.owntv.core.database.entity.RecordingEntity

data class RecordingChapter(val title: String, val startMs: Long, val stopMs: Long)
data class RecordingChapterPoint(val title: String, val positionMs: Long)

/** EPG snapshot, independent of guide retention. Positions are approximate wall-clock mappings. */
object RecordingChapters {
    fun snapshot(row: RecordingEntity, programmes: List<EpgProgrammeEntity>): List<RecordingChapter> =
        programmes.asSequence().filter { it.title.isNotBlank() && it.stopMs > row.startMs && it.startMs < row.stopMs }
            .sortedWith(compareBy<EpgProgrammeEntity> { it.startMs }.thenBy { if (it.sourceId == row.sourceId) 0 else 1 })
            .distinctBy { it.startMs }.take(512).map { RecordingChapter(it.title, it.startMs, it.stopMs) }.toList()
            .ifEmpty { listOf(RecordingChapter(row.title, row.programmeStartMs, row.programmeStopMs)) }

    fun points(row: RecordingEntity, chapters: List<RecordingChapter>): List<RecordingChapterPoint> {
        if (row.gapCount > 0 || row.missingDurationMs > 0) return emptyList()
        val origin = if (RecordingSchedule.isCatchUp(row)) row.programmeStartMs else maxOf(row.startMs, row.startedAt ?: row.startMs)
        val end = if (row.capturedDurationMs > 0) origin + row.capturedDurationMs else row.endedAt?.coerceAtMost(row.stopMs) ?: row.stopMs
        return chapters.filter { it.stopMs > origin && it.startMs < end }.map {
            RecordingChapterPoint(it.title, (it.startMs - origin).coerceAtLeast(0))
        }.distinctBy { it.positionMs }
    }
}

internal class RecordingChapterStore(private val directory: File) {
    private fun file(row: RecordingEntity) = File(directory, "${row.id}-${row.createdAt}.chapters")
    @Synchronized fun save(row: RecordingEntity, chapters: List<RecordingChapter>) {
        require(chapters.size <= 512)
        check(directory.isDirectory || directory.mkdirs())
        val target = file(row)
        val temporary = File(directory, "${target.name}.tmp")
        try {
            java.io.FileOutputStream(temporary).use { raw ->
                val output = DataOutputStream(raw)
                output.writeInt(1); output.writeLong(row.profileId); output.writeLong(row.sourceId); output.writeLong(row.channelId)
                output.writeInt(chapters.size)
                chapters.forEach { entry ->
                    val title = entry.title.toByteArray(Charsets.UTF_8)
                    require(title.size <= 16_384)
                    output.writeInt(title.size); output.write(title); output.writeLong(entry.startMs); output.writeLong(entry.stopMs)
                }
                output.flush(); raw.fd.sync()
            }
            java.nio.file.Files.move(temporary.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    @Synchronized fun read(row: RecordingEntity): List<RecordingChapter> {
        val target = file(row)
        if (!target.isFile) return emptyList()
        require(target.length() <= 9 * 1024 * 1024)
        return DataInputStream(target.inputStream().buffered()).use { input ->
            require(input.readInt() == 1 && input.readLong() == row.profileId && input.readLong() == row.sourceId && input.readLong() == row.channelId)
            val count = input.readInt(); require(count in 0..512)
            val entries = List(count) {
                val length = input.readInt(); require(length in 0..16_384)
                val bytes = ByteArray(length); input.readFully(bytes)
                RecordingChapter(bytes.toString(Charsets.UTF_8), input.readLong(), input.readLong()).also { require(it.stopMs > it.startMs) }
            }
            require(input.read() == -1)
            entries
        }
    }
    @Synchronized fun delete(row: RecordingEntity) { file(row).delete() }
}

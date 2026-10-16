package tv.own.owntv.core.repository

import kotlinx.coroutines.ensureActive
import java.io.Closeable
import java.io.File
import org.json.JSONObject
import tv.own.owntv.core.database.entity.EpgProgrammeEntity

/** Private temporary records: parse first, then replace programmes atomically using bounded batches. */
internal class EpgProgrammeSpool(directory: File) : Closeable {
    private val file = File.createTempFile("own-epg-fill-", ".spool", directory)
    private val output = java.io.DataOutputStream(file.outputStream().buffered())
    private var finished = false
    private var records = 0
    private var bytes = 0L
    val byteCount: Long get() = bytes

    fun append(row: EpgProgrammeEntity) {
        check(!finished)
        val json = JSONObject().put("source", row.sourceId).put("channel", row.epgChannelId)
            .put("start", row.startMs).put("stop", row.stopMs).put("title", row.title)
            .put("description", row.description).put("categories", row.categories).put("year", row.year)
            .put("rating", row.rating).put("length", row.lengthMin).put("episode", row.episode)
        val data = json.toString().toByteArray(Charsets.UTF_8)
        require(data.size <= MAX_RECORD_BYTES)
        bytes += 4 + data.size
        require(bytes <= MAX_SPOOL_BYTES)
        output.writeInt(data.size); output.write(data)
        records++
    }

    fun finish() { if (!finished) { output.close(); finished = true } }

    suspend fun consume(batchSize: Int, emit: suspend (List<EpgProgrammeEntity>) -> Unit) {
        check(finished); require(batchSize > 0)
        file.inputStream().buffered().use { raw ->
            val input = java.io.DataInputStream(raw)
            val batch = ArrayList<EpgProgrammeEntity>(batchSize)
            var batchBytes = 0
            repeat(records) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val length = input.readInt()
                require(length in 1..MAX_RECORD_BYTES)
                val bytes = ByteArray(length); input.readFully(bytes)
                val json = JSONObject(bytes.toString(Charsets.UTF_8))
                fun text(key: String) = if (json.isNull(key)) null else json.getString(key)
                fun integer(key: String) = if (json.isNull(key)) null else json.getInt(key)
                batch += EpgProgrammeEntity(sourceId = json.getLong("source"), epgChannelId = json.getString("channel"),
                    startMs = json.getLong("start"), stopMs = json.getLong("stop"), title = json.getString("title"),
                    description = text("description"), categories = text("categories"), year = integer("year"),
                    rating = text("rating"), lengthMin = integer("length"), episode = text("episode"))
                batchBytes += length
                if (batch.size >= batchSize || batchBytes >= MAX_RECORD_BYTES) {
                    emit(batch.toList()); batch.clear(); batchBytes = 0
                }
            }
            require(input.read() == -1)
            if (batch.isNotEmpty()) emit(batch)
        }
    }

    override fun close() {
        try { finish() } finally { file.delete() }
    }

    companion object {
        private const val MAX_RECORD_BYTES = 2 * 1024 * 1024
        private const val MAX_SPOOL_BYTES = 128L * 1024 * 1024
    }
}

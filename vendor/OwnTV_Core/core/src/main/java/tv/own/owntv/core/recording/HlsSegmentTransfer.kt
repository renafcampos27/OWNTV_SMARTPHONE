package tv.own.owntv.core.recording

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** An HTTP body becomes eligible for commit only after EOF, size validation and fsync. */
internal object HlsSegmentTransfer {
    const val MAX_SEGMENT_BYTES = 64L * 1024 * 1024
    fun copy(input: InputStream, destination: File, expectedBytes: Long? = null,
             beforeWrite: (writtenBytes: Long, additionalBytes: Int) -> Unit = { _, _ -> },
             check: () -> Unit = {}) {
        try {
            FileOutputStream(destination).use { out ->
                val buffer = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    check()
                    val read = input.read(buffer)
                    if (read < 0) break
                    require(total + read <= MAX_SEGMENT_BYTES) { "HLS segment exceeds capture limit" }
                    require(expectedBytes == null || total + read <= expectedBytes) { "Incomplete HLS segment" }
                    beforeWrite(total, read)
                    total += read
                    out.write(buffer, 0, read)
                }
                check()
                require(total > 0 && (expectedBytes == null || expectedBytes == total)) { "Incomplete HLS segment" }
                out.fd.sync()
            }
        } catch (error: Throwable) {
            destination.delete()
            throw error
        }
    }

    fun isTransportStream(file: File): Boolean {
        file.inputStream().use { input ->
            val bytes = ByteArray(377)
            var count = 0
            while (count < bytes.size) { val n = input.read(bytes, count, bytes.size - count); if (n < 0) break; count += n }
            return count >= 188 && bytes[0].toInt() and 0xff == 0x47 &&
                (count < 189 || bytes[188].toInt() and 0xff == 0x47) && file.length() % 188 == 0L
        }
    }

    /** Container boxes only; codec support remains the framework extractor/muxer's responsibility. */
    fun hasMp4Box(file: File, required: String): Boolean = java.io.RandomAccessFile(file, "r").use { input ->
        var offset = 0L
        var boxes = 0
        while (offset + 8 <= input.length() && boxes++ < 256) {
            input.seek(offset)
            var size = input.readInt().toLong() and 0xffffffffL
            val type = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            val header = if (size == 1L) { size = input.readLong(); 16L } else 8L
            if (size == 0L) size = input.length() - offset
            if (size < header || size > input.length() - offset) return@use false
            if (type == required) return@use true
            offset += size
        }
        false
    }
}

package tv.own.owntv.core.recording

import java.io.File
import java.io.OutputStream

/** The provider has closed its output before a stored recording is accepted as complete. */
internal object RecordingStoredCopy {
    fun copy(source: File, openTarget: () -> OutputStream, targetLength: () -> Long,
        checkActive: () -> Unit = {}): Long {
        checkActive()
        val expected = source.length()
        check(expected > 0L) { "Empty recording output" }
        var copied = 0L
        source.inputStream().use { input ->
            openTarget().use { output ->
                val buffer = ByteArray(128 * 1024)
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    copied += count
                    check(copied <= expected) { "Recording output changed during copy" }
                    output.write(buffer, 0, count)
                }
                output.flush()
            }
        }
        checkActive()
        check(copied == expected && source.length() == expected && targetLength() == expected) {
            "Recording stored size mismatch"
        }
        return copied
    }
}

package tv.own.owntv.core.recording

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingStoredCopyTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun source() = temporary.newFile().apply { writeBytes(ByteArray(180_000) { it.toByte() }) }

    @Test fun copyClosesProviderBeforeValidatingItsStoredBytesAndKeepsSource() {
        val source = source()
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true; super.close() } }
        val copied = RecordingStoredCopy.copy(source, { output }, { assertTrue(closed); output.size().toLong() })
        assertEquals(source.length(), copied)
        assertArrayEquals(source.readBytes(), output.toByteArray())
        assertTrue(source.isFile)
    }

    @Test fun providerThatSilentlyDiscardsBytesIsNotAccepted() {
        val source = source()
        val sink = object : OutputStream() { override fun write(value: Int) { } }
        try {
            RecordingStoredCopy.copy(source, { sink }, { 0L })
            fail("Expected size validation")
        } catch (_: IllegalStateException) { }
        assertTrue(source.isFile)
    }

    @Test fun closeFailureAndCancellationDoNotDestroyRecoverableSource() {
        val source = source()
        val broken = object : ByteArrayOutputStream() { override fun close() { throw IOException("provider close") } }
        try {
            RecordingStoredCopy.copy(source, { broken }, { broken.size().toLong() })
            fail("Expected close failure")
        } catch (_: IOException) { }
        var checks = 0
        var closed = false
        val output = object : ByteArrayOutputStream() { override fun close() { closed = true } }
        try {
            RecordingStoredCopy.copy(source, { output }, { output.size().toLong() }) {
                if (++checks == 3) throw CancellationException()
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertTrue(closed)
        assertTrue(source.isFile)
        assertTrue(output.size() < source.length())
    }
}

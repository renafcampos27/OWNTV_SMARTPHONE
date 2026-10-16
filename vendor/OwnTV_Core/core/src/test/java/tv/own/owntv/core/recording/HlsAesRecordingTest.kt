package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class HlsAesRecordingTest {
    private fun playlist(tags: String) = HlsMediaPlaylist.parse("#EXTM3U\n#EXT-X-MEDIA-SEQUENCE:7\n$tags\n#EXTINF:6,\na.ts")
    private val keyTag = "#EXT-X-KEY:METHOD=AES-128,URI=\"key\""
    @Test fun keysAreScopedToEachSegmentAndNoneClearsOnlyFollowingSegments() {
        val p = HlsMediaPlaylist.parse("#EXTM3U\n$keyTag\n#EXTINF:6,\na.ts\n#EXT-X-KEY:METHOD=NONE\n#EXTINF:6,\nb.ts")
        assertNotNull(p.segments.first().key)
        assertNull(p.segments.last().key)
        assertFalse(p.hasUnsupportedEncryption)
        assertEquals(7.toByte(), HlsAes.ivFor(playlist(keyTag).segments.single()).last())
    }
    @Test fun malformedIvNeverFallsBackToSequenceAndDrmStaysRefused() {
        val short = playlist("$keyTag,IV=0x12")
        assertFalse(short.invalid)
        assertEquals(0x12.toByte(), HlsAes.ivFor(short.segments.single()).last())
        assertTrue(playlist("$keyTag,IV=0x").invalid)
        assertTrue(playlist("$keyTag,IV=0x123456789012345678901234567890123").invalid)
        assertTrue(playlist("$keyTag,IV=0xZZ000000000000000000000000000000").invalid)
        assertTrue(playlist("$keyTag,KEYFORMAT=\"com.apple.streamingkeydelivery\"").hasUnsupportedEncryption)
        assertTrue(playlist("#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key\"").hasUnsupportedEncryption)
    }
    @Test fun encryptedRangesAndMapsAreRefused() {
        assertTrue(playlist("$keyTag\n#EXT-X-BYTERANGE:188@0").hasUnsupportedEncryption)
        assertTrue(playlist("$keyTag\n#EXT-X-MAP:URI=\"init.mp4\"").hasUnsupportedEncryption)
    }
    private fun encrypted(clear: ByteArray, key: ByteArray, segment: HlsMediaPlaylist.Segment) =
        Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(HlsAes.ivFor(segment)))
            doFinal(clear)
        }
    @Test fun largeCiphertextStreamsToCompleteClearTransportMediaWithClearHash() {
        val key = ByteArray(16) { it.toByte() }
        val segment = playlist(keyTag).segments.single()
        val clear = ByteArray(188 * 4000).also { for (i in it.indices step 188) it[i] = 0x47 }
        val encrypted = encrypted(clear, key, segment)
        val destination = File.createTempFile("aes-media", ".ts")
        try {
            val hash = HlsSegmentTransfer.copy(ByteArrayInputStream(encrypted), destination,
                encrypted.size.toLong(), cipher = HlsAes.cipher(key, segment))
            assertArrayEquals(clear, destination.readBytes())
            assertEquals(HlsCaptureSession.sha256(destination), hash)
            assertTrue(HlsSegmentTransfer.isTransportStream(destination))
        } finally { destination.delete() }
    }
    @Test fun truncatedCiphertextOrCancellationCannotLeaveACommitCandidate() {
        val key = ByteArray(16)
        val segment = playlist(keyTag).segments.single()
        val encrypted = encrypted(ByteArray(188), key, segment)
        val destination = File.createTempFile("aes-abort", ".ts")
        try {
            try {
                HlsSegmentTransfer.copy(ByteArrayInputStream(encrypted.copyOf(encrypted.size - 1)), destination,
                    cipher = HlsAes.cipher(key, segment))
                fail("truncated AES must fail")
            } catch (_: javax.crypto.IllegalBlockSizeException) { }
            assertFalse(destination.exists())
            try {
                HlsSegmentTransfer.copy(ByteArrayInputStream(encrypted), destination,
                    cipher = HlsAes.cipher(key, segment), check = { throw java.util.concurrent.CancellationException() })
                fail("cancelled AES must fail")
            } catch (_: java.util.concurrent.CancellationException) { }
            assertFalse(destination.exists())
        } finally { destination.delete() }
    }
}

package tv.own.owntv.core.backup

import java.io.File
import java.nio.ByteBuffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupAuthenticationTest {
    @get:Rule val folder = TemporaryFolder()
    private val password = "test-password"
    private fun export(): File = folder.newFile().apply {
        outputStream().use { BackupContainer.writeTo(it, BackupContainer.Payload("{\"version\":25}", null), password) }
    }
    private fun headerEnd(bytes: ByteArray) = 12 + ByteBuffer.wrap(bytes, 8, 4).int
    private fun failure(file: File, pass: String = password): Throwable =
        runCatching { BackupContainer.open(file, pass, temporaryDirectory = folder.root) }.exceptionOrNull()
            ?: error("accepted invalid backup")

    @Test fun wrongPasswordAndModifiedCiphertextAreAuthenticationFailures() {
        val file = export()
        assertTrue(failure(file, "wrong") is BackupManager.WrongPasswordException)
        val bytes = file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        file.writeBytes(bytes)
        assertTrue(failure(file) is BackupManager.WrongPasswordException)
        assertTrue(folder.root.listFiles()!!.none { it.name.startsWith("own-backup-") })
    }

    @Test fun structuralTruncationIsNotReportedAsWrongPassword() {
        val file = export()
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(headerEnd(bytes) + 12 + 3))
        val error = failure(file)
        assertFalse(error is BackupManager.WrongPasswordException)
        assertTrue(error is IllegalArgumentException)
    }

    @Test fun malformedCryptoMetadataAndInvalidInnerZipAreStructuralErrors() {
        val file = export()
        val bytes = file.readBytes()
        val end = headerEnd(bytes)
        val header = JSONObject(String(bytes, 12, end - 12, Charsets.UTF_8))
        val key = BackupCrypto.deriveKey(password, header)!!
        val malformed = header.put("salt", "invalid") .toString().toByteArray()
        file.writeBytes(bytes.copyOfRange(0, 8) + ByteBuffer.allocate(4).putInt(malformed.size).array() + malformed + bytes.copyOfRange(end, bytes.size))
        assertFalse(failure(file) is BackupManager.WrongPasswordException)
        val validHeader = bytes.copyOfRange(12, end)
        file.writeBytes(bytes.copyOfRange(0, 8) + ByteBuffer.allocate(4).putInt(validHeader.size).array() + validHeader + BackupCrypto.encryptBytes(key, "invalid zip".toByteArray()))
        assertFalse(failure(file) is BackupManager.WrongPasswordException)
    }

    @Test fun unsupportedKdfAndMalformedEncryptedFieldStayStructural() {
        val metadata = BackupCrypto.cryptoBlock(BackupCrypto.newSalt())
        assertNull(BackupCrypto.deriveKey(password, JSONObject(metadata.toString()).put("iterations", 2_000_001)))
        assertNull(BackupCrypto.deriveKey(password, JSONObject(metadata.toString()).put("kdf", "unknown")))
        val key = BackupCrypto.deriveKey(password, metadata)!!
        val error = runCatching { BackupCrypto.decrypt(key, JSONObject().put("iv", "AA==").put("ct", "AA==")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertFalse(error is javax.crypto.AEADBadTagException)
    }

    @Test fun authenticatedStreamRoundTripsWithoutRetainingScratch() {
        val file = export()
        assertEquals("{\"version\":25}", BackupContainer.open(file, password, temporaryDirectory = folder.root).json)
        assertTrue(folder.root.listFiles()!!.none { it.name.startsWith("own-backup-") })
    }
}

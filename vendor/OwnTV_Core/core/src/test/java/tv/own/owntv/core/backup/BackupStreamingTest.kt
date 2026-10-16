package tv.own.owntv.core.backup

import java.io.File
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupStreamingTest {
    @get:Rule val folder = TemporaryFolder()
    private val json = """{"version":25,"sources":[{"username":"user","url":"https://example.invalid/list.m3u"}],"settings":{"delay":5}}"""

    @Test fun fileAssetsRoundTripAndInspectionDoesNotRetainThem() {
        val image = folder.newFile().apply { writeBytes(ByteArray(256_000) { (it % 127).toByte() }) }
        val file = File(folder.root, "backup.own")
        val payload = BackupContainer.Payload(json, null, fileAssets = mapOf("wallpaper/image.jpg" to image, "avatars/1.jpg" to image))
        BackupManager.writeAtomically(file) { BackupContainer.writeTo(it, payload, null) }
        val restored = BackupContainer.open(file)
        assertEquals(json, restored.json)
        assertArrayEquals(image.readBytes(), restored.wallpaper!!.bytes)
        assertArrayEquals(image.readBytes(), restored.avatars["1.jpg"])
        val inspected = BackupContainer.open(file, includeAssets = false)
        assertEquals(json, inspected.json)
        assertNull(inspected.wallpaper)
        assertTrue(inspected.avatars.isEmpty()); assertTrue(inspected.subtitles.isEmpty())
    }

    @Test fun legacyAndByteAssetExportsStillOpen() {
        val file = folder.newFile().apply { writeText(json, Charsets.UTF_8) }
        assertEquals(json, BackupContainer.open(file).json)
        val bytes = BackupContainer.pack(BackupContainer.Payload(json, BackupContainer.Asset("wall.jpg", byteArrayOf(1, 2)),
            subtitles = mapOf("one.srt" to byteArrayOf(3, 4))), null)
        file.writeBytes(bytes)
        val payload = BackupContainer.open(file)
        assertArrayEquals(byteArrayOf(1, 2), payload.wallpaper!!.bytes)
        assertArrayEquals(byteArrayOf(3, 4), payload.subtitles["one.srt"])
    }

    @Test fun cancelledStreamingWriteLeavesPreviousBackupIntact() {
        val target = folder.newFile().apply { writeText("previous") }
        val asset = folder.newFile().apply { writeBytes(ByteArray(512_000)) }
        var checks = 0
        val result = runCatching {
            BackupManager.writeAtomically(target) {
                BackupContainer.writeTo(it, BackupContainer.Payload(json, null,
                    fileAssets = mapOf("avatars/image.jpg" to asset)), null) {
                    if (++checks > 3) throw kotlinx.coroutines.CancellationException()
                }
            }
        }
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertEquals("previous", target.readText())
        assertFalse(File(folder.root, target.name + BackupManager.TMP_SUFFIX).exists())
        assertTrue(asset.isFile)
    }

    @Test fun inspectionRejectsOversizedExpandedAssetEvenWhenNotRetained() {
        val file = folder.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json")); zip.write(json.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("avatars/large.jpg"))
            val buffer = ByteArray(64 * 1024)
            repeat((BackupContainer.MAX_ASSET_BYTES / buffer.size).toInt() + 1) { zip.write(buffer) }
            zip.closeEntry()
        }
        assertTrue(runCatching { BackupContainer.open(file, includeAssets = false) }.isFailure)
    }

    @Test fun traversalAndMissingExportSourceAreRejectedWithoutReplacingBackup() {
        val file = folder.newFile()
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json")); zip.write(json.toByteArray()); zip.closeEntry()
            zip.putNextEntry(ZipEntry("avatars/../outside")); zip.write(byteArrayOf(1)); zip.closeEntry()
        }
        assertTrue(runCatching { BackupContainer.open(file) }.isFailure)
        file.writeText("good")
        assertTrue(runCatching {
            BackupManager.writeAtomically(file) { BackupContainer.writeTo(it,
                BackupContainer.Payload(json, null, fileAssets = mapOf("avatars/a.jpg" to File(folder.root, "missing"))), null) }
        }.isFailure)
        assertEquals("good", file.readText())
    }

    @Test fun streamingEncryptionHasTheExistingWireFormatAndAuthenticatesTheTag() {
        val key = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val plain = ByteArray(128_000) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(key, encrypted).use { it.write(plain, 0, 64_000); it.write(plain, 64_000, 64_000) }
        assertArrayEquals(plain, BackupCrypto.decryptBytes(key, encrypted.toByteArray()))
        val restored = ByteArrayOutputStream()
        BackupCrypto.decryptTo(key, BackupCrypto.encryptBytes(key, plain).inputStream(), restored, 256_000)
        assertArrayEquals(plain, restored.toByteArray())
        val corrupt = encrypted.toByteArray().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
        assertTrue(runCatching { BackupCrypto.decryptTo(key, corrupt.inputStream(), ByteArrayOutputStream(), 256_000) }.isFailure)
    }

    @Test fun failedRotationKeepsThePrimaryBackup() {
        val target = File(folder.root, "backup.own")
        BackupManager.writeAtomically(target, "good")
        File(folder.root, target.name + BackupManager.BAK_SUFFIX).mkdir()
        File(folder.root, target.name + BackupManager.BAK_SUFFIX + "/keep").writeText("keep")
        assertTrue(runCatching { BackupManager.writeAtomically(target, "new") }.isFailure)
        assertEquals("good", target.readText())
    }
}

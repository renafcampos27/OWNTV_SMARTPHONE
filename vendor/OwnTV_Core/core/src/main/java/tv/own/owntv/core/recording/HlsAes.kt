package tv.own.owntv.core.recording

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Transport AES-128 only. Keys are ephemeral and never enter checkpoints or backups. */
internal object HlsAes {
    const val KEY_BYTES = 16
    private val explicitIv = Regex("0[xX][0-9a-fA-F]{1,32}")
    fun validIv(value: String): Boolean = explicitIv.matches(value)
    fun ivFor(segment: HlsMediaPlaylist.Segment): ByteArray {
        val iv = segment.key?.iv
        if (iv != null) {
            require(validIv(iv)) { "Invalid HLS IV" }
            val hexadecimal = iv.substring(2).padStart(32, '0')
            return ByteArray(16) { hexadecimal.substring(it * 2, 2 + it * 2).toInt(16).toByte() }
        }
        require(segment.sequence >= 0) { "Invalid HLS sequence" }
        return ByteBuffer.allocate(16).putLong(0).putLong(segment.sequence).array()
    }
    fun cipher(key: ByteArray, segment: HlsMediaPlaylist.Segment): Cipher {
        require(key.size == KEY_BYTES) { "Invalid HLS key" }
        return Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(ivFor(segment)))
        }
    }
}

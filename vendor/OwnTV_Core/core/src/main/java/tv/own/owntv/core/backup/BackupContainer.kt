package tv.own.owntv.core.backup

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The `.own` backup container — what [BackupManager.export] writes from v4.2 on.
 *
 * A backup is no longer a bare `owntv-backup.json`. Two reasons:
 *
 * 1. **It has to carry files, not just text.** The Glass effect background image lives in app-private
 *    storage as an absolute path (`filesDir/backgrounds/background_<ts>.jpg`). Backing that *path* up
 *    was useless — it does not exist on the device being restored, so the wallpaper silently came back
 *    blank. The image bytes now ride inside the container.
 * 2. **A plain JSON file exposes everything.** Field-level [BackupCrypto] only ever sealed the
 *    secrets (source/proxy passwords, TMDB key, OpenSubtitles login); playlist URLs, usernames,
 *    profile names and watch history sat next to them in readable text. With a passphrase the whole
 *    container is now encrypted — the file list included.
 *
 * ## Layout
 *
 * - **No passphrase** → a plain ZIP (`PK\x03\x04`): `backup.json` at the root, plus
 *   `wallpaper/<filename>` when a background is set. Secrets are omitted, exactly as before.
 * - **Passphrase** → [MAGIC] + a 4-byte big-endian header length + a plaintext header JSON (the
 *   [BackupCrypto] KDF params: scheme/kdf/iterations/salt) + `iv || ciphertext` of the entire ZIP.
 *   The header must stay plaintext: restore needs the salt and iteration count to derive the key
 *   before it can decrypt anything.
 *
 * The inner `backup.json` is byte-for-byte the schema the old `.json` export used, field-level
 * sealing included. That is deliberate — [BackupManager] builds and parses one JSON shape, and a
 * container is only ever a wrapper around it.
 *
 * Reading is format-sniffing, never extension-trusting: [probe] looks at the first bytes, so a legacy
 * `.json` (or a `.own` a user renamed) restores through the same call.
 *
 * Logging rule, inherited from [BackupCrypto]: nothing here logs the passphrase, key or plaintext.
 */
object BackupContainer {
    /** File magic for an ENCRYPTED container. Plain containers are ordinary ZIPs and have none. */
    private val MAGIC = "OWNTVBK1".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK"

    const val JSON_ENTRY = "backup.json"
    const val WALLPAPER_DIR = "wallpaper/"

    /**
     * Downloaded/imported subtitle files (v17). Like the wallpaper, these are real files the JSON can
     * only *point* at: `subtitle_cache.cachedPath` is an absolute path into this device's private
     * storage, so restoring the row alone gave the next device a remembered subtitle selection whose
     * file did not exist. Entry names are `<cacheId>_<fileName>`, matched back to their row by the
     * `file` field the manager writes alongside each exported cache row.
     */
    const val SUBTITLE_DIR = "subtitles/"

    /**
     * Profile pictures (v37) — the same problem as the wallpaper and the subtitle files: the JSON can
     * only point at `profiles.avatarPath`, which is an absolute path into *this* device's private
     * storage and means nothing on another one. Without the bytes, restoring a profile on a new
     * television would silently drop the picture the user chose.
     *
     * Entry names are `<profileId>_<fileName>`, matched back by the `avatarFile` field the manager
     * writes alongside each exported profile — the id in the name is the *exported* one, which a
     * restore may map onto a different local profile, so the field is what resolves it, not the name.
     */
    const val AVATAR_DIR = "avatars/"

    /** A file riding inside the container alongside the JSON (today: the background image). */
    data class Asset(val name: String, val bytes: ByteArray) {
        // ByteArray gives identity equals/hashCode, which makes this data class quietly wrong in any
        // set/map. Nothing relies on that today, but the trap is cheap to close.
        override fun equals(other: Any?) =
            other is Asset && name == other.name && bytes.contentEquals(other.bytes)

        override fun hashCode() = 31 * name.hashCode() + bytes.contentHashCode()
    }

    /** The unpacked contents of a backup file, whatever format it arrived in. */
    data class Payload(
        val json: String,
        val wallpaper: Asset?,
        /** Subtitle files, keyed by their container entry name (see [SUBTITLE_DIR]). */
        val subtitles: Map<String, ByteArray> = emptyMap(),
        /** Profile pictures, keyed by their container entry name (see [AVATAR_DIR]). */
        val avatars: Map<String, ByteArray> = emptyMap(),
        /** Export-only sources, streamed from disk using their complete container entry names. */
        val fileAssets: Map<String, File> = emptyMap(),
    )

    /** What a file on disk turns out to be. */
    enum class Kind {
        /** A `.own` ZIP with no passphrase — readable straight away. */
        CONTAINER,

        /** A `.own` sealed with a passphrase — [open] needs the password. */
        ENCRYPTED_CONTAINER,

        /** A pre-4.2 bare `owntv-backup.json`. Still restorable, forever. */
        LEGACY_JSON,

        /** Not something we can read. */
        UNKNOWN,
    }

    /** Sniffs [file] by its leading bytes — the extension is never trusted. */
    fun probe(file: File): Kind {
        val head = runCatching {
            file.inputStream().use { input ->
                ByteArray(MAGIC.size).let { buf ->
                    val n = input.read(buf)
                    if (n <= 0) ByteArray(0) else buf.copyOf(n)
                }
            }
        }.getOrElse { return Kind.UNKNOWN }
        return when {
            head.size >= MAGIC.size && head.copyOf(MAGIC.size).contentEquals(MAGIC) -> Kind.ENCRYPTED_CONTAINER
            head.size >= 4 && head.copyOf(4).contentEquals(ZIP_MAGIC) -> Kind.CONTAINER
            // Anything else non-empty is treated as a legacy JSON file and left to the JSON parser —
            // a file starting with whitespace or a BOM is still perfectly valid JSON.
            head.isNotEmpty() -> Kind.LEGACY_JSON
            else -> Kind.UNKNOWN
        }
    }

    /** Compatibility helper; production exports use writeTo to avoid whole-archive copies. */
    fun pack(payload: Payload, passphrase: String?): ByteArray = ByteArrayOutputStream().also {
        writeTo(it, payload, passphrase)
    }.toByteArray()

    /** The caller owns output (including its final fsync/atomic promotion). */
    fun writeTo(output: java.io.OutputStream, payload: Payload, passphrase: String?,
        checkCancelled: () -> Unit = {}) {
        val destination = object : java.io.FilterOutputStream(output) {
            override fun close() { flush() }
        }
        val pass = passphrase?.takeIf { it.isNotBlank() }
        val stream = if (pass == null) destination else {
            val salt = BackupCrypto.newSalt()
            val key = BackupCrypto.deriveKey(pass.toCharArray(), salt, BackupCrypto.ITERATIONS)
            val header = BackupCrypto.cryptoBlock(salt).put("scheme", CONTAINER_SCHEME)
                .toString().toByteArray(Charsets.UTF_8)
            destination.write(MAGIC); destination.write(intToBytes(header.size)); destination.write(header)
            BackupCrypto.encryptingStream(key, destination)
        }
        ZipOutputStream(stream).use { zip ->
            val names = HashSet<String>()
            var total = 0L
            fun put(name: String, length: Long, input: () -> java.io.InputStream) {
                validateName(name)
                require(names.add(name) && names.size <= MAX_ENTRIES)
                require(length in 0..(if (name == JSON_ENTRY) MAX_JSON_BYTES else MAX_ASSET_BYTES))
                total += length
                require(total <= MAX_EXPANDED_BYTES)
                zip.putNextEntry(ZipEntry(name))
                var copied = 0L
                input().use { source ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        checkCancelled()
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= length)
                        zip.write(buffer, 0, count)
                    }
                }
                require(copied == length)
                zip.closeEntry()
            }
            val json = payload.json.toByteArray(Charsets.UTF_8)
            put(JSON_ENTRY, json.size.toLong()) { json.inputStream() }
            payload.wallpaper?.let { put(WALLPAPER_DIR + it.name, it.bytes.size.toLong()) { it.bytes.inputStream() } }
            payload.subtitles.forEach { (name, bytes) -> put(SUBTITLE_DIR + name, bytes.size.toLong()) { bytes.inputStream() } }
            payload.avatars.forEach { (name, bytes) -> put(AVATAR_DIR + name, bytes.size.toLong()) { bytes.inputStream() } }
            payload.fileAssets.forEach { (name, file) ->
                require(file.isFile)
                val length = file.length()
                put(name, length) { file.inputStream() }
                require(file.length() == length)
            }
        }
    }

    /** Inspection validates the archive but never retains wallpaper/subtitle/avatar bytes. */
    fun open(file: File, passphrase: String? = null, includeAssets: Boolean = true,
        temporaryDirectory: File = requireNotNull(file.absoluteFile.parentFile)): Payload {
        require(file.length() in 1..MAX_CONTAINER_BYTES) { "Backup exceeds size limit" }
        return when (probe(file)) {
            Kind.LEGACY_JSON -> Payload(file.inputStream().use { readLimited(it, MAX_JSON_BYTES) }.toString(Charsets.UTF_8), null)
            Kind.CONTAINER -> file.inputStream().use { unzip(it, includeAssets) }
            Kind.ENCRYPTED_CONTAINER -> {
                val pass = passphrase?.takeIf { it.isNotBlank() } ?: throw BackupManager.WrongPasswordException()
                val staleBefore = System.currentTimeMillis() - 24L * 60 * 60 * 1000
                temporaryDirectory.listFiles()?.forEach {
                    if (it.isFile && it.name.startsWith("own-backup-") && it.name.endsWith(".zip") && it.lastModified() < staleBefore) it.delete()
                }
                val scratch = File.createTempFile("own-backup-", ".zip", temporaryDirectory)
                try {
                    file.inputStream().use { raw ->
                        val input = java.io.DataInputStream(raw)
                        val magic = ByteArray(MAGIC.size); input.readFully(magic)
                        require(magic.contentEquals(MAGIC))
                        val length = input.readInt()
                        require(length in 1..MAX_HEADER_BYTES) { "Corrupt backup header" }
                        val bytes = ByteArray(length); input.readFully(bytes)
                        val header = JSONObject(bytes.toString(Charsets.UTF_8))
                        require(header.optInt("iterations", BackupCrypto.ITERATIONS) in 1..2_000_000)
                        val key = BackupCrypto.deriveKey(pass, header) ?: error("Corrupt backup header")
                        scratch.outputStream().use { out ->
                            try { BackupCrypto.decryptTo(key, input, out, MAX_CONTAINER_BYTES) }
                            catch (_: javax.crypto.AEADBadTagException) { throw BackupManager.WrongPasswordException() }
                        }
                    }
                    // The complete ciphertext was authenticated before any ZIP/JSON is parsed.
                    scratch.inputStream().use { unzip(it, includeAssets) }
                } finally { scratch.delete() }
            }
            Kind.UNKNOWN -> error("Not an OwnTV backup file")
        }
    }

    private fun validateName(name: String) {
        require(name.isNotEmpty() && !name.startsWith('/') && !name.contains('\\') &&
            name.split('/').none { it == ".." || it == "." }) { "Invalid backup entry" }
    }

    private fun readLimited(input: java.io.InputStream, limit: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size().toLong() + count <= limit) { "Backup entry exceeds size limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun unzip(input: java.io.InputStream, includeAssets: Boolean): Payload {
        var json: String? = null
        var wallpaper: Asset? = null
        val subtitles = LinkedHashMap<String, ByteArray>()
        val avatars = LinkedHashMap<String, ByteArray>()
        val names = HashSet<String>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                validateName(name)
                require(names.add(name) && names.size <= MAX_ENTRIES) { "Duplicate or excessive backup entries" }
                val asset = !entry.isDirectory && listOf(WALLPAPER_DIR, SUBTITLE_DIR, AVATAR_DIR).any {
                    name.startsWith(it) && '/' !in name.removePrefix(it)
                }
                val retain = name == JSON_ENTRY || includeAssets && asset
                val bytes = if (retain) ByteArrayOutputStream() else null
                var entryBytes = 0L
                val limit = if (name == JSON_ENTRY) MAX_JSON_BYTES else MAX_ASSET_BYTES
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    entryBytes += count; total += count
                    require(entryBytes <= limit && total <= MAX_EXPANDED_BYTES) { "Backup entry exceeds size limit" }
                    bytes?.write(buffer, 0, count)
                }
                val value = bytes?.toByteArray()
                when {
                    name == JSON_ENTRY -> json = requireNotNull(value).toString(Charsets.UTF_8)
                    value != null && name.startsWith(WALLPAPER_DIR) -> wallpaper = Asset(File(name).name, value)
                    value != null && name.startsWith(SUBTITLE_DIR) -> subtitles[File(name).name] = value
                    value != null && name.startsWith(AVATAR_DIR) -> avatars[File(name).name] = value
                }
                zip.closeEntry()
            }
        }
        return Payload(json ?: error("Not an OwnTV backup file"), wallpaper, subtitles, avatars)
    }

    internal const val MAX_JSON_BYTES = 32L * 1024 * 1024
    internal const val MAX_ASSET_BYTES = 16L * 1024 * 1024
    internal const val MAX_EXPANDED_BYTES = 64L * 1024 * 1024
    internal const val MAX_CONTAINER_BYTES = 80L * 1024 * 1024
    private const val MAX_ENTRIES = 4_096

    private fun intToBytes(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    /** Sanity bound so a corrupt length field can't make us allocate wildly. */
    private const val MAX_HEADER_BYTES = 8 * 1024
    private const val CONTAINER_SCHEME = "container-aes-gcm"
}

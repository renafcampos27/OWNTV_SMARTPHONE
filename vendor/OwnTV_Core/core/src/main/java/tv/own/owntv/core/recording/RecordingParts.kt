package tv.own.owntv.core.recording

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicInteger
import tv.own.owntv.core.storage.MediaTarget
import tv.own.owntv.core.timeshift.LocalSegmentStore
import tv.own.owntv.core.timeshift.LocalTimeshiftServer

/** One bounded local package on the selected volume, including SAF destinations. */
internal object RecordingParts {
    fun isIndex(target: MediaTarget) = target.displayName == RecordingPartsPlan.INDEX_NAME
    fun spoolTarget(target: MediaTarget): MediaTarget = if (target is MediaTarget.Path && isIndex(target))
        MediaTarget.Path(File(target.file.parentFile!!.parentFile, "capture.ts")) else target

    private class Directory(val context: Context?, val path: File? = null, val uri: Uri? = null) {
        data class Item(val name: String, val bytes: Long, val file: Boolean, val target: MediaTarget)
        fun children(): List<Item> {
            if (path != null) return (path.listFiles() ?: throw java.io.IOException("Recording parts listing unavailable")).map {
                Item(it.name, it.length(), it.isFile && !Files.isSymbolicLink(it.toPath()), MediaTarget.Path(it))
            }
            val parent = requireNotNull(uri)
            val resolver = requireNotNull(context).contentResolver
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, DocumentsContract.getDocumentId(parent))
            return resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { rows ->
                buildList { while (rows.moveToNext()) add(Item(rows.getString(1), rows.getLong(2).coerceAtLeast(0),
                    rows.getString(3) != DocumentsContract.Document.MIME_TYPE_DIR,
                    MediaTarget.Document(requireNotNull(context), DocumentsContract.buildDocumentUriUsingTree(parent, rows.getString(0))))) }
            } ?: throw java.io.IOException("Recording parts listing unavailable")
        }
        fun child(name: String, create: Boolean = false): MediaTarget? {
            require(name == RecordingPartsPlan.INDEX_NAME || name == "init.mp4" ||
                name.matches(Regex("part-[0-9]{6}\\.(ts|m4s)")))
            if (path != null) {
                val child = File(path, name)
                require(!Files.isSymbolicLink(child.toPath()))
                return MediaTarget.Path(child)
            }
            val parent = requireNotNull(uri)
            val id = DocumentsContract.getDocumentId(parent)
            val resolver = requireNotNull(context).contentResolver
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(parent, id)
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { rows ->
                while (rows.moveToNext()) if (rows.getString(1) == name)
                    return MediaTarget.Document(requireNotNull(context), DocumentsContract.buildDocumentUriUsingTree(parent, rows.getString(0)))
            }
            if (!create) return null
            val created = DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name) ?: return null
            return MediaTarget.Document(requireNotNull(context), created)
        }
        fun remove(): Boolean {
            if (children().isNotEmpty()) return false
            return if (path != null) path.delete()
                else runCatching { DocumentsContract.deleteDocument(requireNotNull(context).contentResolver, uri!!) }.getOrDefault(false)
        }
    }
    private fun directory(context: Context?, target: MediaTarget, id: Long? = null, create: Boolean = true): Directory? {
        if (target is MediaTarget.Path) {
            val path = if (isIndex(target)) target.file.parentFile!! else File(target.file.parentFile, ".owntv-parts-${requireNotNull(id)}")
            require(path.name.matches(Regex("\\.owntv-parts-[0-9]+")))
            if (id != null) require(path.name == ".owntv-parts-$id")
            require(!Files.isSymbolicLink(path.toPath()))
            if (!create && !path.exists()) return null
            check(path.isDirectory || path.mkdirs())
            return Directory(context, path = path)
        }
        val document = target as MediaTarget.Document
        val documentId = DocumentsContract.getDocumentId(document.uri)
        val parentId = if ('/' in documentId) documentId.substringBeforeLast('/') else documentId.substringBefore(':') + ":"
        val parent = DocumentsContract.buildDocumentUriUsingTree(document.uri, parentId)
        if (isIndex(target)) {
            val folderName = parentId.substringAfterLast('/').substringAfter(':')
            require(folderName.matches(Regex("\\.owntv-parts-[0-9]+")))
            if (id != null) require(folderName == ".owntv-parts-$id")
            if (!create && androidx.documentfile.provider.DocumentFile.fromSingleUri(requireNotNull(context), parent)?.exists() != true) return null
            return Directory(context, uri = parent)
        }
        if (!create) return null
        val name = ".owntv-parts-${requireNotNull(id)}"
        requireNotNull(context).contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(parent, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { rows ->
            while (rows.moveToNext()) if (rows.getString(1) == name)
                return Directory(context, uri = DocumentsContract.buildDocumentUriUsingTree(parent, rows.getString(0)))
        }
        val created = DocumentsContract.createDocument(requireNotNull(context).contentResolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw java.io.IOException("Recording parts destination unavailable")
        return Directory(context, uri = created)
    }
    private fun read(index: MediaTarget): RecordingPartsPlan.Index {
        require(index.length() in 1..4L * 1024 * 1024)
        val text = index.openInput().use { input ->
            val bytes = input.readBytesLimited(4 * 1024 * 1024)
            bytes.toString(Charsets.UTF_8)
        }
        return RecordingPartsPlan.decode(text)
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val n = read(buffer); if (n < 0) break
            require(out.size() + n <= limit); out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
    private fun write(target: MediaTarget, sources: List<Pair<File, String>>, expected: Long) {
        target.openOutput(false).use { output ->
            sources.forEach { (file, expectedHash) ->
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val count = input.read(buffer); if (count < 0) break
                        digest.update(buffer, 0, count); output.write(buffer, 0, count)
                    }
                }
                check(digest.digest().joinToString("") { "%02x".format(it) } == expectedHash) { "Recording segment damaged" }
            }
            output.flush()
            if (output is FileOutputStream) output.fd.sync()
        }
        check(target.length() == expected)
    }
    suspend fun finish(context: Context?, target: MediaTarget, id: Long, session: HlsCaptureSession,
        onCommitted: (Long) -> Unit = {},
        reserve: suspend (Long) -> Unit): Pair<MediaTarget, Long> {
        val dir = requireNotNull(directory(context, target, id))
        val groups = RecordingPartsPlan.groups(session.entries)
        require(groups.isNotEmpty())
        require(dir.children().all(::owned))
        val format = session.container ?: error("Recording container missing")
        require(format in listOf("ts", "fmp4"))
        val descriptor = session.checkpoint() + session.entries.joinToString { "${it.sequence}:${it.hash}:${it.durationMs}" }
        val identity = MessageDigest.getInstance("SHA-256").digest(descriptor.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val existing = dir.child(RecordingPartsPlan.INDEX_NAME)
        val previous = existing?.takeIf { it.exists() }?.let { runCatching { read(it) }.getOrNull() }
        if (previous != null) {
            check(previous.identity == identity) { "Recording parts identity changed" }
            validate(dir, previous)
            return requireNotNull(existing) to previous.bytes
        }
        // Unpublished output can be rebuilt from the authoritative source spool; remove stale tails.
        for (item in dir.children()) check(item.target.delete() || !item.target.exists())
        val initBytes = if (format == "fmp4") session.initFile.length() else 0L
        if (format == "fmp4") {
            check(initBytes in 1..RecordingPartsPlan.MAX_PART_BYTES)
            reserve(initBytes)
            write(dir.child("init.mp4", true)!!, listOf(session.initFile to requireNotNull(session.initHash)), initBytes)
            onCommitted(initBytes)
        }
        var written = initBytes
        val parts = groups.mapIndexed { index, group ->
            val bytes = group.sumOf { it.bytes }
            val name = "part-${index.toString().padStart(6, '0')}.${if (format == "fmp4") "m4s" else "ts"}"
            reserve(written + bytes)
            write(dir.child(name, true)!!, group.map { File(session.directory, it.name) to it.hash }, bytes)
            written += bytes
            onCommitted(written)
            val previousTail = groups.getOrNull(index - 1)?.last()
            RecordingPartsPlan.Part(name, bytes, group.sumOf { it.durationMs }, previousTail?.let {
                group.first().sequence != it.sequence + 1 || group.first().discontinuity != it.discontinuity
            } == true)
        }
        val manifest = RecordingPartsPlan.Index(identity, format, initBytes, parts)
        val index = dir.child(RecordingPartsPlan.INDEX_NAME, true)!!
        val payload = manifest.encode().toByteArray(Charsets.UTF_8)
        require(payload.size <= 4 * 1024 * 1024)
        if (index is MediaTarget.Path) {
            val scratch = File(index.file.parentFile, "index.pending")
            FileOutputStream(scratch).use { it.write(payload); it.fd.sync() }
            Files.move(scratch.toPath(), index.file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else index.openOutput(false).use { output -> output.write(payload); output.flush(); if (output is FileOutputStream) output.fd.sync() }
        check(read(index) == manifest)
        return index to manifest.bytes
    }
    private fun validate(dir: Directory, index: RecordingPartsPlan.Index) {
        index.parts.forEach { check(dir.child(it.name)?.length() == it.bytes) { "Recording part missing" } }
        if (index.initBytes > 0) check(dir.child("init.mp4")?.length() == index.initBytes)
    }
    fun confirmedBytes(context: Context?, target: MediaTarget, id: Long): Long {
        require(isIndex(target))
        val dir = requireNotNull(directory(context, target, id, create = false))
        val index = read(target)
        validate(dir, index)
        return index.bytes
    }
    fun openPlayback(context: Context?, target: MediaTarget, readers: AtomicInteger): RecordingPlayback {
        val dir = requireNotNull(directory(context, target))
        val index = read(target)
        validate(dir, index)
        readers.incrementAndGet()
        try {
            val server = LocalTimeshiftServer(index::playlist) { number ->
                val part = index.parts.getOrNull(number.toInt())?.takeIf { number >= 0 && number < index.parts.size }
                val name = if (number == -1L && index.initBytes > 0) "init.mp4" else part?.name
                val media = name?.let { dir.child(it) }
                val stream = runCatching { media?.openInput() }.getOrNull()
                val file = stream as? FileInputStream
                if (file == null) { stream?.close(); null } else {
                    readers.incrementAndGet()
                    LocalSegmentStore.Reader(file, if (number == -1L) index.initBytes else part!!.bytes,
                        if (index.container == "fmp4") "video/mp4" else "video/mp2t") { readers.decrementAndGet() }
                }
            }
            return RecordingPlayback(server.url) { server.close(); readers.decrementAndGet() }
        } catch (error: Exception) { readers.decrementAndGet(); throw error }
    }
    /** Metadata only, used on the IO dispatcher for an interrupted unpublished package. */
    fun stagedBytes(context: Context?, target: MediaTarget, id: Long): Long {
        if (isIndex(target)) return 0
        val dir = directory(context, target, id, create = false) ?: return 0
        return dir.children().sumOf { it.bytes }
    }
    private fun owned(item: Directory.Item) = item.file && (item.name == RecordingPartsPlan.INDEX_NAME ||
        item.name == "index.pending" || item.name == "init.mp4" || item.name.matches(Regex("part-[0-9]{6}\\.(ts|m4s)")))
    /** Also removes an interrupted package; retries tolerate already removed parts/index. */
    fun delete(context: Context?, target: MediaTarget, id: Long): Boolean {
        val dir = directory(context, target, id, create = false) ?: return true
        val items = dir.children()
        if (!items.all(::owned)) return false
        for (item in items.sortedBy { it.name == RecordingPartsPlan.INDEX_NAME }) {
            if (!item.target.delete() && item.target.exists()) return false
        }
        return dir.remove()
    }
}

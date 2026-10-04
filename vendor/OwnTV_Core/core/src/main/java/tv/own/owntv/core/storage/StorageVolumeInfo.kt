package tv.own.owntv.core.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import java.io.File

/** Physical destination identity; a content URI is not evidence that a volume is removable. */
internal data class StorageVolumeInfo(val key: String, val root: File, val removable: Boolean,
    val free: Long, val total: Long, val maxFileBytes: Long) {
    companion object {
        fun resolve(context: Context, stored: String): StorageVolumeInfo? = runCatching {
            val file = if (MediaTarget.isDocument(stored)) {
                val uri = Uri.parse(stored)
                if (uri.authority != "com.android.externalstorage.documents") return null
                val id = runCatching { DocumentsContract.getDocumentId(uri) }
                    .getOrElse { DocumentsContract.getTreeDocumentId(uri) }
                DocumentVolumes.dirOf(context, DocumentVolumes.volumeIdOf(id)) ?: return null
            } else File(stored)
            val dir = if (file.isDirectory) file else file.parentFile ?: return null
            val manager = context.getSystemService(StorageManager::class.java)
            val volume = manager.getStorageVolume(dir)
            // An unknown /storage mount must never be charged to internal storage.
            if (volume == null && !dir.canonicalPath.startsWith(Environment.getDataDirectory().canonicalPath + File.separator)) return null
            val removable = volume?.isRemovable == true
            val root = if (removable) context.getExternalFilesDirs(null).filterNotNull()
                .mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile }
                .firstOrNull { dir.absolutePath == it.absolutePath || dir.absolutePath.startsWith(it.absolutePath + File.separator) }
                ?: return null else Environment.getDataDirectory()
            if (!root.exists() || (volume != null && volume.state != Environment.MEDIA_MOUNTED)) return null
            val stat = android.os.StatFs(root.absolutePath)
            val type = runCatching {
                File("/proc/mounts").readLines().map { it.split(' ') }.filter { it.size >= 3 }.let { mounts ->
                    // /storage may be a FUSE view; inspect its physical USB mount too.
                    mounts.firstOrNull { removable && it[1] == "/mnt/media_rw/${root.name}" }?.get(2)
                        ?: mounts.filter { dir.absolutePath == it[1] || dir.absolutePath.startsWith(it[1] + "/") }
                            .maxByOrNull { it[1].length }?.get(2)
                }
            }.getOrNull()
            StorageVolumeInfo(if (removable) volume.uuid ?: root.absolutePath else "internal", root, removable,
                stat.availableBytes, stat.totalBytes, if (type == "vfat" || type == "msdos") 0xffff_ffffL else Long.MAX_VALUE)
        }.getOrNull()
    }
}

package tv.own.owntv.core.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

enum class MediaAvailability { AVAILABLE, MISSING, STORAGE_UNAVAILABLE, PERMISSION_DENIED, UNREADABLE }

/** Read-only: never creates a folder, changes a grant or substitutes another destination. Run on IO. */
object MediaAvailabilityProbe {
    fun inspect(context: Context, stored: String?, verifyReadable: Boolean = false): MediaAvailability {
        if (stored.isNullOrBlank()) return MediaAvailability.MISSING
        return try {
            if (MediaTarget.isDocument(stored)) {
                val uri = Uri.parse(stored)
                if (uri.authority == "com.android.externalstorage.documents") {
                    val root = DocumentVolumes.dirOf(context, DocumentVolumes.volumeIdOf(DocumentsContract.getDocumentId(uri)))
                    if (root == null || !root.exists()) return MediaAvailability.STORAGE_UNAVAILABLE
                }
                context.contentResolver.query(uri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                    if (!it.moveToFirst()) return MediaAvailability.MISSING
                } ?: return MediaAvailability.UNREADABLE
                if (verifyReadable) {
                    context.contentResolver.openFileDescriptor(uri, "r")?.use { }
                        ?: return MediaAvailability.UNREADABLE
                }
                MediaAvailability.AVAILABLE
            } else {
                val file = File(stored)
                if (!file.exists() && (stored.startsWith("/storage/") || stored.startsWith("/mnt/")) &&
                    StorageVolumeInfo.resolve(context, stored) == null) return MediaAvailability.STORAGE_UNAVAILABLE
                inspectPath(file, verifyReadable)
            }
        } catch (_: SecurityException) {
            MediaAvailability.PERMISSION_DENIED
        } catch (_: FileNotFoundException) {
            MediaAvailability.MISSING
        } catch (_: Exception) {
            MediaAvailability.UNREADABLE
        }
    }

    internal fun inspectPath(file: File, verifyReadable: Boolean = false): MediaAvailability {
        if (!file.exists()) {
            val parent = generateSequence(file.parentFile) { it.parentFile }.firstOrNull { it.exists() }
            return if (parent != null && !parent.canRead()) MediaAvailability.PERMISSION_DENIED else MediaAvailability.MISSING
        }
        if (!file.isFile) return MediaAvailability.UNREADABLE
        if (!file.canRead()) return MediaAvailability.PERMISSION_DENIED
        if (verifyReadable) file.inputStream().use { }
        return MediaAvailability.AVAILABLE
    }

    fun text(availability: MediaAvailability, res: android.content.res.Resources): String? = when (availability) {
        MediaAvailability.AVAILABLE -> null
        MediaAvailability.MISSING -> res.getString(tv.own.owntv.core.R.string.media_file_missing)
        MediaAvailability.STORAGE_UNAVAILABLE -> res.getString(tv.own.owntv.core.R.string.media_storage_offline)
        MediaAvailability.PERMISSION_DENIED -> res.getString(tv.own.owntv.core.R.string.media_read_permission)
        MediaAvailability.UNREADABLE -> res.getString(tv.own.owntv.core.R.string.media_file_unreadable)
    }
}

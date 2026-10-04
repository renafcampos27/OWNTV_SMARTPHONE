package tv.own.owntv.mobile.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * The system's "save as" dialog, asking for a grant that **outlives the process**.
 *
 * [ActivityResultContracts.CreateDocument] on its own returns a URI the app may read and write until
 * it next starts, which is all a copy ever needed. Export is not a copy any more: the item's stored
 * location follows the file to where the user put it, so the app has to still be able to open that
 * file next week. Without `FLAG_GRANT_PERSISTABLE_URI_PERMISSION` on the request,
 * `takePersistableUriPermission` is refused and the row would end up pointing at something
 * unplayable — worse than the copy it replaced.
 *
 * Everything else is the contract's own behaviour, including the caller-supplied filename.
 */
class ExportDocument(private val mimeType: String) : ActivityResultContracts.CreateDocument(mimeType) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

package tv.own.owntv.mobile.backup

import android.content.Context
import org.json.JSONObject
import tv.own.owntv.core.backup.BackupAppSettings
import tv.own.owntv.mobile.ui.screens.settings.SimpleModeBackupCodec
import tv.own.owntv.mobile.ui.screens.settings.SimpleModeOptions
import tv.own.owntv.mobile.ui.screens.settings.SimpleModePreferences

/** Portable mobile modes travel with the Core's selective, encrypted settings backup. */
class MobileBackupAppSettings(private val context: Context) : BackupAppSettings {
    override suspend fun export(): JSONObject = JSONObject()
        .put(SimpleModeBackupCodec.KEY, SimpleModePreferences.exportBackup(context))

    override fun validate(data: JSONObject) {
        if (data.has(SimpleModeBackupCodec.KEY)) {
            SimpleModeBackupCodec.decode(data.getJSONObject(SimpleModeBackupCodec.KEY), SimpleModeOptions())
        }
    }

    override suspend fun restore(data: JSONObject) {
        if (data.has(SimpleModeBackupCodec.KEY)) {
            SimpleModePreferences.restoreBackup(context, data.getJSONObject(SimpleModeBackupCodec.KEY))
        }
    }
}

package tv.own.owntv.core.backup

import org.json.JSONObject

/** Host-owned portable preferences, carried by every SETTINGS export/restore (file or LAN).
 * Device permissions and transient runtime state do not belong in this payload. */
interface BackupAppSettings {
    suspend fun export(): JSONObject
    /** Validate before the manager writes any restored database or preference data. */
    fun validate(data: JSONObject)
    suspend fun restore(data: JSONObject)

    object None : BackupAppSettings {
        override suspend fun export() = JSONObject()
        override fun validate(data: JSONObject) = Unit
        override suspend fun restore(data: JSONObject) = Unit
    }
}

/** Absence in an older file means preserve the device values, never reset to defaults. */
object BackupAppSettingsPayload {
    private const val KEY = "appSettings"

    suspend fun exportInto(root: JSONObject, selected: Boolean, settings: BackupAppSettings) {
        if (selected) settings.export().takeIf { it.length() > 0 }?.let { root.put(KEY, it) }
    }

    fun readAndValidate(root: JSONObject, selected: Boolean, settings: BackupAppSettings): JSONObject? {
        if (!selected || !root.has(KEY)) return null
        val data = root.getJSONObject(KEY)
        settings.validate(data)
        return data
    }

    /** Count known leaf settings, independent of JSON member order; unknown host fields are ignored. */
    fun changedValues(incoming: JSONObject, current: JSONObject): Int {
        var count = 0
        incoming.keys().forEach { key ->
            if (!current.has(key)) return@forEach
            val value = incoming.get(key)
            val old = current.get(key)
            count += if (value is JSONObject && old is JSONObject) changedValues(value, old)
                else if (value.toString() == old.toString()) 0 else 1
        }
        return count
    }

    fun isPresent(root: JSONObject) = root.optJSONObject(KEY)?.length()?.let { it > 0 } == true
}

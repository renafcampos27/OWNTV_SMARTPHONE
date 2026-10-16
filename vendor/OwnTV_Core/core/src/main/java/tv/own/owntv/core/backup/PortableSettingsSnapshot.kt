package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject

/** New snapshots identify implicit portable choices; old backups keep their merge semantics. */
internal object PortableSettingsSnapshot {
    private const val VERSION = "portableSettingsSnapshotVersion"
    private const val UNSET = "unsetPortableKeys"

    fun write(output: JSONObject, managedKeys: Set<String>) {
        output.put(VERSION, 1)
        output.put(UNSET, JSONArray(managedKeys.filterNot(output::has).sorted()))
    }

    fun validate(input: JSONObject, managedKeys: Set<String>) { unsetKeys(input, managedKeys) }

    fun unsetKeys(input: JSONObject, managedKeys: Set<String>): Set<String> {
        if (!input.has(VERSION) && !input.has(UNSET)) return emptySet()
        BackupValueValidator.whole(input.opt(VERSION), "settings.$VERSION", 1, 1)
        val values = BackupValueValidator.array(input.opt(UNSET), "settings.$UNSET")
        if (values.length() > managedKeys.size) BackupValueValidator.invalid("settings.$UNSET")
        val keys = LinkedHashSet<String>()
        for (index in 0 until values.length()) {
            val key = BackupValueValidator.text(values.get(index), "settings.$UNSET[$index]")!!
            if (key !in managedKeys || input.has(key) || !keys.add(key))
                BackupValueValidator.invalid("settings.$UNSET[$index]")
        }
        return keys
    }
}

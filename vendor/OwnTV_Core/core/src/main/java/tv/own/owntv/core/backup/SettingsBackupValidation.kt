package tv.own.owntv.core.backup

import org.json.JSONObject

/** Unknown optional keys remain forward compatible; known keys must match their storage types. */
internal object SettingsBackupValidation {
    fun validate(input: JSONObject, strings: Set<String>, integers: Set<String>, flags: Set<String>,
        floats: Set<String>, stringSets: Set<String>, ranges: Map<String, LongRange>) {
        input.keys().forEach { key ->
            val value = input.get(key)
            val path = "settings.$key"
            when (key) {
                in strings -> BackupValueValidator.text(value, path)
                in integers -> {
                    val bounds = ranges[key] ?: (Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong())
                    BackupValueValidator.whole(value, path, bounds.first, bounds.last)
                }
                in flags -> BackupValueValidator.flag(value, path)
                in floats -> BackupValueValidator.finite(value, path)
                in stringSets -> {
                    val values = BackupValueValidator.array(value, path)
                    for (i in 0 until values.length()) BackupValueValidator.text(values.get(i), "$path[$i]")
                }
            }
        }
    }
}

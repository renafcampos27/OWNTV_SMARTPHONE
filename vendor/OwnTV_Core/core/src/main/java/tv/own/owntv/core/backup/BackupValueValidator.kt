package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject

/** Validation errors identify a field, never its possibly secret value. */
internal object BackupValueValidator {
    fun invalid(path: String): Nothing = throw IllegalArgumentException("Invalid backup field: $path")

    fun text(value: Any?, path: String, nullable: Boolean = false): String? {
        if (nullable && value == JSONObject.NULL) return null
        return value as? String ?: invalid(path)
    }

    fun flag(value: Any?, path: String) { if (value !is Boolean) invalid(path) }

    fun whole(value: Any?, path: String, minimum: Long = Long.MIN_VALUE, maximum: Long = Long.MAX_VALUE): Long {
        val number = if (value is Number) runCatching { value.toString().toBigDecimal().longValueExact() }.getOrNull() else null
        return number?.takeIf { it in minimum..maximum } ?: invalid(path)
    }

    fun finite(value: Any?, path: String) {
        if (value !is Number || !value.toDouble().isFinite() || kotlin.math.abs(value.toDouble()) > Float.MAX_VALUE) invalid(path)
    }

    fun obj(value: Any?, path: String): JSONObject = value as? JSONObject ?: invalid(path)
    fun array(value: Any?, path: String): JSONArray = value as? JSONArray ?: invalid(path)

    fun optionalText(o: JSONObject, key: String, path: String, nullable: Boolean = false) {
        if (o.has(key)) text(o.get(key), "$path.$key", nullable)
    }

    fun optionalWhole(o: JSONObject, key: String, path: String, minimum: Long = Long.MIN_VALUE,
        maximum: Long = Long.MAX_VALUE, nullable: Boolean = false) {
        if (o.has(key) && !(nullable && o.isNull(key))) whole(o.get(key), "$path.$key", minimum, maximum)
    }

    fun optionalFlag(o: JSONObject, key: String, path: String) { if (o.has(key)) flag(o.get(key), "$path.$key") }

    fun rows(root: JSONObject, key: String, check: (JSONObject, String) -> Unit) {
        if (!root.has(key)) return
        val values = array(root.get(key), key)
        for (i in 0 until values.length()) check(obj(values.get(i), "$key[$i]"), "$key[$i]")
    }

    fun map(root: JSONObject, key: String, check: (Any, String) -> Unit) {
        if (!root.has(key)) return
        val values = obj(root.get(key), key)
        // Map keys can themselves be legacy URLs; do not include them in diagnostic messages.
        values.keys().asSequence().forEachIndexed { index, field -> check(values.get(field), "$key[$index]") }
    }
}

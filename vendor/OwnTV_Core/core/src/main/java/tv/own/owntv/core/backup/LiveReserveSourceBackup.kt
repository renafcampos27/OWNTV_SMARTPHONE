package tv.own.owntv.core.backup

import org.json.JSONObject
import tv.own.owntv.core.database.entity.FOLLOW_GLOBAL_RESERVE_SECS
import tv.own.owntv.core.database.entity.SourceEntity

/** v25 reserve fields, including the combined delay/reserve choice carried by older backups. */
internal object LiveReserveSourceBackup {
    fun writeSource(out: JSONObject, source: SourceEntity) {
        out.put("liveReserveMode", source.liveReserveMode ?: JSONObject.NULL)
        out.put("liveReserveCustomSecs", source.liveReserveCustomSecs)
        out.put("liveReserveExtraSecs", source.liveReserveExtraSecs)
    }

    /** Missing fields preserve the target row; explicit null restores global inheritance. */
    fun readSource(input: JSONObject, base: SourceEntity): SourceEntity {
        val hasIndependentReserve = input.has("liveReserveMode") || input.has("liveReserveCustomSecs") || input.has("liveReserveExtraSecs")
        val modeKey = if (hasIndependentReserve) "liveReserveMode" else "liveLatencyMode"
        val customKey = if (hasIndependentReserve) "liveReserveCustomSecs" else "liveLatencyCustomSecs"
        val mode = if (input.has(modeKey)) {
            if (input.isNull(modeKey)) null else input.optString(modeKey).takeIf { it.isNotBlank() }
        } else base.liveReserveMode
        // An explicit inherit choice resets the whole override, including omitted or dormant fields.
        if (input.has(modeKey) && mode == null) {
            return base.copy(liveReserveMode = null, liveReserveCustomSecs = FOLLOW_GLOBAL_RESERVE_SECS,
                liveReserveExtraSecs = FOLLOW_GLOBAL_RESERVE_SECS)
        }
        val custom = if (input.has(customKey)) input.optInt(customKey, FOLLOW_GLOBAL_RESERVE_SECS) else base.liveReserveCustomSecs
        val extra = when {
            input.has("liveReserveExtraSecs") -> input.optInt("liveReserveExtraSecs", FOLLOW_GLOBAL_RESERVE_SECS)
                .let { if (it == FOLLOW_GLOBAL_RESERVE_SECS) it else it.coerceIn(0, 10) }
            !hasIndependentReserve && input.has("liveLatencyMode") -> 2
            else -> base.liveReserveExtraSecs
        }
        return base.copy(liveReserveMode = mode, liveReserveCustomSecs = custom, liveReserveExtraSecs = extra)
    }
}

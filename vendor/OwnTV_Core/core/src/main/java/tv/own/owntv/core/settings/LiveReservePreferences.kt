package tv.own.owntv.core.settings

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/** Reserve durations published together so a tune cannot combine different preference edits. */
data class LiveReserveConfiguration(val seconds: Int?, val extraSecs: Int)

/** Preserve the old combined buffer choice once, before delay and reserve can diverge. */
internal object LiveReservePreferences {
    val MODE = stringPreferencesKey("live_reserve_mode")
    val CUSTOM_SECS = intPreferencesKey("live_reserve_custom_secs")
    val EXTRA_SECS = intPreferencesKey("live_reserve_extra_secs")
    private val legacyModeKey = stringPreferencesKey("live_latency_mode")
    private val legacyCustom = intPreferencesKey("live_latency_custom_secs")

    fun mode(prefs: Preferences, legacyMode: String): String =
        LiveLatency.fromName(prefs[MODE] ?: prefs[legacyModeKey] ?: legacyMode).name

    fun customSecs(prefs: Preferences): Int = LiveBuffer.clampCustom(
        prefs[CUSTOM_SECS] ?: prefs[legacyCustom] ?: LiveBuffer.CUSTOM_DEFAULT,
    )

    fun extraSecs(prefs: Preferences): Int = (prefs[EXTRA_SECS] ?: 2).coerceIn(0, 10)

    fun configuration(prefs: Preferences, legacyMode: String): LiveReserveConfiguration =
        LiveReserveConfiguration(
            LiveBuffer.effectiveSeconds(LiveLatency.fromName(mode(prefs, legacyMode)), customSecs(prefs)),
            extraSecs(prefs),
        )

    fun materialize(prefs: MutablePreferences, legacyMode: String) {
        val chosenMode = mode(prefs, legacyMode)
        val chosenCustom = customSecs(prefs)
        if (prefs[MODE] == null) prefs[MODE] = chosenMode
        if (prefs[CUSTOM_SECS] == null) prefs[CUSTOM_SECS] = chosenCustom
    }

    fun setMode(prefs: MutablePreferences, name: String, legacyMode: String) {
        materialize(prefs, legacyMode)
        val selected = LiveLatency.fromName(name)
        prefs[MODE] = selected.name
        if (selected != LiveLatency.CUSTOM) prefs[EXTRA_SECS] = LiveBuffer.DEFAULT_EXTRA_SECS
    }

    fun setRange(prefs: MutablePreferences, minimumSecs: Int, maximumSecs: Int, legacyMode: String) {
        materialize(prefs, legacyMode)
        val minimum = LiveBuffer.clampCustom(minimumSecs)
        prefs[CUSTOM_SECS] = minimum
        prefs[EXTRA_SECS] = LiveBuffer.clampExtra(maximumSecs - minimum)
        prefs[MODE] = LiveLatency.CUSTOM.name
    }

    /** Apply only fields supplied by a backup; legacy combined fields also seed the reserve. */
    fun importBackup(prefs: MutablePreferences, input: org.json.JSONObject, legacyMode: String) {
        materialize(prefs, legacyMode)
        val hasIndependentReserve = input.has(MODE.name) || input.has(CUSTOM_SECS.name) || input.has(EXTRA_SECS.name)
        val modeKey = if (hasIndependentReserve) MODE.name else legacyModeKey.name
        if (input.has(modeKey)) prefs[MODE] = LiveLatency.fromName(input.getString(modeKey)).name
        val customKey = if (hasIndependentReserve) CUSTOM_SECS.name else legacyCustom.name
        if (input.has(customKey)) prefs[CUSTOM_SECS] = LiveBuffer.clampCustom(input.getInt(customKey))
        if (input.has(EXTRA_SECS.name)) prefs[EXTRA_SECS] = input.getInt(EXTRA_SECS.name).coerceIn(0, 10)
        else if (!hasIndependentReserve && input.has(legacyModeKey.name)) prefs[EXTRA_SECS] = LiveBuffer.DEFAULT_EXTRA_SECS
    }
}

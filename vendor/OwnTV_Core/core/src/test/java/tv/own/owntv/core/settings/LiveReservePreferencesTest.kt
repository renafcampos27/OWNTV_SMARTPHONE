package tv.own.owntv.core.settings

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveReservePreferencesTest {
    private val delayMode = stringPreferencesKey("live_latency_mode")
    private val delayCustom = intPreferencesKey("live_latency_custom_secs")

    @Test fun `legacy custom reserve survives startup reset and later delay changes`() {
        val prefs = mutablePreferencesOf(delayMode to "CUSTOM", delayCustom to 23)
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "BALANCED"))
        LiveReservePreferences.materialize(prefs, "BALANCED")
        prefs[delayMode] = "BALANCED"
        prefs[delayCustom] = 5
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "BALANCED"))
        assertEquals(23, LiveReservePreferences.customSecs(prefs))
    }

    @Test fun `every legacy preset and dormant custom value survive materialization`() {
        for (mode in LiveLatency.entries) {
            val prefs = mutablePreferencesOf(delayMode to mode.name, delayCustom to 31)
            LiveReservePreferences.materialize(prefs, "BALANCED")
            prefs[delayMode] = "LOW"
            prefs[delayCustom] = 4
            assertEquals(mode.name, LiveReservePreferences.mode(prefs, "LOW"))
            assertEquals(31, LiveReservePreferences.customSecs(prefs))
        }
    }

    @Test fun `reserve changes do not mutate requested delay`() {
        val prefs = mutablePreferencesOf(delayMode to "LOW", delayCustom to 7)
        LiveReservePreferences.materialize(prefs, "LOW")
        prefs[LiveReservePreferences.MODE] = "CUSTOM"
        prefs[LiveReservePreferences.CUSTOM_SECS] = 8
        prefs[LiveReservePreferences.EXTRA_SECS] = 4
        assertEquals("LOW", prefs[delayMode])
        assertEquals(7, prefs[delayCustom])
        assertEquals(4, LiveReservePreferences.extraSecs(prefs))
    }

    @Test fun `old backup restores its combined reserve over existing independent settings`() {
        val prefs = mutablePreferencesOf(
            LiveReservePreferences.MODE to "LOW", LiveReservePreferences.CUSTOM_SECS to 3,
            LiveReservePreferences.EXTRA_SECS to 4,
        )
        LiveReservePreferences.importBackup(prefs, JSONObject()
            .put(delayMode.name, "CUSTOM").put(delayCustom.name, 12), "BALANCED")
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "BALANCED"))
        assertEquals(12, LiveReservePreferences.customSecs(prefs))
        assertEquals(2, LiveReservePreferences.extraSecs(prefs))
    }

    @Test fun `new backup preserves distinct choices while missing keys preserve target reserve`() {
        val prefs = mutablePreferencesOf(delayMode to "LOW", delayCustom to 5,
            LiveReservePreferences.MODE to "STABLE", LiveReservePreferences.CUSTOM_SECS to 18)
        LiveReservePreferences.importBackup(prefs, JSONObject(), "LOW")
        assertEquals("STABLE", LiveReservePreferences.mode(prefs, "LOW"))
        assertEquals(18, LiveReservePreferences.customSecs(prefs))
        LiveReservePreferences.importBackup(prefs, JSONObject()
            .put(delayMode.name, "LOW").put(delayCustom.name, 5)
            .put(LiveReservePreferences.MODE.name, "CUSTOM")
            .put(LiveReservePreferences.CUSTOM_SECS.name, 8)
            .put(LiveReservePreferences.EXTRA_SECS.name, 4), "LOW")
        assertEquals("LOW", prefs[delayMode])
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "LOW"))
        assertEquals(8, LiveReservePreferences.customSecs(prefs))
        assertEquals(4, LiveReservePreferences.extraSecs(prefs))
    }

    @Test fun `reserve extra defaults to two and imported range is bounded`() {
        val prefs = mutablePreferencesOf()
        assertEquals(2, LiveReservePreferences.extraSecs(prefs))
        LiveReservePreferences.importBackup(prefs, JSONObject().put(LiveReservePreferences.EXTRA_SECS.name, 999), "BALANCED")
        assertEquals(10, LiveReservePreferences.extraSecs(prefs))
        LiveReservePreferences.importBackup(prefs, JSONObject().put(LiveReservePreferences.EXTRA_SECS.name, -8), "BALANCED")
        assertEquals(0, LiveReservePreferences.extraSecs(prefs))
    }

    @Test fun `partial independent backup never fills omitted reserve fields from delay`() {
        val prefs = mutablePreferencesOf(
            LiveReservePreferences.MODE to "STABLE", LiveReservePreferences.CUSTOM_SECS to 18,
            LiveReservePreferences.EXTRA_SECS to 4,
        )
        LiveReservePreferences.importBackup(prefs, JSONObject()
            .put(LiveReservePreferences.MODE.name, "CUSTOM")
            .put(delayMode.name, "LOW").put(delayCustom.name, 5), "BALANCED")
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "BALANCED"))
        assertEquals(18, LiveReservePreferences.customSecs(prefs))
        assertEquals(4, LiveReservePreferences.extraSecs(prefs))
        LiveReservePreferences.importBackup(prefs, JSONObject()
            .put(LiveReservePreferences.EXTRA_SECS.name, 3)
            .put(delayMode.name, "LOW").put(delayCustom.name, 5), "BALANCED")
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "BALANCED"))
        assertEquals(18, LiveReservePreferences.customSecs(prefs))
        assertEquals(3, LiveReservePreferences.extraSecs(prefs))
    }

    @Test fun `whole range update leaves delay intact and preset resets only refill margin`() {
        val prefs = mutablePreferencesOf(delayMode to "LOW", delayCustom to 7)
        LiveReservePreferences.setRange(prefs, 8, 12, "BALANCED")
        assertEquals("CUSTOM", LiveReservePreferences.mode(prefs, "BALANCED"))
        assertEquals(8, LiveReservePreferences.customSecs(prefs))
        assertEquals(4, LiveReservePreferences.extraSecs(prefs))
        assertEquals(LiveReserveConfiguration(8, 4), LiveReservePreferences.configuration(prefs, "BALANCED"))
        assertEquals("LOW", prefs[delayMode])
        assertEquals(7, prefs[delayCustom])
        LiveReservePreferences.setMode(prefs, "STABLE", "BALANCED")
        assertEquals("STABLE", LiveReservePreferences.mode(prefs, "BALANCED"))
        assertEquals(8, LiveReservePreferences.customSecs(prefs))
        assertEquals(2, LiveReservePreferences.extraSecs(prefs))
        assertEquals(LiveReserveConfiguration(15, 2), LiveReservePreferences.configuration(prefs, "BALANCED"))
    }

    @Test fun `range bounds are applied together without negative or excessive margin`() {
        val prefs = mutablePreferencesOf()
        LiveReservePreferences.setRange(prefs, 8, 2, "BALANCED")
        assertEquals(8, LiveReservePreferences.customSecs(prefs))
        assertEquals(0, LiveReservePreferences.extraSecs(prefs))
        LiveReservePreferences.setRange(prefs, 80, 100, "BALANCED")
        assertEquals(60, LiveReservePreferences.customSecs(prefs))
        assertEquals(10, LiveReservePreferences.extraSecs(prefs))
    }
}

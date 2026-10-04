package tv.own.owntv.mobile.ui.screens.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import org.json.JSONObject

// App-owned preferences, included in SETTINGS backups through TvBackupAppSettings.
private val Context.simpleModeStore by preferencesDataStore(name = "owntv_simple_mode")

data class SimpleModeOptions(val hideCategories: Boolean = false, val hideSidebar: Boolean = false, val channelRecovery: Boolean = true, val startOnBoot: Boolean = false, val startOnWake: Boolean = false, val recoveryTimeoutSeconds: Int = RecoveryTimeout.DEFAULT_SECONDS)

object SimpleModePreferences {

    private val categories = booleanPreferencesKey("hide_categories")
    private val sidebar = booleanPreferencesKey("hide_sidebar")

    private val recoveryTimeout = intPreferencesKey("channel_recovery_timeout_seconds")
    private val recovery = booleanPreferencesKey("channel_recovery")
    private val boot = booleanPreferencesKey("start_on_boot")
    private val wake = booleanPreferencesKey("start_on_wake")

    suspend fun setRecoveryTimeout(context: Context, seconds: Int) {
        context.applicationContext.simpleModeStore.edit { it[recoveryTimeout] = RecoveryTimeout.normalize(seconds) }
    }

    suspend fun setChannelRecovery(context: Context, enabled: Boolean) { context.applicationContext.simpleModeStore.edit { it[recovery] = enabled } }
    suspend fun setStartOnBoot(context: Context, enabled: Boolean) { context.applicationContext.simpleModeStore.edit { it[boot] = enabled } }
    suspend fun setStartOnWake(context: Context, enabled: Boolean) { context.applicationContext.simpleModeStore.edit { it[wake] = enabled } }

    private fun options(prefs: Preferences) = SimpleModeOptions(
        hideCategories = prefs[categories] ?: false,
        hideSidebar = prefs[sidebar] ?: false,
        channelRecovery = prefs[recovery] ?: true,
        startOnBoot = prefs[boot] ?: false,
        startOnWake = prefs[wake] ?: false,
        recoveryTimeoutSeconds = RecoveryTimeout.normalize(prefs[recoveryTimeout]),
    )

    fun observe(context: Context) = context.applicationContext.simpleModeStore.data.map(::options)

    suspend fun exportBackup(context: Context): JSONObject = exportBackup(context.applicationContext.simpleModeStore)
    suspend fun restoreBackup(context: Context, data: JSONObject) = restoreBackup(context.applicationContext.simpleModeStore, data)

    internal suspend fun exportBackup(store: DataStore<Preferences>): JSONObject = SimpleModeBackupCodec.encode(options(store.data.first()))

    internal suspend fun restoreBackup(store: DataStore<Preferences>, data: JSONObject) {
        // Decode all fields before changing any key; edit commits all six values atomically.
        store.edit { prefs ->
            val restored = SimpleModeBackupCodec.decode(data, options(prefs))
            prefs[categories] = restored.hideCategories
            prefs[sidebar] = restored.hideSidebar
            prefs[recovery] = restored.channelRecovery
            prefs[recoveryTimeout] = restored.recoveryTimeoutSeconds
            prefs[boot] = false
            prefs[wake] = false
        }
    }

    suspend fun setHideCategories(context: Context, hidden: Boolean) {
        context.applicationContext.simpleModeStore.edit { it[categories] = hidden }
    }

    suspend fun setHideSidebar(context: Context, hidden: Boolean) {
        context.applicationContext.simpleModeStore.edit { it[sidebar] = hidden }
    }
}

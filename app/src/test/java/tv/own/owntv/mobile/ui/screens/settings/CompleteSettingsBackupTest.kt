package tv.own.owntv.mobile.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.PreferencesSerializer
import androidx.datastore.core.okio.OkioStorage
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import tv.own.owntv.core.backup.BackupAppSettings
import tv.own.owntv.core.backup.BackupAppSettingsPayload

class CompleteSettingsBackupTest {
    @get:Rule val folder = TemporaryFolder()
    private val configured = SimpleModeOptions(
        hideCategories = true, hideSidebar = true, channelRecovery = false,
        startOnBoot = false, startOnWake = false, recoveryTimeoutSeconds = 17,
    )

    private class StoreSettings(private val store: DataStore<Preferences>) : BackupAppSettings {
        override suspend fun export() = JSONObject().put(SimpleModeBackupCodec.KEY, SimpleModePreferences.exportBackup(store))
        override fun validate(data: JSONObject) {
            if (data.has(SimpleModeBackupCodec.KEY)) SimpleModeBackupCodec.decode(data.getJSONObject(SimpleModeBackupCodec.KEY), SimpleModeOptions())
        }
        override suspend fun restore(data: JSONObject) {
            if (data.has(SimpleModeBackupCodec.KEY)) SimpleModePreferences.restoreBackup(store, data.getJSONObject(SimpleModeBackupCodec.KEY))
        }
    }

    private suspend fun withStore(name: String, action: suspend (DataStore<Preferences>) -> Unit) {
        val job = SupervisorJob()
        // Android FileStorage uses File.renameTo, which cannot replace an existing file on Windows.
        // Use real DataStore + the same protobuf serializer with the portable JVM filesystem here.
        val store = PreferenceDataStoreFactory.create(
            storage = OkioStorage(FileSystem.SYSTEM, PreferencesSerializer) {
                folder.root.resolve("$name.preferences_pb").absolutePath.toPath()
            },
            scope = CoroutineScope(Dispatchers.IO + job),
        )
        try { action(store) } finally { job.cancelAndJoin() }
    }

    private suspend fun read(store: DataStore<Preferences>) = SimpleModeBackupCodec.decode(SimpleModePreferences.exportBackup(store), SimpleModeOptions())

    @Test fun completeFileRoundTripRestoresEveryOptionAndPersistsAfterReopeningStore() = runBlocking {
        var file = ""
        withStore("source") { store ->
            SimpleModePreferences.restoreBackup(store, SimpleModeBackupCodec.encode(configured))
            val root = JSONObject().put("version", 23)
            BackupAppSettingsPayload.exportInto(root, true, StoreSettings(store))
            file = root.toString() // transport serialization, as in the .own payload and LAN export
        }
        withStore("destination") { store ->
            val settings = StoreSettings(store)
            val root = JSONObject(file)
            assertTrue(BackupAppSettingsPayload.isPresent(root))
            settings.restore(BackupAppSettingsPayload.readAndValidate(root, true, settings)!!)
            assertEquals(configured, read(store))
        }
        withStore("destination") { store -> assertEquals(configured, read(store)) }
    }

    @Test fun explicitDefaultsAndFalseFlagsReplacePreviouslyEnabledOptions() = runBlocking {
        withStore("defaults") { store ->
            SimpleModePreferences.restoreBackup(store, SimpleModeBackupCodec.encode(configured))
            SimpleModePreferences.restoreBackup(store, SimpleModeBackupCodec.encode(SimpleModeOptions()))
            assertEquals(SimpleModeOptions(), read(store))
        }
    }

    @Test fun oldBackupWithoutAppSettingsPreservesExistingCustomOptions() = runBlocking {
        withStore("legacy") { store ->
            SimpleModePreferences.restoreBackup(store, SimpleModeBackupCodec.encode(configured))
            val old = JSONObject("{\"version\":22,\"settings\":{}}")
            val settings = StoreSettings(store)
            val incoming = BackupAppSettingsPayload.readAndValidate(old, true, settings)
            assertNull(incoming)
            incoming?.let { settings.restore(it) }
            assertEquals(configured, read(store))
        }
    }

    @Test fun deselectedSettingsAreNeitherExportedNorRestored() = runBlocking {
        withStore("selective") { store ->
            val settings = StoreSettings(store)
            val root = JSONObject()
            BackupAppSettingsPayload.exportInto(root, false, settings)
            assertFalse(BackupAppSettingsPayload.isPresent(root))
            root.put("appSettings", "invalid but not selected")
            assertNull(BackupAppSettingsPayload.readAndValidate(root, false, settings))
            assertEquals(SimpleModeOptions(), read(store))
        }
    }

    @Test fun retiredZappingExperimentDoesNotPreventRestoringOldBackups() = runBlocking {
        withStore("retired") { store ->
            val legacy = SimpleModeBackupCodec.encode(configured).put("stopClearZapping", true)
            SimpleModePreferences.restoreBackup(store, legacy)
            assertEquals(configured, read(store))
            assertFalse(SimpleModePreferences.exportBackup(store).has("stopClearZapping"))
        }
    }

    @Test fun invalidLaterFieldCannotPartiallyApplyEarlierFlags() = runBlocking {
        withStore("atomic") { store ->
            SimpleModePreferences.restoreBackup(store, SimpleModeBackupCodec.encode(configured))
            val malformed = SimpleModeBackupCodec.encode(SimpleModeOptions()).put("channelRecovery", "false")
            try {
                SimpleModePreferences.restoreBackup(store, malformed)
                fail("Invalid boolean must fail before any change")
            } catch (_: IllegalArgumentException) { }
            assertEquals(configured, read(store))
        }
    }

    @Test fun omittedIndividualFieldsPreserveCurrentSettingsAndUnknownFieldsAreIgnored() {
        val partial = JSONObject().put("version", 1).put("hideSidebar", false).put("futureOption", true)
        assertEquals(configured.copy(hideSidebar = false), SimpleModeBackupCodec.decode(partial, configured))
    }

    @Test fun invalidTimeoutsAndFutureSchemaAreRejectedDuringPreflight() = runBlocking {
        withStore("validation") { store ->
            val settings = StoreSettings(store)
            for (invalid in listOf(0, 61, -3, 3.5, "17", JSONObject.NULL)) {
                val block = SimpleModeBackupCodec.encode(configured).put("recoveryTimeoutSeconds", invalid)
                val root = JSONObject().put("appSettings", JSONObject().put(SimpleModeBackupCodec.KEY, block))
                assertThrows(IllegalArgumentException::class.java) { BackupAppSettingsPayload.readAndValidate(root, true, settings) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                SimpleModeBackupCodec.decode(SimpleModeBackupCodec.encode(configured).put("version", 99), configured)
            }
            assertEquals(SimpleModeOptions(), read(store))
        }
    }

    @Test fun previewCountsChangedValuesWithoutDependingOnJsonOrder() {
        val current = SimpleModeBackupCodec.encode(configured)
        val reordered = JSONObject()
        current.keys().asSequence().toList().reversed().forEach { reordered.put(it, current.get(it)) }
        assertEquals(0, BackupAppSettingsPayload.changedValues(reordered, current))
        reordered.put("hideSidebar", false).put("recoveryTimeoutSeconds", 12).put("unknownFutureOption", true)
        assertEquals(2, BackupAppSettingsPayload.changedValues(
            JSONObject().put(SimpleModeBackupCodec.KEY, reordered),
            JSONObject().put(SimpleModeBackupCodec.KEY, current),
        ))
    }

    @Test fun timeoutBoundariesAreRestoredExactly() {
        for (seconds in listOf(1, 60)) {
            val options = configured.copy(recoveryTimeoutSeconds = seconds, channelRecovery = true)
            assertEquals(options, SimpleModeBackupCodec.decode(SimpleModeBackupCodec.encode(options), SimpleModeOptions()))
        }
    }
}

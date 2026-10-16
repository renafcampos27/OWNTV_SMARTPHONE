package tv.own.owntv.core.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.i18n.LocaleStore
import tv.own.owntv.core.settings.SettingsRepository

/** Actual DataStore export/import, rather than a duplicate allowlist fixture. */
@RunWith(AndroidJUnit4::class)
class SettingsBackupContractTest {
    @Test fun allRecordingOptionsOpacityAndMobileChoicesSurviveSerializedRoundTrip() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context, LocaleStore.from(context))
        val previous = settings.exportSettings()
        try {
            val choices = JSONObject().put("multiview_enabled", true).put("multiview_tiles", 2)
                .put("multiview_warning_accepted", true).put("recording_reserve_connection", false)
                .put("recording_pre_roll_minutes", 4).put("recording_post_roll_minutes", 8)
                .put("record_what_im_watching", true).put("recording_over_mobile_data", false)
                .put("sub_bg_opacity", -1).put("data_saver", true).put("background_playback", false)
            settings.importSettings(choices)
            val exported = JSONObject(settings.exportSettings().toString())
            choices.keys().forEach { key -> assertEquals(key, choices.get(key), exported.get(key)) }
            settings.importSettings(JSONObject().put("recording_pre_roll_minutes", 0).put("data_saver", false))
            settings.importSettings(exported)
            assertEquals(4, settings.recordingPreRollMinutes.first())
            assertEquals(-1, settings.subtitleBgOpacity.first())
            assertTrue(settings.dataSaver.first())
            for (invalid in listOf(-2, 101)) {
                assertTrue(runCatching { settings.importSettings(JSONObject().put("sub_bg_opacity", invalid)) }.isFailure)
                assertEquals(-1, settings.subtitleBgOpacity.first())
            }
        } finally { settings.importSettings(previous) }
    }

    @Test fun oldBackupsMergeAndKeepingHardwareAlsoKeepsImplicitHardwareChoices() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = SettingsRepository(context, LocaleStore.from(context))
        val previous = settings.exportSettings()
        try {
            settings.importSettings(JSONObject().put("recording_post_roll_minutes", 9).put("hw_decoding", false))
            settings.importSettings(JSONObject().put("recording_pre_roll_minutes", 3))
            assertEquals(9, settings.recordingPostRollMinutes.first())
            val snapshot = JSONObject(previous.toString())
            snapshot.remove("hw_decoding")
            snapshot.getJSONArray("unsetPortableKeys").put("hw_decoding")
            settings.importSettings(snapshot, keepDeviceSettings = true)
            assertFalse(settings.hwDecoding.first())
        } finally { settings.importSettings(previous) }
    }
}

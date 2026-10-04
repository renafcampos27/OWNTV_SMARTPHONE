package tv.own.owntv.core.backup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.database.entity.PlaybackQuirkEntity
import tv.own.owntv.core.model.SourceType

class PlaybackDatabaseBackupTest {
    private val source = SourceEntity(id = 4, name = "test", type = SourceType.M3U, url = "https://example.invalid/list",
        catchupTimezone = "MANUAL", catchupOffsetMin = 60, vodEnginePreference = "EXO_ONLY",
        liveTuneTimeoutSecs = 12, httpReferer = "https://example.invalid/private?token=secret")
    private val seal: (String) -> JSONObject = { JSONObject().put("cipher", it.reversed()) }
    private val unseal: (Any?) -> String? = { (it as? JSONObject)?.getString("cipher")?.reversed() }

    @Test fun `all source overrides round trip with a sealed referer`() {
        val json = JSONObject()
        PlaybackDatabaseBackup.writeSource(json, source, seal)
        assertFalse(json.toString().contains("token=secret"))
        assertEquals(source, PlaybackDatabaseBackup.readSource(json, source.copy(httpReferer = null,
            catchupTimezone = null, catchupOffsetMin = null, vodEnginePreference = null, liveTuneTimeoutSecs = null), unseal))
    }
    @Test fun `plaintext backup omits referer and old backups keep existing values`() {
        val json = JSONObject()
        PlaybackDatabaseBackup.writeSource(json, source, null)
        assertFalse(json.has("httpReferer"))
        assertEquals(source, PlaybackDatabaseBackup.readSource(JSONObject(), source, unseal))
    }
    @Test fun `explicit null clears while unreadable referer retains current value`() {
        assertNull(PlaybackDatabaseBackup.readSource(JSONObject().put("httpReferer", JSONObject.NULL), source, unseal).httpReferer)
        assertEquals(source.httpReferer, PlaybackDatabaseBackup.readSource(JSONObject().put("httpReferer", "broken"), source, unseal).httpReferer)
    }
    @Test fun `quirk remaps the source and stable key together`() {
        val row = PlaybackQuirkEntity("4:LIVE:rtp", 4, "LIVE", "EXO", true, 125, 1234)
        val result = PlaybackDatabaseBackup.decode(PlaybackDatabaseBackup.encode(row), mapOf(4L to 9L), setOf(9L))
        assertEquals(row.copy(contentKey = "9:LIVE:rtp", sourceId = 9), result)
    }
    @Test fun `unknown sources and mismatched media types cannot affect another item`() {
        val row = PlaybackQuirkEntity("4:LIVE:rtp", 4, "LIVE", "EXO")
        assertNull(PlaybackDatabaseBackup.decode(PlaybackDatabaseBackup.encode(row), emptyMap(), setOf(9L)))
        assertNull(PlaybackDatabaseBackup.decode(PlaybackDatabaseBackup.encode(row).put("type", "MOVIE"), emptyMap(), setOf(4L)))
    }
    @Test fun `delay and timeout are bounded and empty exceptions ignored`() {
        val json = PlaybackDatabaseBackup.encode(PlaybackQuirkEntity("4:LIVE:x", 4, "LIVE", audioDelayMs = 99999))
        assertEquals(5000, PlaybackDatabaseBackup.decode(json, emptyMap(), setOf(4L))!!.audioDelayMs)
        assertEquals(60, PlaybackDatabaseBackup.readSource(JSONObject().put("liveTuneTimeoutSecs", 9999), source, unseal).liveTuneTimeoutSecs)
        assertNull(PlaybackDatabaseBackup.decode(JSONObject().put("k", "4:LIVE:x").put("type", "LIVE"), emptyMap(), setOf(4L)))
    }
}

package tv.own.owntv.core.backup

import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.PlaybackQuirkEntity
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class RestoreIdentityTest {
    @Test fun settingsOnlyMetadataMatchesIdentityAtNewIdWithoutExportingSecrets() {
        val source = SourceEntity(id = 7, name = "Test", type = SourceType.XTREAM,
            url = "https://private.invalid/account", username = "secret-user", password = "secret-password")
        val identity = RestoreIdentity.sourceFingerprint(source)
        val metadata = JSONArray().put(JSONObject().put("id", 7).put("identity", identity))
        assertEquals(mapOf(7L to 42L), RestoreIdentity.sources(metadata, listOf(source.copy(id = 42, password = "rotated"))))
        assertTrue(RestoreIdentity.sources(metadata, listOf(source.copy(username = "another-user"))).isEmpty())
        assertFalse(metadata.toString().contains("private.invalid"))
        assertFalse(metadata.toString().contains("secret-user"))
        assertFalse(metadata.toString().contains("secret-password"))
        assertTrue(RestoreIdentity.sources(metadata, listOf(source, source.copy(id = 42))).isEmpty())
    }
    @Test fun coincidentIdsWithoutIdentityMatchCannotRestoreSettings() {
        val input = JSONObject().put("7", "unrelated").put("8", "matched")
        val restored = RestoreIdentity.keys(input, mapOf(8L to 7L))
        assertEquals(1, restored.length())
        assertEquals("matched", restored.getString("7"))
        assertEquals(0, RestoreIdentity.keys(input, emptyMap()).length())
    }
    @Test fun stablePlayerKeysRequireSourceIdentityButLegacyAndLocalKeysSurvive() {
        assertNull(RestoreIdentity.playerKey("7:LIVE:123", emptyMap()))
        assertEquals("42:LIVE:123", RestoreIdentity.playerKey("7:LIVE:123", mapOf(7L to 42L)))
        assertEquals("https://legacy.invalid/live", RestoreIdentity.playerKey("https://legacy.invalid/live", emptyMap()))
        assertEquals("-1:LIVE:local", RestoreIdentity.playerKey("-1:LIVE:local", emptyMap()))
    }
    @Test fun playbackQuirkCannotAttachToAnUnrelatedSourceWithTheSameId() {
        val row = PlaybackDatabaseBackup.encode(PlaybackQuirkEntity("7:LIVE:123", 7, "LIVE", "EXO"))
        assertNull(PlaybackDatabaseBackup.decode(row, emptyMap(), setOf(7L)))
        assertEquals("42:LIVE:123", PlaybackDatabaseBackup.decode(row, mapOf(7L to 42L), setOf(42L))!!.contentKey)
    }
}

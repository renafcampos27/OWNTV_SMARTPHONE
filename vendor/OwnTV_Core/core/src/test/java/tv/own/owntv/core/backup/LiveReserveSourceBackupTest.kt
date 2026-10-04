package tv.own.owntv.core.backup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType

class LiveReserveSourceBackupTest {
    private val source = SourceEntity(id = 4, name = "Test", type = SourceType.M3U,
        url = "https://example.invalid/list", liveLatencyMode = "LOW", liveLatencyCustomSecs = 5,
        liveReserveMode = "CUSTOM", liveReserveCustomSecs = 8, liveReserveExtraSecs = 4)

    @Test fun `v25 roundtrip keeps distinct delay and reserve choices`() {
        val json = JSONObject()
        LiveReserveSourceBackup.writeSource(json, source)
        val target = source.copy(liveReserveMode = "STABLE", liveReserveCustomSecs = 20, liveReserveExtraSecs = 2)
        assertEquals(source, LiveReserveSourceBackup.readSource(json, target))
    }

    @Test fun `legacy backup applies old coupled reserve to existing and new sources`() {
        val json = JSONObject().put("liveLatencyMode", "CUSTOM").put("liveLatencyCustomSecs", 23)
        for (target in listOf(source, source.copy(liveReserveMode = null, liveReserveCustomSecs = -1, liveReserveExtraSecs = -1))) {
            val restored = LiveReserveSourceBackup.readSource(json, target)
            assertEquals("CUSTOM", restored.liveReserveMode)
            assertEquals(23, restored.liveReserveCustomSecs)
            assertEquals(2, restored.liveReserveExtraSecs)
            assertEquals(target.liveLatencyMode, restored.liveLatencyMode)
        }
    }

    @Test fun `old backup with no player fields leaves target untouched`() {
        assertEquals(source, LiveReserveSourceBackup.readSource(JSONObject(), source))
    }

    @Test fun `explicit null and sentinel restore global inheritance`() {
        val json = JSONObject().put("liveReserveMode", JSONObject.NULL)
            .put("liveReserveCustomSecs", -1).put("liveReserveExtraSecs", -1)
        val restored = LiveReserveSourceBackup.readSource(json, source)
        assertNull(restored.liveReserveMode)
        assertEquals(-1, restored.liveReserveCustomSecs)
        assertEquals(-1, restored.liveReserveExtraSecs)
        val legacy = LiveReserveSourceBackup.readSource(JSONObject()
            .put("liveLatencyMode", JSONObject.NULL).put("liveLatencyCustomSecs", -1), source)
        assertEquals(restored, legacy)
    }

    @Test fun `partial legacy custom field preserves independent preset and extra`() {
        val restored = LiveReserveSourceBackup.readSource(JSONObject().put("liveLatencyCustomSecs", 17), source)
        assertEquals("CUSTOM", restored.liveReserveMode)
        assertEquals(17, restored.liveReserveCustomSecs)
        assertEquals(4, restored.liveReserveExtraSecs)
    }

    @Test fun `fresh sources inherit global reserve`() {
        val fresh = SourceEntity(name = "Fresh", type = SourceType.M3U, url = "https://example.invalid/fresh")
        assertNull(fresh.liveReserveMode)
        assertEquals(-1, fresh.liveReserveCustomSecs)
        assertEquals(-1, fresh.liveReserveExtraSecs)
    }

    @Test fun `explicit inherit in partial backup clears stale range and ignores old delay`() {
        val restored = LiveReserveSourceBackup.readSource(JSONObject()
            .put("liveReserveMode", JSONObject.NULL)
            .put("liveLatencyMode", "LOW").put("liveLatencyCustomSecs", 5), source)
        assertNull(restored.liveReserveMode)
        assertEquals(-1, restored.liveReserveCustomSecs)
        assertEquals(-1, restored.liveReserveExtraSecs)
    }

    @Test fun `partial independent backup preserves omitted fields instead of migrating delay`() {
        val restored = LiveReserveSourceBackup.readSource(JSONObject()
            .put("liveReserveMode", "STABLE")
            .put("liveLatencyMode", "LOW").put("liveLatencyCustomSecs", 5), source)
        assertEquals("STABLE", restored.liveReserveMode)
        assertEquals(8, restored.liveReserveCustomSecs)
        assertEquals(4, restored.liveReserveExtraSecs)
        val extraOnly = LiveReserveSourceBackup.readSource(JSONObject()
            .put("liveReserveExtraSecs", 3)
            .put("liveLatencyMode", "LOW").put("liveLatencyCustomSecs", 5), source)
        assertEquals("CUSTOM", extraOnly.liveReserveMode)
        assertEquals(8, extraOnly.liveReserveCustomSecs)
        assertEquals(3, extraOnly.liveReserveExtraSecs)
    }

    @Test fun `explicit balanced survives restore as an override rather than inheritance`() {
        val balanced = source.copy(liveReserveMode = "BALANCED", liveReserveCustomSecs = -1, liveReserveExtraSecs = 2)
        val json = JSONObject()
        LiveReserveSourceBackup.writeSource(json, balanced)
        val restored = LiveReserveSourceBackup.readSource(json, source)
        assertEquals("BALANCED", restored.liveReserveMode)
        assertEquals(-1, restored.liveReserveCustomSecs)
        assertEquals(2, restored.liveReserveExtraSecs)
    }
}

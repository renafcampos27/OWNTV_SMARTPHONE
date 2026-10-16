package tv.own.owntv.core.backup

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortableSettingsSnapshotTest {
    private val managed = setOf("hardware", "preRoll", "postRoll", "recordWatching")

    @Test fun `new snapshot identifies implicit choices without resetting device paths`() {
        val output = JSONObject().put("hardware", true).put("postRoll", 3)
        PortableSettingsSnapshot.write(output, managed)
        val wire = JSONObject(output.toString())
        val unset = PortableSettingsSnapshot.unsetKeys(wire, managed)
        assertEquals(setOf("preRoll", "recordWatching"), unset)
        val destination = mutableMapOf<String, Any>("hardware" to false, "preRoll" to 7,
            "recordWatching" to true, "download_root" to "device-private")
        unset.forEach(destination::remove)
        for (key in managed) if (wire.has(key)) destination[key] = wire.get(key)
        assertEquals(true, destination["hardware"])
        assertEquals(3, destination["postRoll"])
        assertFalse(destination.containsKey("preRoll"))
        assertEquals("device-private", destination["download_root"])
    }

    @Test fun `old partial backups keep merge semantics`() {
        assertTrue(PortableSettingsSnapshot.unsetKeys(JSONObject().put("hardware", true), managed).isEmpty())
    }

    @Test fun `reject unknown non portable duplicated and conflicting removals`() {
        for (keys in listOf(listOf("download_root"), listOf("hardware", "hardware"), listOf("hardware"))) {
            val data = JSONObject().put("portableSettingsSnapshotVersion", 1).put("unsetPortableKeys", JSONArray(keys))
            if (keys == listOf("hardware")) data.put("hardware", true)
            try { PortableSettingsSnapshot.validate(data, managed); fail("accepted invalid snapshot") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun `reject partial metadata and unsupported versions before writing`() {
        for (data in listOf(JSONObject().put("unsetPortableKeys", JSONArray()),
            JSONObject().put("portableSettingsSnapshotVersion", 1),
            JSONObject().put("portableSettingsSnapshotVersion", 2).put("unsetPortableKeys", JSONArray()))) {
            try { PortableSettingsSnapshot.validate(data, managed); fail("accepted invalid metadata") }
            catch (_: IllegalArgumentException) { }
        }
    }
}

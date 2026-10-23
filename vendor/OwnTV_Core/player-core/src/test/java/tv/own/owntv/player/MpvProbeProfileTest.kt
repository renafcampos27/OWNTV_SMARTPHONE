package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class MpvProbeProfileTest {
    @Test fun `mid GOP archives keep full probe and wait for a keyframe with either container`() {
        for (rawTs in listOf(true, false)) {
            val archive = MpvProbeProfile.forLoad(live = false, archive = true, rawTs = rawTs, forceFullProbe = false)
            assertFalse(archive.trimmedRawTs)
            assertEquals("5000000", archive.probeSize)
            assertEquals("0", archive.analyzeDuration)
            assertEquals("wait_keyframe=1", archive.lavcOptions)
        }
    }

    @Test fun `archive then live HLS clears the keyframe option without trimming the manifest probe`() {
        val archive = MpvProbeProfile.forLoad(false, true, true, false)
        val live = MpvProbeProfile.forLoad(true, false, false, false)
        assertNotEquals(archive.lavcOptions, live.lavcOptions)
        assertEquals("", live.lavcOptions)
        assertFalse(live.trimmedRawTs)
        assertEquals(archive.probeSize, live.probeSize)
    }

    @Test fun `raw live TS retains fast probe unless a retry needs full metadata`() {
        val fast = MpvProbeProfile.forLoad(true, false, true, false)
        val retry = MpvProbeProfile.forLoad(true, false, true, true)
        assertTrue(fast.trimmedRawTs); assertEquals("1000000", fast.probeSize)
        assertEquals("1.0", fast.analyzeDuration)
        assertFalse(retry.trimmedRawTs); assertEquals("5000000", retry.probeSize)
        assertEquals("", fast.lavcOptions); assertEquals("", retry.lavcOptions)
    }

    @Test fun `finite TS recordings never inherit the live fast probe or archive wait`() {
        val recording = MpvProbeProfile.forLoad(false, false, true, false)
        assertFalse(recording.trimmedRawTs)
        assertEquals("5000000", recording.probeSize)
        assertEquals("", recording.lavcOptions)
    }

    @Test fun `live wins over an inconsistent archive flag and adds no keyframe gate`() {
        assertEquals("", MpvProbeProfile.forLoad(true, true, false, false).lavcOptions)
    }

    @Test fun `manual software or item rescue clears the MediaCodec option but retains the full archive probe`() {
        val hardware = MpvProbeProfile.forLoad(false, true, true, false, hardwareDecoding = true)
        val software = MpvProbeProfile.forLoad(false, true, true, false, hardwareDecoding = false)
        assertEquals("wait_keyframe=1", hardware.lavcOptions)
        assertEquals("", software.lavcOptions)
        assertEquals(hardware.probeSize, software.probeSize)
        assertFalse(software.trimmedRawTs)
    }
}

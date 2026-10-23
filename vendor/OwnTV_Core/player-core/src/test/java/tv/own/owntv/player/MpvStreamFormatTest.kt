package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.player.ManifestType

class MpvStreamFormatTest {
    @Test fun declaredHlsOverridesAmbiguousTsPathAndExtensionlessAddress() {
        assertTrue(MpvStreamFormat.isHls("https://host/live/1.ts", true, ManifestType.HLS))
        assertTrue(MpvStreamFormat.isHls("https://host/archive?id=1", false, ManifestType.HLS))
    }
    @Test fun knownAndStrictLivePolicyDoesNotLeakIntoArchives() {
        assertTrue(MpvStreamFormat.isHls("https://host/live/1.ts", true, null, strict = true))
        assertTrue(MpvStreamFormat.isHls("https://host/live/1.ts", true, null, learned = true))
        assertFalse(MpvStreamFormat.isHls("https://host/archive/1.ts", false, null, strict = true, learned = true))
        assertTrue(MpvStreamFormat.isHls("https://host/archive.m3u8?token=1", false, null))
        assertFalse(MpvStreamFormat.isHls("https://host/live/1.ts", true, null))
    }
}

package tv.own.owntv.player

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import tv.own.owntv.core.player.EnginePreference

class HlsOnlyPolicyTest {
    @Test fun `Xtream conversion preserves query and fragment`() {
        assertEquals("https://example.invalid/1.m3u8?token=x#y", HlsOnlyPolicy.url("https://example.invalid/1.TS?token=x#y", true))
        assertEquals("https://example.invalid/signed.ts?token=x", HlsOnlyPolicy.url("https://example.invalid/signed.ts?token=x", false))
    }
    @Test fun `manual TS rewrites only the Xtream suffix and preserves credentials query`() {
        assertEquals("https://example.invalid/live/1.ts?token=x#y", HlsOnlyPolicy.directTsUrl("https://example.invalid/live/1.M3U8?token=x#y", true))
        assertEquals("https://example.invalid/signed.m3u8?token=x", HlsOnlyPolicy.directTsUrl("https://example.invalid/signed.m3u8?token=x", false))
    }
    @Test fun `manual TS overrides host and manifest HLS hints without changing subsequent routes`() {
        assertEquals(StreamRoute.PROGRESSIVE, LivePreviewEngine.routeFor(null, true, true, true, true, true, manualTs = true))
        assertEquals(StreamRoute.HLS, LivePreviewEngine.routeFor(null, true, false, true, false, true))
    }
    @Test fun `strict ladder never learns or tries a TS fallback`() = runTest {
        val ladder = LiveLadder()
        ladder.arm("https://example.invalid/test.ts", EnginePreference.MPV_FIRST, hlsOnly = true) { false }
        assertNull(ladder.advance(failureWasAboutFormat = true, nowMs = 0))
    }
    @Test fun `strict HLS manual mpv route has no TS or Exo fallback rung`() = runTest {
        val ladder = LiveLadder()
        ladder.arm("https://example.invalid/test.m3u8", EnginePreference.MPV_ONLY, hlsOnly = true) { true }
        assertEquals(listOf(LiveLadder.Rung.MPV_HLS), ladder.plan)
        assertNull(ladder.advance(failureWasAboutFormat = true, nowMs = 0))
    }

}

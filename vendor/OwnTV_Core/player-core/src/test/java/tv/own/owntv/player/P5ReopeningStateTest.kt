package tv.own.owntv.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Validates Batch P5: Preserving tuning parameters across re-openings (A09)
 * and unified baseline configuration upon player rebuild (A10).
 */
class P5ReopeningStateTest {

    @Test
    fun `tuning request bundles manifestType and directSource faithfully`() {
        val request = LivePreviewEngine.TuningRequest(
            url = "http://panel.example/live/stream.m3u8",
            muted = false,
            userAgent = "CustomUA/1.0",
            prerollSecsOverride = 3,
            httpHeaders = "Referer: http://allowed.example",
            drmConfig = "clear_key:123456",
            manifestType = "mpd",
            directSource = "http://direct.example:8080/stream.mpd",
            choiceId = 42L,
        )

        assertEquals("http://panel.example/live/stream.m3u8", request.url)
        assertEquals("mpd", request.manifestType)
        assertEquals("http://direct.example:8080/stream.mpd", request.directSource)
        assertEquals("CustomUA/1.0", request.userAgent)
        assertEquals(3, request.prerollSecsOverride)
        assertEquals("Referer: http://allowed.example", request.httpHeaders)
        assertEquals("clear_key:123456", request.drmConfig)
        assertEquals(42L, request.choiceId)
    }

    @Test
    fun `tuning request copy preserves manifestType and directSource when url changes`() {
        val original = LivePreviewEngine.TuningRequest(
            url = "http://stalker.example/play/original",
            muted = true,
            manifestType = "m3u8",
            directSource = "http://stalker.example:8080/direct",
        )

        val refreshed = original.copy(url = "http://stalker.example/play/fresh_token_123")
        assertEquals("http://stalker.example/play/fresh_token_123", refreshed.url)
        assertEquals("m3u8", refreshed.manifestType)
        assertEquals("http://stalker.example:8080/direct", refreshed.directSource)
        assertEquals(true, refreshed.muted)
    }

    @Test
    fun `defaults in tuning request do not require optional metadata`() {
        val minimal = LivePreviewEngine.TuningRequest(
            url = "http://example.com/live.ts",
            muted = false,
        )

        assertNull(minimal.manifestType)
        assertNull(minimal.directSource)
        assertNull(minimal.userAgent)
        assertNull(minimal.prerollSecsOverride)
        assertNotNull(minimal.meta)
    }
}

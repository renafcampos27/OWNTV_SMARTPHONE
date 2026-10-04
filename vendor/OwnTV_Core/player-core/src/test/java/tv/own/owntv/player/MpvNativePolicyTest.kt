package tv.own.owntv.player

import dev.jdtech.mpv.MPVLib
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvNativePolicyTest {
    @Test fun `mpeg2 and mpeg4 can enter hardware path`() {
        assertEquals(true, OwnTVPlayer.hwdecCovers("mpeg2video"))
        assertEquals(true, OwnTVPlayer.hwdecCovers("mpeg4 (MPEG-4 part 2)"))
    }

    @Test fun `main live codecs retain hardware eligibility`() {
        listOf("h264", "hevc", "av1", "vp9").forEach {
            assertEquals(true, OwnTVPlayer.hwdecCovers(it))
        }
    }

    @Test fun `an excluded codec skips impossible hardware retries`() {
        assertEquals(false, OwnTVPlayer.hwdecCovers("mjpeg"))
        assertEquals(false, OwnTVPlayer.hwdecCovers("msmpeg4v3"))
    }

    @Test fun `unknown codec must not trigger software rescue`() {
        assertNull(OwnTVPlayer.hwdecCovers(null))
        assertNull(OwnTVPlayer.hwdecCovers("  "))
    }

    @Test fun `codec identification tolerates case and whitespace`() {
        assertEquals(true, OwnTVPlayer.hwdecCovers("  MPEG2VIDEO (mpeg-2)  "))
    }

    @Test fun `native stop with no credits is still cleanup`() {
        assertTrue(OwnTVPlayer.ignoreNativeEndFile(MPVLib.MpvEndFileReason.STOP, 7, 7, false))
    }

    @Test fun `a credited cleanup remains valid after a replacement load`() {
        val credits = PendingStopCredits(4)
        credits.credit()
        // Replacement FILE_LOADED does not clear the outgoing command's credit.
        assertTrue(OwnTVPlayer.ignoreNativeEndFile(-1, 7, 8, credits.consume()))
        assertFalse(credits.consume())
    }

    @Test fun `real error without credits follows existing recovery`() {
        assertFalse(OwnTVPlayer.ignoreNativeEndFile(MPVLib.MpvEndFileReason.ERROR, 8, 8, false))
    }

    @Test fun `natural eof is not swallowed`() {
        assertFalse(OwnTVPlayer.ignoreNativeEndFile(MPVLib.MpvEndFileReason.EOF, 8, 8, false))
    }

    @Test fun `unknown native reason follows existing recovery`() {
        assertFalse(OwnTVPlayer.ignoreNativeEndFile(-1, null, 8, false))
    }

    @Test fun `callback invalidated between native metadata and dispatch is ignored`() {
        assertTrue(OwnTVPlayer.ignoreNativeEndFile(MPVLib.MpvEndFileReason.ERROR, 7, 8, false))
    }

    @Test fun `a late stop cannot consume a later real error classification`() {
        assertTrue(OwnTVPlayer.ignoreNativeEndFile(MPVLib.MpvEndFileReason.STOP, 9, 9, false))
        assertFalse(OwnTVPlayer.ignoreNativeEndFile(MPVLib.MpvEndFileReason.ERROR, 9, 9, false))
    }
}

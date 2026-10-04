package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class LivePlaybackConfirmationTest {
    private fun video(owns: Boolean = true, playing: Boolean = true, failed: Boolean = false, rendered: Boolean = true, age: Long? = 30L) =
        confirmsLivePlayback(owns, playing, failed, true, rendered, age, true, true)

    @Test fun previousSourceOrCurrentEngineErrorCannotClearTheFailure() {
        assertFalse(video(owns = false))
        assertFalse(video(failed = true))
    }
    @Test fun readyAndAudioWithoutTheFirstVideoFrameDoNotProveOpening() {
        assertFalse(video(rendered = false, age = null))
        assertFalse(video(playing = false))
        assertTrue(video())
    }
    @Test fun pictureFromAFrozenSourceDoesNotDefeatItsWatchdog() {
        assertFalse(video(age = 2_001L))
        assertFalse(video(age = -1L))
        assertFalse(video(age = null))
    }
    @Test fun radioRequiresActualAudioAdvanceInsteadOfAVideoFrame() {
        assertTrue(confirmsLivePlayback(true, true, false, false, false, null, true, true))
        assertFalse(confirmsLivePlayback(true, true, false, false, false, null, true, false))
        assertFalse(confirmsLivePlayback(true, true, false, false, false, null, false, true))
    }
}

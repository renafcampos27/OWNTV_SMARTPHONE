package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackFocusPolicyTest {
    @Test fun bufferingKeepsFocusUntilUserPausesOrPlaybackFails() {
        assertTrue(shouldHoldPlaybackFocus(true, false, true, false, false))
        assertTrue(shouldHoldPlaybackFocus(false, true, true, false, false))
        assertTrue(shouldHoldPlaybackFocus(false, false, true, false, false)) // READY but suppressed
        assertFalse(shouldHoldPlaybackFocus(false, true, false, false, false))
        assertFalse(shouldHoldPlaybackFocus(false, true, true, true, false))
        assertFalse(shouldHoldPlaybackFocus(false, false, false, false, false))
    }

    @Test fun interruptionPauseRetainsRequestForTheGainCallback() {
        assertTrue(shouldHoldPlaybackFocus(false, false, false, false, true))
        assertFalse(shouldHoldPlaybackFocus(false, false, false, false, false))
    }
}

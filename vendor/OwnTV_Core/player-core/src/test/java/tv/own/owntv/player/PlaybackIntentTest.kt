package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackIntentTest {
    @Test fun homeDuringDisplayHoldPreservesPlayButManualPauseWins() {
        val intent = PlaybackIntent(true)
        intent.request(false)
        intent.markTemporaryPause()
        assertFalse(intent.requested.value)
        assertTrue(intent.restoreRequested)
        intent.request(false) // Deliberate Pause while the display operation is pending.
        assertFalse(intent.restoreRequested)
    }

    @Test fun stopOrNewSelectionInvalidatesDisplayHold() {
        val intent = PlaybackIntent(true)
        intent.request(false)
        intent.markTemporaryPause()
        val heldAt = intent.revision.value
        intent.request(false) // Stop/release before leaving the player.
        assertFalse(intent.restoreRequested)
        assertFalse(mayResumeOwnedPause(true, heldAt, intent.revision.value, false))
        intent.request(true) // The next item owns its own intent.
        assertTrue(intent.restoreRequested)
        assertFalse(mayResumeOwnedPause(true, heldAt, intent.revision.value, false))
    }

    @Test fun deliberateRepeatCommandsInvalidateAnAutomaticResume() {
        val intent = PlaybackIntent(true)
        intent.request(false)
        val pausedAt = intent.revision.value
        assertFalse(intent.requested.value)
        assertTrue(mayResumeOwnedPause(true, pausedAt, intent.revision.value, false))
        intent.request(false)
        assertFalse(mayResumeOwnedPause(true, pausedAt, intent.revision.value, false))
        intent.request(true)
        assertTrue(intent.requested.value)
        assertEquals(3L, intent.revision.value)
    }
    @Test fun anotherOwnerOrTerminalFailureCannotResume() {
        assertFalse(mayResumeOwnedPause(false, 4L, 4L, false))
        assertFalse(mayResumeOwnedPause(true, 4L, 4L, true))
        assertFalse(mayResumeOwnedPause(true, 4L, 5L, false))
    }
    @Test fun softwareUhdIsRestrictedOnlyForConfirmedConstrainedDevices() {
        assertFalse(softwareDecoderAllowed(true, false, 3840, 2160))
        assertTrue(softwareDecoderAllowed(false, false, 3840, 2160))
        assertTrue(softwareDecoderAllowed(true, true, 3840, 2160))
        assertTrue(softwareDecoderAllowed(true, null, 3840, 2160))
        assertTrue(softwareDecoderAllowed(true, false, 0, 0))
        assertTrue(softwareDecoderAllowed(true, false, 1920, 1080))
        assertTrue(softwareDecoderAllowed(true, false, 1080, 1920))
        assertFalse(softwareDecoderAllowed(true, false, 2560, 1080))
    }
}

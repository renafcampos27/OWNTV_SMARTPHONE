package tv.own.owntv.mobile.playback

import org.junit.Assert.*
import org.junit.Test

class BackgroundPlaybackHoldTest {
    private class Session {
        var requested = true
        var revision = 1L
        var item = 1L
        var audioOnly = false
        var audioRevision = 0L
        fun pause(): Long { requested = false; return ++revision }
        fun command(play: Boolean) { requested = play; revision++ }
    }
    private fun BackgroundPlaybackHold<Session>.leave(s: Session, background: Boolean = false) = onBackground(
        s, s.item, s.requested, s.audioOnly, background, true,
        pause = s::pause, hideVideo = { s.audioOnly = true; ++s.audioRevision },
    )
    private fun BackgroundPlaybackHold<Session>.restore(s: Session, failed: Boolean = false) =
        onForeground(s, s.revision, s.item, s.audioRevision, failed)

    @Test fun bufferingRequestIsPausedAndOnlyItsOwnPauseCanResumeOnce() {
        val s = Session() // Requested, even if no frame/isPlaying yet.
        val hold = BackgroundPlaybackHold<Session>()
        hold.leave(s)
        assertFalse(s.requested)
        assertTrue(hold.restore(s).play)
        assertFalse(hold.restore(s).play)
    }
    @Test fun notificationPlayDuringBufferingIsNotToggledBackToPause() {
        val s = Session(); val hold = BackgroundPlaybackHold<Session>()
        hold.leave(s); s.command(true)
        assertFalse(hold.restore(s).play)
        assertTrue(s.requested)
    }
    @Test fun manualPauseEvenWhenAlreadyPausedSupersedesAutomaticResume() {
        val s = Session(); val hold = BackgroundPlaybackHold<Session>()
        hold.leave(s); s.command(false)
        assertFalse(hold.restore(s).play)
    }
    @Test fun newItemOrEngineAndFailureCannotInheritTheOldPause() {
        val s = Session(); val hold = BackgroundPlaybackHold<Session>()
        hold.leave(s); s.item++
        assertFalse(hold.restore(s).play)
        s.requested = true; hold.leave(s)
        assertFalse(hold.restore(Session()).play)
        s.requested = true; hold.leave(s)
        assertFalse(hold.restore(s, failed = true).play)
    }
    @Test fun pictureReturnsAfterRecoveryWithoutUndoingManualPause() {
        val s = Session(); val hold = BackgroundPlaybackHold<Session>()
        hold.leave(s, background = true); s.command(false)
        val restore = hold.restore(s)
        assertTrue(restore.video)
        assertFalse(restore.play)
        assertFalse(s.requested)
        s.audioOnly = false; hold.leave(s, background = true); s.item++
        assertTrue(hold.restore(s).video) // New stream inherited the lifecycle audio mode.
    }
    @Test fun explicitAudioModeChoiceAndAnotherEngineInvalidatePictureRestoration() {
        val s = Session(); val hold = BackgroundPlaybackHold<Session>()
        hold.leave(s, background = true)
        s.audioRevision++ // User deliberately chooses audio-only, even if already active.
        assertFalse(hold.restore(s).video)
        s.audioOnly = false; hold.leave(s, background = true)
        assertFalse(hold.restore(Session()).video)
    }
    @Test fun userPausedOrUserAudioOnlyIsLeftAsChosen() {
        val s = Session(); val hold = BackgroundPlaybackHold<Session>()
        s.requested = false; hold.leave(s)
        assertFalse(hold.restore(s).play)
        s.audioOnly = true; hold.leave(s, background = true)
        assertFalse(hold.restore(s).video)
    }
}

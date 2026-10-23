package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class TerminalSourceRetirementTest {
    @Test fun setupFailureBeforeAnyMediaSourceAlsoRetiresTheAttempt() {
        val owner = TuneOwnership().apply { nextTune() }
        var released = false
        assertTrue(retireTerminalSource(owner, owner.current, false) { released = true })
        assertTrue(released)
        assertTrue(owner.suspended)
    }

    @Test fun terminalSourceLosesOwnershipBeforeReleaseAndIsReleasedOnce() {
        val owner = TuneOwnership().apply { nextTune(); nextSource() }
        val token = owner.current
        var releases = 0
        assertTrue(retireTerminalSource(owner, token, false) {
            assertFalse(owner.accepts(token)); releases++
        })
        assertFalse(retireTerminalSource(owner, token, false) { releases++ })
        assertEquals(1, releases)
    }

    @Test fun healthyCurrentPlaybackSurvivesAFalseTerminalReport() {
        val owner = TuneOwnership().apply { nextTune(); nextSource() }
        val token = owner.current
        assertFalse(retireTerminalSource(owner, token, true) { fail("working source must survive") })
        assertTrue(owner.accepts(token))
    }

    @Test fun oldChannelErrorCannotRetireANewTuneOrReopenedUrl() {
        val owner = TuneOwnership().apply { nextTune(); nextSource() }
        val old = owner.current
        owner.nextTune(); owner.nextSource()
        assertFalse(retireTerminalSource(owner, old, false) { fail("new tune must survive") })
        assertTrue(owner.accepts(owner.current))
    }

    @Test fun oldSourceErrorCannotRetireTheCurrentRetryWithinTheSameTune() {
        val owner = TuneOwnership().apply { nextTune(); nextSource() }
        val old = owner.current
        owner.nextSource()
        assertFalse(retireTerminalSource(owner, old, false) { fail("new source must survive") })
        assertTrue(owner.accepts(owner.current))
    }

    @Test fun freshTuneCanStartAfterTerminalRetirement() {
        val owner = TuneOwnership().apply { nextTune(); nextSource() }
        retireTerminalSource(owner, owner.current, false) {}
        owner.nextTune(); owner.nextSource()
        assertTrue(owner.accepts(owner.current))
    }
}

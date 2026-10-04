package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class TuneOwnershipTest {
    @Test fun lateRetryAndHttpCannotMutateSecondVisitToSameChannel() {
        val owner = TuneOwnership()
        owner.nextTune()
        val firstA = owner.nextSource()
        owner.nextTune() // B
        val b = owner.nextSource()
        owner.nextTune() // A again, same URL
        val secondA = owner.nextSource()
        var reconnectPending = true
        var prepares = 0
        owner.runIfCurrent(firstA) { reconnectPending = false; prepares++ }
        owner.runIfCurrent(b) { reconnectPending = false; prepares++ }
        assertTrue(reconnectPending)
        assertEquals(0, prepares)
        owner.runIfCurrent(secondA) { reconnectPending = false; prepares++ }
        assertFalse(reconnectPending)
        assertEquals(1, prepares)
    }

    @Test fun retrySourceDropsLateSegmentsFromEarlierPrepareWithinSameTune() {
        val owner = TuneOwnership()
        owner.nextTune()
        val manifestAndSegments = owner.nextSource()
        val retry = owner.nextSource()
        assertEquals(manifestAndSegments.tuneId, retry.tuneId)
        assertFalse(owner.accepts(manifestAndSegments))
        assertTrue(owner.accepts(retry))
        owner.nextTune() // stop: no active source, even if an old callback arrives now
        assertFalse(owner.accepts(retry))
        assertFalse(owner.accepts(owner.current))
    }

    @Test fun countsOutstandingBodiesSeparatelyAcrossTunesAndRetries() {
        val calls = TuneHttpRequests()
        val a = TuneToken(1, 1)
        val b = TuneToken(2, 1)
        val bRetry = TuneToken(2, 2)
        calls.started(a); calls.started(a); calls.started(b); calls.started(bRetry)
        assertEquals(2, calls.otherTunes(2))
        calls.finished(a)
        assertEquals(1, calls.otherTunes(2))
        calls.finished(a); calls.finished(a) // duplicate completion cannot underflow
        assertEquals(0, calls.otherTunes(2))
        assertEquals(2, calls.otherTunes(3))
        calls.finished(b); calls.finished(bRetry)
        assertEquals("", calls.snapshot())
    }

}

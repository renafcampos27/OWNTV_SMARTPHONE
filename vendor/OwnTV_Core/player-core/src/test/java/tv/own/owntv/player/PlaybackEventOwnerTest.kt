package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackEventOwnerTest {
    @Test fun delayedErrorsFramesTracksAndPositionFromPreviousLoadCannotPublish() {
        val owner = PlaybackEventOwner()
        val old = owner.begin()
        val published = mutableListOf<String>()
        val delayed = listOf("error", "first-frame", "tracks", "position").map { name ->
            { if (owner.accepts(old)) published.add(name) }
        }
        val active = owner.begin()
        delayed.forEach { it() }
        assertTrue(published.isEmpty())
        assertTrue(owner.accepts(active))
    }
    @Test fun sameUrlNewLoadAndStopInvalidateEveryOldEvent() {
        val owner = PlaybackEventOwner()
        val a = owner.begin(); owner.begin(); val aAgain = owner.begin()
        assertFalse(owner.accepts(a)); assertTrue(owner.accepts(aAgain))
        owner.invalidate()
        assertFalse(owner.accepts(aAgain)); assertFalse(owner.accepts(null))
    }
    @Test fun anotherPlayerWithItsOwnOwnerCannotBorrowAnIdentity() {
        val a = PlaybackEventOwner(); val b = PlaybackEventOwner()
        val token = a.begin(); b.begin()
        assertFalse(b.accepts(token))
    }
    @Test fun decoderHandoverCannotReloadAnOldGenerationOrOverrideManualTransport() {
        val old = PlaybackRequestStamp(1, 1)
        assertFalse(old.accepts(3, 3, true)) // A -> B -> A, URL equality is irrelevant.
        assertFalse(old.accepts(1, 2, false)) // Pause while waiting.
        assertFalse(old.accepts(1, 3, true)) // A later Play needs a fresh continuation.
        assertFalse(old.accepts(2, 2, false)) // Stop.
        assertTrue(PlaybackRequestStamp(1, 3).accepts(1, 3, true))
    }
}

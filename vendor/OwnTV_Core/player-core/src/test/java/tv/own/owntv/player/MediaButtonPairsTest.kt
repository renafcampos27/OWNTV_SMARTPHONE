package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class MediaButtonPairsTest {
    @Test fun completePairIsHandledOnceByPlatform() {
        val keys = MediaButtonPairs()
        assertFalse(keys.orphanUp(85, true, 123))
        assertFalse(keys.orphanUp(85, false, 123))
        assertTrue(keys.orphanUp(85, false, 124))
    }

    @Test fun staleOrDifferentDownDoesNotSwallowAnOrphanUp() {
        val keys = MediaButtonPairs()
        keys.orphanUp(85, true, 123)
        assertTrue(keys.orphanUp(85, false, 999))
        keys.orphanUp(126, true, 123)
        assertTrue(keys.orphanUp(127, false, 123))
    }

    @Test fun engineChangeClearsOldKeys() {
        val keys = MediaButtonPairs()
        keys.orphanUp(85, true, 123)
        keys.reset()
        assertTrue(keys.orphanUp(85, false, 123))
    }
}

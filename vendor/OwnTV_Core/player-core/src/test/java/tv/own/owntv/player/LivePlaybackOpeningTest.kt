package tv.own.owntv.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LivePlaybackOpeningTest {
    @Test fun `FILE_LOADED without playback remains bounded by the opening watchdog`() {
        val opening = LivePlaybackOpening().apply { arm(1, "a") }
        assertFalse(opening.confirm(1, "a", true, true, false))
        assertFalse(opening.confirm(1, "a", true, false, true))
        assertFalse(opening.confirm(1, "a", true, null, false))
        assertFalse(opening.confirm(1, "a", false, false, false))
        assertTrue(opening.confirm(1, "a", true, false, false))
    }

    @Test fun `audio only and video both confirm from native playback without dimensions`() {
        val opening = LivePlaybackOpening().apply { arm(1, "radio") }
        assertTrue(opening.confirm(1, "radio", true, false, false))
        assertFalse(opening.confirm(1, "radio", true, false, false))
    }

    @Test fun `old source and commands cannot confirm a newer tune even when URL repeats`() {
        val opening = LivePlaybackOpening().apply { arm(1, "a"); arm(2, "b"); arm(3, "a") }
        assertFalse(opening.confirm(1, "a", true, false, false))
        assertFalse(opening.confirm(3, "b", true, false, false))
        assertTrue(opening.confirm(3, "a", true, false, false))
    }

    @Test fun `late notification captured with current generation cannot confirm old native ABA entry`() {
        val opening = LivePlaybackOpening().apply { arm(3, "a", "old-entry", true) }
        assertFalse(opening.confirm(3, "a", true, false, false, "old-entry"))
        assertFalse(opening.confirm(3, "a", true, false, false, null))
        assertTrue(opening.confirm(3, "a", true, false, false, "new-entry"))
    }

    @Test fun `first load waits for an actual playing entry when playlist introspection exists`() {
        val opening = LivePlaybackOpening().apply { arm(1, "radio", null, true) }
        assertFalse(opening.confirm(1, "radio", true, false, false, null))
        assertTrue(opening.confirm(1, "radio", true, false, false, "first-entry"))
    }
}

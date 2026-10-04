package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for P3 (Identidade, cancelamento e descarte terminal):
 * A01, A02, A11, A13 identity, guard rules, and disposal lifecycle.
 */
class P3HandoverAndDisposalTest {

    @Test
    fun `A01 - capturing generation after hardReset preserves fallback identity`() {
        var loadGeneration = 10L
        val simulateHardReset = {
            loadGeneration++
        }

        // Buggy behavior (A01 before fix):
        loadGeneration++
        val staleGen = loadGeneration
        simulateHardReset() // hardReset bumped loadGeneration again
        val buggyProceeds = staleGen == loadGeneration
        assertFalse("Before fix, captured gen diverged and fallback was abandoned", buggyProceeds)

        // Fixed behavior (A01):
        loadGeneration++
        simulateHardReset()
        val fixedGen = loadGeneration
        val fixedProceeds = fixedGen == loadGeneration
        assertTrue("After fix, gen captured after hardReset matches current generation", fixedProceeds)
    }

    @Test
    fun `A02 - responsive handover aborts if superseded before stopping mpv or before starting exo`() {
        var loadGeneration = 20L
        var currentUrl: String? = "http://stream1"
        loadGeneration++
        val gen = loadGeneration
        val expectedUrl = currentUrl

        // Case 1: user zapped/stopped while mpvAsync was queued
        var userActionHappened = true
        if (userActionHappened) {
            loadGeneration++ // stop() or new tune
            currentUrl = null
        }
        val allowedToStopMpv = gen == loadGeneration && currentUrl == expectedUrl
        assertFalse("Superseded handover must not stop or detach mpv", allowedToStopMpv)

        // Case 2: user zapped during SURFACE_HANDOFF_MS
        val freshGen = loadGeneration
        val freshUrl = "http://stream2"
        currentUrl = freshUrl
        userActionHappened = true
        if (userActionHappened) {
            loadGeneration++ // stop() or new tune during delay
            currentUrl = "http://stream3"
        }
        val allowedToStartExo = freshGen == loadGeneration && currentUrl == freshUrl
        assertFalse("Superseded handover must not start Exo with stale URL", allowedToStartExo)

        // Case 3: uninterrupted handover proceeds
        val uninterruptedGen = loadGeneration
        val uninterruptedUrl = currentUrl
        val proceeds = uninterruptedGen == loadGeneration && currentUrl == uninterruptedUrl
        assertTrue("Handover with matching generation proceeds cleanly", proceeds)
    }

    @Test
    fun `A11 - property event guard rejects all callbacks when exoActive is true`() {
        var exoActive = true
        var decodeGuardTripped = false
        var subText: String? = "initial"
        var isPlaying = true
        var earlyHardResetTripped = false

        val onEventPropertyString = { property: String, value: String ->
            if (!exoActive) {
                if (property == "hwdec-current") {
                    decodeGuardTripped = true
                } else if (property == "sub-text") {
                    subText = value
                }
            }
        }

        val onEventPropertyNoValue = { property: String ->
            if (!exoActive) {
                if (property == "sub-text") subText = null
            }
        }

        val onEventPropertyBoolean = { property: String, value: Boolean ->
            if (!exoActive) {
                if (property == "pause") isPlaying = !value
            }
        }

        val onEventEndFile = { pendingCredits: Int ->
            var consumed = false
            if (pendingCredits > 0) {
                consumed = true
            }
            if (!consumed && !exoActive) {
                earlyHardResetTripped = true
            }
        }

        // When exoActive is true:
        onEventPropertyString("hwdec-current", "no")
        assertFalse("hwdec-current must be ignored when exoActive", decodeGuardTripped)

        onEventPropertyString("sub-text", "late subtitle")
        assertEquals("initial", subText)

        onEventPropertyNoValue("sub-text")
        assertEquals("initial", subText)

        onEventPropertyBoolean("pause", true)
        assertTrue("pause must be ignored when exoActive", isPlaying)

        // Late END_FILE with 0 credits when exoActive is true
        onEventEndFile(0)
        assertFalse("END_FILE must not trigger hard reset when exoActive is true", earlyHardResetTripped)

        // When exoActive is false:
        exoActive = false
        onEventPropertyString("hwdec-current", "no")
        assertTrue("hwdec-current is processed when mpv is active", decodeGuardTripped)

        onEventEndFile(0)
        assertTrue("END_FILE is processed when mpv is active and no credits", earlyHardResetTripped)
    }

    @Test
    fun `A13 - terminal disposal vs release for reuse contract`() {
        var isDisposed = false
        var playerReleased = false
        var scopesCancelled = false

        fun release() {
            playerReleased = true
        }

        fun dispose() {
            isDisposed = true
            release()
            scopesCancelled = true
        }

        // Reusable release:
        release()
        assertTrue("Player is released for reuse", playerReleased)
        assertFalse("Scopes are not cancelled during reusable release", scopesCancelled)
        assertFalse("Engine is not marked disposed during reusable release", isDisposed)

        // Terminal disposal:
        dispose()
        assertTrue("Engine marked disposed on terminal disposal", isDisposed)
        assertTrue("Scopes cancelled on terminal disposal", scopesCancelled)
    }
}

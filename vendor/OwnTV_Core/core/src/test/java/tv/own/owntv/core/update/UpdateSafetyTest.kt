package tv.own.owntv.core.update

import org.junit.Assert.*
import org.junit.Test

class UpdateSafetyTest {
    @Test fun anyActiveCaptureBlocksRegardlessOfProfile() {
        assertTrue(updateRecordingBlocks(1, emptyList(), 1000))
        assertFalse(updateRecordingBlocks(0, emptyList(), 1000))
    }
    @Test fun imminentOrOverdueTimersBlockButExpiredAndDistantOnesDoNot() {
        assertTrue(updateRecordingBlocks(0, listOf(121000L to 300000L), 1000))
        assertTrue(updateRecordingBlocks(0, listOf(0L to 300000L), 1000))
        assertFalse(updateRecordingBlocks(0, listOf(121001L to 300000L), 1000))
        assertFalse(updateRecordingBlocks(0, listOf(0L to 1000L), 1000))
    }
}

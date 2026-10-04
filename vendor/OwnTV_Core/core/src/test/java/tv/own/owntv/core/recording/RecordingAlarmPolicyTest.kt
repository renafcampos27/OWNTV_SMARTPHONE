package tv.own.owntv.core.recording

import org.junit.Assert.*
import org.junit.Test

class RecordingAlarmPolicyTest {
    @Test fun revokedBetweenCheckAndArmImmediatelyFallsBack() {
        var exact = 0
        var inexact = 0
        assertTrue(RecordingAlarmPolicy.arm({ true }, { exact++; throw SecurityException() }, { inexact++ }).isSuccess)
        assertEquals(1, exact)
        assertEquals(1, inexact)
    }
    @Test fun deniedPermissionSkipsExactAttempt() {
        var inexact = 0
        assertTrue(RecordingAlarmPolicy.arm({ false }, { error("must not run") }, { inexact++ }).isSuccess)
        assertEquals(1, inexact)
    }
    @Test fun failureOfFallbackRemainsObservable() {
        val failed = RecordingAlarmPolicy.arm({ true }, { throw SecurityException() }, { throw IllegalStateException("alarm unavailable") })
        assertEquals("alarm unavailable", failed.exceptionOrNull()?.message)
    }
    @Test fun nonPermissionFailureIsNotMaskedWithAnotherAlarm() {
        var inexact = 0
        assertTrue(RecordingAlarmPolicy.arm({ true }, { throw IllegalStateException() }, { inexact++ }).isFailure)
        assertEquals(0, inexact)
    }
}

package tv.own.owntv.mobile.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryTimeoutTest {
    @Test fun existingInstallKeepsThreeSeconds() { assertEquals(3, RecoveryTimeout.normalize(null)) }
    @Test fun allSelectableValuesArePreserved() {
        for (seconds in 1..60) {
            assertEquals(seconds, RecoveryTimeout.normalize(seconds))
            assertEquals(seconds * 1000L, RecoveryTimeout.milliseconds(seconds))
        }
    }
    @Test fun outOfRangeValuesAreBounded() {
        assertEquals(1, RecoveryTimeout.normalize(-1))
        assertEquals(60, RecoveryTimeout.normalize(Int.MAX_VALUE))
    }
}

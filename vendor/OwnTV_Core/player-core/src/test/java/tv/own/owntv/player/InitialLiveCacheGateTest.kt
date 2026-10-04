package tv.own.owntv.player

import org.junit.Assert.*
import org.junit.Test

class InitialLiveCacheGateTest {
    @Test fun `loading or unavailable native readiness cannot release initial gate`() {
        val gate = InitialLiveCacheGate().apply { arm(1, "a", true) }
        assertFalse(gate.release(1, "a", true, false))
        assertFalse(gate.release(1, "a", false, true))
        assertFalse(gate.release(1, "a", null, false))
        assertFalse(gate.release(1, "a", false, null))
        assertTrue(gate.release(1, "a", false, false))
    }

    @Test fun `old commands and native source cannot release a new load even after ABA`() {
        val gate = InitialLiveCacheGate().apply { arm(1, "a", true); arm(2, "b", true); arm(3, "a", true) }
        assertFalse(gate.release(1, "a", false, false))
        assertFalse(gate.release(3, "b", false, false))
        assertTrue(gate.release(3, "a", false, false))
    }

    @Test fun `gate is released once and a new load rearms it`() {
        val gate = InitialLiveCacheGate().apply { arm(1, "a", true) }
        assertTrue(gate.release(1, "a", false, false))
        assertFalse(gate.release(1, "a", false, false))
        gate.arm(2, "b", true)
        assertTrue(gate.release(2, "b", false, false))
    }

    @Test fun `automatic initial buffering does not install a custom gate`() {
        val gate = InitialLiveCacheGate().apply { arm(1, "a", false) }
        assertFalse(gate.release(1, "a", false, false))
    }
}

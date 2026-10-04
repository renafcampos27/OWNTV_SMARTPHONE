package tv.own.owntv.core.sync.work

import org.junit.Assert.*
import org.junit.Test

class DrainWakeupGateTest {
    @Test fun kickBeforeCloseMakesCurrentWorkerDrainAgain() {
        val gate = DrainWakeupGate()
        val before = gate.begin("worker")
        assertEquals(DrainWakeupGate.Enqueue.NONE, gate.request(false))
        assertFalse(gate.close("worker", before))
        assertTrue(gate.close("worker", gate.currentRevision()))
    }
    @Test fun kickAfterLastClosePersistsExactlyOneSuccessor() {
        val gate = DrainWakeupGate()
        val revision = gate.begin("worker")
        assertTrue(gate.close("worker", revision))
        assertEquals(DrainWakeupGate.Enqueue.SUCCESSOR, gate.request(true))
        repeat(50) { assertEquals(DrainWakeupGate.Enqueue.NONE, gate.request(true)) }
        val next = gate.begin("successor")
        assertTrue(gate.close("successor", next))
    }
    @Test fun coldProcessWithUnfinishedWorkRequestsOneDurableSuccessor() {
        val gate = DrainWakeupGate()
        assertTrue(gate.needsWorkSnapshot())
        assertEquals(DrainWakeupGate.Enqueue.SUCCESSOR, gate.request(true))
        assertEquals(DrainWakeupGate.Enqueue.NONE, gate.request(true))
    }
    @Test fun emptyColdQueueStartsOneWorker() {
        val gate = DrainWakeupGate()
        assertEquals(DrainWakeupGate.Enqueue.KEEP, gate.request(false))
        assertEquals(DrainWakeupGate.Enqueue.NONE, gate.request(false))
    }
    @Test fun replacementCannotBeClosedByPreviousWorker() {
        val gate = DrainWakeupGate()
        val first = gate.begin("old")
        val next = gate.begin("replacement")
        assertTrue(gate.close("old", first))
        assertEquals(DrainWakeupGate.Enqueue.NONE, gate.request(false))
        assertFalse(gate.close("replacement", next))
    }
    @Test fun enqueueFailureCanRetryWithoutLosingRevision() {
        val gate = DrainWakeupGate()
        gate.request(false)
        gate.enqueueFailed()
        assertEquals(DrainWakeupGate.Enqueue.SUCCESSOR, gate.request(true))
        assertEquals(2L, gate.currentRevision())
    }
}

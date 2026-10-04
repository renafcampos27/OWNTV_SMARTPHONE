package tv.own.owntv.core.storage

import org.junit.Assert.*
import org.junit.Test

class PhysicalStorageLedgerTest {
    @Test fun commitDuringSpaceQuerySurvivesPublishingOlderFreeSnapshot() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(110, 0)
        assertTrue(ledger.reserve("a", 8, 0, 100))
        val ticketBeforeStatRead = ledger.observationTicket()
        ledger.commit("a", 8)
        ledger.observe(110, 500_000_000, ticketBeforeStatRead)
        assertFalse(ledger.reserve("b", 8, 0, 100))
        assertTrue(ledger.reserve("b", 2, 0, 100))
    }
    @Test fun cumulativeCommitCounterSaturatesAndFailsClosedRatherThanWrapping() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(Long.MAX_VALUE, 0)
        val before = ledger.observationTicket()
        ledger.commit("a", Long.MAX_VALUE)
        ledger.commit("a", 1)
        assertEquals(Long.MAX_VALUE, ledger.observationTicket())
        ledger.observe(Long.MAX_VALUE, 500_000_000, before)
        assertFalse(ledger.reserve("b", 1, 0, 0))
    }
    @Test fun twoPendingWritersCannotSpendTheSameFreeSpace() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(110, 0)
        assertTrue(ledger.reserve("a", 8, 0, 100))
        assertFalse(ledger.reserve("b", 8, 0, 100))
    }
    @Test fun committedOtherWriterStillCountsBeforeNextPhysicalSnapshot() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(120, 0)
        assertTrue(ledger.reserve("a", 8, 0, 100))
        assertTrue(ledger.reserve("b", 8, 0, 100))
        ledger.commit("a", 8)
        ledger.commit("b", 8)
        assertFalse(ledger.reserve("a", 5, 0, 100))
        assertTrue(ledger.reserve("a", 4, 0, 100))
    }
    @Test fun newObservationDoesNotCountCommittedBytesTwice() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(120, 0)
        assertTrue(ledger.reserve("a", 8, 0, 100))
        ledger.commit("a", 8)
        ledger.observe(112, 500_000_000)
        assertTrue(ledger.reserve("b", 12, 0, 100))
        assertFalse(ledger.reserve("c", 1, 0, 100))
    }
    @Test fun releaseRemovesPendingReservationButNotBytesActuallyWritten() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(120, 0)
        assertTrue(ledger.reserve("a", 8, 3, 100))
        ledger.commit("a", 4)
        ledger.release("a")
        assertTrue(ledger.reserve("b", 16, 0, 100))
        assertFalse(ledger.reserve("c", 1, 0, 100))
    }
    @Test fun scratchAndPendingRemainReservedAcrossPhysicalObservations() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(130, 0)
        assertTrue(ledger.reserve("a", 10, 10, 100))
        ledger.observe(130, 500_000_000)
        assertFalse(ledger.reserve("b", 11, 0, 100))
        assertTrue(ledger.reserve("b", 10, 0, 100))
    }
    @Test fun malformedHugeSizesDoNotOverflowIntoAdmission() {
        val ledger = PhysicalStorageLedger()
        ledger.observe(Long.MAX_VALUE, 0)
        assertTrue(ledger.reserve("a", Long.MAX_VALUE - 10, 0, 0))
        assertFalse(ledger.reserve("b", 20, 0, 0))
    }
}

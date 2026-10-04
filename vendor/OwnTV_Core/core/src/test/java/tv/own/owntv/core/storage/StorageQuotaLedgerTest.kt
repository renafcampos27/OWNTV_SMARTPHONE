package tv.own.owntv.core.storage

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StorageQuotaLedgerTest {
    @Test fun `all profiles share one budget and same file is counted once`() {
        val ledger = StorageQuotaLedger()
        ledger.reconcile(mapOf("profile1/a" to 70, "profile2/b" to 20))
        ledger.acquire("profile3/c")
        assertTrue(ledger.grow("profile3/c", 10, 100))
        assertFalse(ledger.grow("profile3/c", 11, 100))
        assertEquals(100L, ledger.total())
    }
    @Test fun `database refresh cannot replace an active reservation with stale progress`() {
        val ledger = StorageQuotaLedger()
        ledger.acquire("capture")
        assertTrue(ledger.grow("capture", 80, 100))
        ledger.reconcile(mapOf("capture" to 2, "saved" to 20))
        assertEquals(100L, ledger.total())
        assertFalse(ledger.grow("capture", 81, 100))
        ledger.reconcile(emptyMap())
        assertEquals(80L, ledger.total())
    }
    @Test fun `lowering quota keeps media but refuses new bytes until enough is removed`() {
        val ledger = StorageQuotaLedger()
        ledger.reconcile(mapOf("saved" to 80))
        ledger.acquire("new")
        assertFalse(ledger.grow("new", 1, 50))
        assertEquals(80L, ledger.total())
        ledger.reconcile(emptyMap())
        assertTrue(ledger.grow("new", 50, 50))
    }
    @Test fun `failure releases only unwritten reservation and retains actual bytes`() {
        val ledger = StorageQuotaLedger()
        ledger.acquire("partial")
        assertTrue(ledger.grow("partial", 80, 100))
        ledger.release("partial", 25)
        ledger.acquire("other")
        assertTrue(ledger.grow("other", 75, 100))
        assertFalse(ledger.grow("other", 76, 100))
    }
    @Test fun `resume starts with retained bytes rather than charging them twice`() {
        val ledger = StorageQuotaLedger()
        ledger.reconcile(mapOf("partial" to 70))
        ledger.acquire("partial")
        assertTrue(ledger.grow("partial", 90, 100))
        assertEquals(90L, ledger.total())
        ledger.release("partial", 90)
        ledger.acquire("partial")
        assertTrue(ledger.grow("partial", 100, 100))
        assertEquals(100L, ledger.total())
    }
    @Test fun `two concurrent admissions cannot overspend the last available bytes`() {
        val ledger = StorageQuotaLedger()
        ledger.acquire("a"); ledger.acquire("b")
        val go = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = listOf("a", "b").map { key -> pool.submit<Boolean> { go.await(); ledger.grow(key, 70, 100) } }
            go.countDown()
            assertEquals(1, results.count { it.get(5, TimeUnit.SECONDS) })
            assertEquals(70L, ledger.total())
        } finally { pool.shutdownNow() }
    }
    @Test fun `one destination cannot have two writers`() {
        val ledger = StorageQuotaLedger()
        ledger.acquire("same")
        assertTrue(runCatching { ledger.acquire("same") }.isFailure)
        ledger.release("same", 10)
        ledger.acquire("same")
        assertTrue(ledger.grow("same", 20, 20))
    }
    @Test fun `volume ledgers remain independent`() {
        val internal = StorageQuotaLedger(); val usb = StorageQuotaLedger()
        internal.acquire("a"); usb.acquire("b")
        assertTrue(internal.grow("a", 4 * StorageQuotaPolicy.GIB, 4 * StorageQuotaPolicy.GIB))
        assertTrue(usb.grow("b", 16 * StorageQuotaPolicy.GIB, 16 * StorageQuotaPolicy.GIB))
        assertFalse(internal.grow("a", 4 * StorageQuotaPolicy.GIB + 1, 4 * StorageQuotaPolicy.GIB))
    }
    @Test fun `large imported metadata cannot overflow into a negative total and grant more quota`() {
        val ledger = StorageQuotaLedger()
        ledger.reconcile(mapOf("a" to Long.MAX_VALUE, "b" to Long.MAX_VALUE))
        ledger.acquire("new")
        assertEquals(Long.MAX_VALUE, ledger.total())
        assertFalse(ledger.grow("new", 1, Long.MAX_VALUE))
    }
    @Test fun `new names cannot collide across profiles channels or archived programmes started in one minute`() {
        val row = tv.own.owntv.core.database.entity.RecordingEntity(profileId = 1, sourceId = 1,
            channelId = 1, channelName = "Canal", streamUrl = "https://example.invalid/archive", title = "Programa",
            programmeStartMs = 1000, programmeStopMs = 2000, startMs = 3000, stopMs = 5000)
        val name = tv.own.owntv.core.recording.RecordingRules.fileName(row)
        assertNotEquals(name, tv.own.owntv.core.recording.RecordingRules.fileName(row.copy(profileId = 2)))
        assertNotEquals(name, tv.own.owntv.core.recording.RecordingRules.fileName(row.copy(channelId = 2)))
        assertNotEquals(name, tv.own.owntv.core.recording.RecordingRules.fileName(row.copy(programmeStartMs = 1500)))
        assertEquals(name, tv.own.owntv.core.recording.RecordingRules.fileName(row.copy(id = 999)))
    }
    @Test fun `internal reserve protects three GiB or ten percent of the real volume`() {
        assertEquals(3 * StorageQuotaPolicy.GIB, StorageQuotaPolicy.reserve(20 * StorageQuotaPolicy.GIB, false))
        assertEquals(4 * StorageQuotaPolicy.GIB, StorageQuotaPolicy.reserve(40 * StorageQuotaPolicy.GIB, false))
    }
    @Test fun `USB reserve protects one GiB or five percent regardless of content URI representation`() {
        assertEquals(StorageQuotaPolicy.GIB, StorageQuotaPolicy.reserve(8 * StorageQuotaPolicy.GIB, true))
        assertEquals(5 * StorageQuotaPolicy.GIB, StorageQuotaPolicy.reserve(100 * StorageQuotaPolicy.GIB, true))
    }
}

package tv.own.owntv.core.live

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OpenStreamRegistryConcurrencyTest {
    @Test fun onlyOneConcurrentCaptureCanReserveLastSlot() {
        val registry = OpenStreamRegistry()
        val source = tv.own.owntv.core.database.entity.SourceEntity(id = 1, name = "source",
            type = tv.own.owntv.core.model.SourceType.XTREAM, url = "http://example.invalid", maxConnections = 1)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val winners = (0 until 100).map { pool.submit<OpenStreamRegistry.Claim?> {
                registry.tryClaim(source, StreamPurpose.WATCHING)
            } }.mapNotNull { it.get() }
            assertEquals(1, winners.size)
            registry.release(winners.single())
            assertNotNull(registry.tryClaim(source, StreamPurpose.WATCHING))
        } finally { pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
    }
    @Test fun concurrentClaimsAndReleasesNeverLoseOtherOwners() {
        val registry = OpenStreamRegistry()
        val pool = Executors.newFixedThreadPool(8)
        try {
            val claims = (0 until 200).map { pool.submit<OpenStreamRegistry.Claim> { registry.claim(1, StreamPurpose.RECORDING) } }.map { it.get() }
            assertEquals(200, registry.openOn(1).recording)
            claims.take(100).map { claim -> pool.submit { registry.release(claim) } }.forEach { it.get() }
            assertEquals(claims.drop(100).map { it.id }.toSet(), registry.claims.value.map { it.id }.toSet())
            claims.drop(100).map { claim -> pool.submit { registry.release(claim) } }.forEach { it.get() }
            assertEquals(0, registry.openOn(1).total)
        } finally { pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
    }
}

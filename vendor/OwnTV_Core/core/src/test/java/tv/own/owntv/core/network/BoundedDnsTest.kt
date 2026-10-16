package tv.own.owntv.core.network

import okhttp3.Dns
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BoundedDnsTest {
    private val answer = listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
    private fun fails(block: () -> Unit) {
        try { block(); fail("expected UnknownHostException") } catch (_: UnknownHostException) { }
    }

    @Test fun fallbackExpiresAndCacheIsBounded() {
        var now = 1L
        var healthy = true
        val dns = BoundedDns(delegate = Dns { if (healthy) answer else throw UnknownHostException() },
            maxEntries = 2, staleTtlMs = 100, nowMs = { now })
        dns.lookup("one"); dns.lookup("two"); dns.lookup("three")
        healthy = false
        fails { dns.lookup("one") }
        assertEquals(answer, dns.lookup("three"))
        now += 100
        fails { dns.lookup("three") }
    }

    @Test fun changingCustomResolverCannotReuseOldAddresses() {
        val delegate = object : Dns, DnsCacheIdentity {
            var config = 1
            override fun cacheIdentity(): Any = config
            override fun lookup(hostname: String) = lookupForIdentity(hostname, config)
            override fun lookupForIdentity(hostname: String, identity: Any): List<InetAddress> {
                if (identity == 1) return answer
                throw UnknownHostException()
            }
        }
        val dns = BoundedDns(delegate = delegate)
        assertEquals(answer, dns.lookup("same.host"))
        delegate.config = 2
        fails { dns.lookup("same.host") }
    }

    @Test fun timedOutHostSharesItsOutstandingNativeLookup() {
        val release = CountDownLatch(1)
        val entered = CountDownLatch(1)
        val count = AtomicInteger()
        val dns = BoundedDns(timeoutMs = 30, delegate = Dns {
            count.incrementAndGet(); entered.countDown(); release.await(2, TimeUnit.SECONDS); answer
        })
        try {
            fails { dns.lookup("hung.host") }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            repeat(4) { fails { dns.lookup("hung.host") } }
            assertEquals(1, count.get())
        } finally { release.countDown() }
    }

    @Test fun manyDistinctStuckHostsDoNotGrowWorkersOrQueue() {
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val dns = BoundedDns(timeoutMs = 20, delegate = Dns {
            count.incrementAndGet(); release.await(2, TimeUnit.SECONDS); answer
        })
        try {
            repeat(20) { index -> fails { dns.lookup("hung.$index") } }
            assertTrue("global native resolver cap", count.get() <= 4)
        } finally { release.countDown() }
    }

    @Test fun interruptionsRemainCancellationRatherThanStaleSuccess() {
        val dns = BoundedDns(delegate = Dns { answer })
        dns.lookup("host")
        try {
            Thread.currentThread().interrupt()
            fails { dns.lookup("host") }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
    }
}

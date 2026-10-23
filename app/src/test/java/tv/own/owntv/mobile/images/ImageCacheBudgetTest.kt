package tv.own.owntv.mobile.images

import okhttp3.OkHttpClient
import okhttp3.Protocol
import org.junit.Assert.*
import org.junit.Test

class ImageCacheBudgetTest {
    private val mib = 1024L * 1024L

    @Test fun lowRamReceivesSmallerHeapBudgetAndBothProfilesAreBounded() {
        assertEquals(8 * mib, ImageCacheBudget.memoryBytes(128 * mib, true))
        assertTrue(ImageCacheBudget.memoryBytes(128 * mib, false) > 8 * mib)
        assertEquals(16 * mib, ImageCacheBudget.memoryBytes(Long.MAX_VALUE, true))
        assertEquals(48 * mib, ImageCacheBudget.memoryBytes(Long.MAX_VALUE, false))
    }

    @Test fun diskCacheIsDisabledWhenSpaceIsLowAndOtherwiseLimitedToAvailableBudget() {
        assertEquals(0L, ImageCacheBudget.diskBytes(63 * mib, false))
        assertEquals(0L, ImageCacheBudget.diskBytes(0L, true))
        assertEquals(100 * mib / 50, ImageCacheBudget.diskBytes(100 * mib, false))
        assertEquals(32 * mib, ImageCacheBudget.diskBytes(Long.MAX_VALUE, true))
        assertEquals(96 * mib, ImageCacheBudget.diskBytes(Long.MAX_VALUE, false))
    }

    @Test fun imageTransportChangesDoNotMutateTheCatalogOrStreamingClient() {
        val original = OkHttpClient.Builder().retryOnConnectionFailure(false)
            .protocols(listOf(Protocol.HTTP_1_1)).build()
        val images = imageHttpClient(original)
        assertEquals(listOf(Protocol.HTTP_1_1), original.protocols)
        assertFalse(original.retryOnConnectionFailure)
        assertEquals(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1), images.protocols)
        assertTrue(images.retryOnConnectionFailure)
        assertSame(original.dns, images.dns)
        assertSame(original.proxySelector, images.proxySelector)
        assertSame(original.sslSocketFactory, images.sslSocketFactory)
        assertNotSame(original, images)
    }
}

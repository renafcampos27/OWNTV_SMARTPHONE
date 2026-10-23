package tv.own.owntv.mobile.images

import android.app.ActivityManager
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okio.Path.Companion.toOkioPath
import org.koin.core.context.GlobalContext

/** Derive image transport without changing IPTV protocols, retry policy, DNS or proxy semantics. */
internal fun installMobileImageLoading() {
    SingletonImageLoader.setSafe { context ->
        val lowRam = context.getSystemService(Context.ACTIVITY_SERVICE)
            .let { it as? ActivityManager }?.isLowRamDevice == true
        val imageClient = imageHttpClient(GlobalContext.get().get())
        val diskBytes = ImageCacheBudget.diskBytes(context.cacheDir.usableSpace, lowRam)
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(ImageCacheBudget.memoryBytes(Runtime.getRuntime().maxMemory(), lowRam)).build() }
            .diskCache {
                if (diskBytes == 0L) null else DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(diskBytes).build()
            }
            .build()
    }
}

internal fun imageHttpClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
    .retryOnConnectionFailure(true)
    .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
    .build()

package tv.own.owntv.player

import okhttp3.Interceptor
import okhttp3.Response

/** Logical stream origin survives redirects to a CDN with unrelated segment paths. */
internal data class ProviderWaitOrigin(val url: String)

/** Enforce the provider deadline before DNS/connect, also on a new manual channel choice. */
internal class ProviderBackoffGuard : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val origin = request.tag(ProviderWaitOrigin::class.java)?.url ?: request.url.toString()
        val wait = LiveStreamQuirks.providerBackOff(origin)
        if (wait != null) {
            throw ProviderHttpRefusal(wait.httpCode, mapOf("Retry-After" to listOf(wait.secondsLeft.toString())), "")
        }
        return chain.proceed(request)
    }
}

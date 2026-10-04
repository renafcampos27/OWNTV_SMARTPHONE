package tv.own.owntv.player

import java.net.ProtocolException
import okhttp3.Interceptor
import okhttp3.Response

/** An observed HTTP refusal, not a connection failure eligible for OkHttp recovery. */
internal class ProviderHttpRefusal(
    val responseCode: Int,
    val responseHeaders: Map<String, List<String>>,
    val responseText: String,
) : ProtocolException("HTTP $responseCode")

/** Keep HTTP follow-ups inside the player budget while retaining DNS/connect failover. */
internal class ProviderRetryGuard(
    private val onRefused: (okhttp3.Request, ProviderHttpRefusal) -> Unit = { _, _ -> },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code != 408 && response.code != 429 && response.code != 503) return response
        val refusal = ProviderHttpRefusal(
            response.code, response.headers.toMultimap(),
            runCatching { response.peekBody(564).string() }.getOrDefault(""),
        )
        response.close()
        onRefused(response.request, refusal)
        throw refusal
    }
}

package tv.own.owntv.mobile.ui.screens.settings

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.withContext
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.own.owntv.core.network.DnsConfig
import tv.own.owntv.core.network.DnsConfigHolder
import tv.own.owntv.mobile.R
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/** What the proxy or DNS test row is showing. */
sealed interface NetworkTestState {
    data object Idle : NetworkTestState
    data object Testing : NetworkTestState

    /** Reached it, and how long it took. */
    data class Ok(val millis: Long) : NetworkTestState

    /** Didn't reach it. [argument] fills the messages that name a code or a host. */
    data class Failed(@param:StringRes val messageRes: Int, val argument: Any? = null) : NetworkTestState
}

/** The 204 endpoint the TV app tests against too — tiny, unauthenticated and always up. */
private const val PROXY_PROBE_URL = "https://www.gstatic.com/generate_204"

/** A hostname worth resolving: it exists, it is short, and nobody's ISP blocks it. */
private const val DNS_PROBE_HOST = "dns.google"

private const val TEST_TIMEOUT_SECS = 10L

/**
 * One HEAD request through the typed proxy. The app's own client is reused so the test inherits its
 * TLS and interceptors — only the proxy and the timeouts are replaced.
 */
suspend fun probeProxy(
    client: OkHttpClient,
    host: String,
    port: Int,
    username: String,
    password: String,
): NetworkTestState {
    val h = host.trim()
    if (h.isBlank() || port !in 1..65535) {
        return NetworkTestState.Failed(R.string.settings_proxy_invalid_address)
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val builder = client.newBuilder()
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(h, port)))
                .connectTimeout(TEST_TIMEOUT_SECS, TimeUnit.SECONDS)
                .readTimeout(TEST_TIMEOUT_SECS, TimeUnit.SECONDS)
            if (username.trim().isNotBlank()) {
                builder.proxyAuthenticator { _, response ->
                    // Answering a second time would loop: the credentials were already refused.
                    if (response.request.header("Proxy-Authorization") != null) return@proxyAuthenticator null
                    response.request.newBuilder()
                        .header("Proxy-Authorization", Credentials.basic(username.trim(), password))
                        .build()
                }
            } else {
                builder.proxyAuthenticator(Authenticator.NONE)
            }
            val started = System.currentTimeMillis()
            builder.build().newCall(Request.Builder().url(PROXY_PROBE_URL).head().build())
                .execute()
                .use { response ->
                    if (!response.isSuccessful && response.code != 204) throw ProxyHttpException(response.code)
                }
            System.currentTimeMillis() - started
        }.fold(
            onSuccess = { NetworkTestState.Ok(it) },
            onFailure = { proxyFailure(it) },
        )
    }
}

private class ProxyHttpException(val code: Int) : IOException()

private fun proxyFailure(t: Throwable): NetworkTestState = when (t) {
    is ProxyHttpException -> NetworkTestState.Failed(R.string.settings_proxy_http, t.code)
    is UnknownHostException -> NetworkTestState.Failed(R.string.settings_proxy_host_unreachable)
    is SocketTimeoutException -> NetworkTestState.Failed(R.string.settings_proxy_timed_out)
    is ConnectException -> NetworkTestState.Failed(R.string.settings_proxy_connection_failed)
    else -> NetworkTestState.Failed(R.string.settings_proxy_failed)
}

/**
 * One lookup through the typed DNS server, with the system resolver deliberately switched off: a
 * custom server that cannot answer must fail the test rather than quietly succeed through Android's.
 */
suspend fun probeDns(host: String, port: Int, dohUrl: String): NetworkTestState {
    val h = host.trim()
    val doh = dohUrl.trim()
    if (h.isBlank() && doh.isBlank()) {
        return NetworkTestState.Failed(R.string.settings_dns_enter_server)
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val holder = DnsConfigHolder(
                configFlow = emptyFlow(),
                initialConfig = DnsConfig(
                    enabled = true,
                    host = h,
                    port = if (port in 1..65535) port else 53,
                    dohUrl = doh,
                ),
                fallbackToSystem = false,
            )
            val started = System.currentTimeMillis()
            val addresses = holder.dns.lookup(DNS_PROBE_HOST)
            val elapsed = System.currentTimeMillis() - started
            if (addresses.isEmpty()) throw NoAddressesException()
            elapsed
        }.fold(
            onSuccess = { NetworkTestState.Ok(it) },
            onFailure = { dnsFailure(it) },
        )
    }
}

private class NoAddressesException : IOException()

private fun dnsFailure(t: Throwable): NetworkTestState {
    val message = t.message.orEmpty()
    return when {
        t is NoAddressesException ->
            NetworkTestState.Failed(R.string.settings_dns_no_addresses, DNS_PROBE_HOST)
        // The custom resolver wraps the real cause in its message, so the text is what there is to go on.
        message.contains("unknown host", ignoreCase = true) ||
            message.contains("UnknownHostException", ignoreCase = true) ->
            NetworkTestState.Failed(R.string.settings_dns_not_reachable)
        t is SocketTimeoutException ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true) ->
            NetworkTestState.Failed(R.string.settings_dns_timed_out)
        message.contains("Network is unreachable", ignoreCase = true) ->
            NetworkTestState.Failed(R.string.settings_dns_network_unreachable)
        message.contains("refused", ignoreCase = true) ->
            NetworkTestState.Failed(R.string.settings_dns_connection_refused)
        else -> NetworkTestState.Failed(R.string.settings_dns_test_failed)
    }
}

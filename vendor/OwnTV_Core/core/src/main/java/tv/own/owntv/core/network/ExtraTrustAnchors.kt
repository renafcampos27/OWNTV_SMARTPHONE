package tv.own.owntv.core.network

import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Let's Encrypt roots that many devices do not trust yet (#208), read from `res/raw/isrg_extra_roots.pem`.
 *
 * Older devices may lack ISRG Root X2 and the newer Root YE / YR anchors.
 * The platform decides first; these roots are consulted only when it rejects the chain, so nothing that
 * worked before changes. Source: https://letsencrypt.org/certificates/ — bundled DER fingerprints
 * were verified against the official HTTPS downloads and are pinned in ExtraTrustAnchorsTest.
 */
class ExtraTrustAnchors(pems: InputStream) {

    val certificates: List<X509Certificate> = pems.use {
        CertificateFactory.getInstance("X.509").generateCertificates(it).map { c -> c as X509Certificate }
    }

    /** The platform trust manager, falling back to [certificates] when it rejects a server chain. */
    val trustManager: X509TrustManager = run {
        val system = trustManagerFor(null)
        val bundled = trustManagerFor(
            KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                certificates.forEachIndexed { i, cert -> setCertificateEntry(i.toString(), cert) }
            },
        )
        systemFirst(system, bundled)
    }

    val sslSocketFactory: SSLSocketFactory =
        SSLContext.getInstance("TLS").apply { init(null, arrayOf(trustManager), null) }.socketFactory

    internal companion object {
        /** Supplement server trust without weakening client authentication or bypassing validation. */
        fun systemFirst(system: X509TrustManager, bundled: X509TrustManager): X509TrustManager =
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                    system.checkClientTrusted(chain, authType)

                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                    try {
                        system.checkServerTrusted(chain, authType)
                    } catch (systemFailure: CertificateException) {
                        try {
                            bundled.checkServerTrusted(chain, authType)
                        } catch (_: CertificateException) {
                            throw systemFailure
                        }
                    }
                }

                override fun getAcceptedIssuers(): Array<X509Certificate> =
                    system.acceptedIssuers + bundled.acceptedIssuers
            }
    }

    private fun trustManagerFor(keyStore: KeyStore?): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore) }
            .trustManagers.filterIsInstance<X509TrustManager>().first()
}

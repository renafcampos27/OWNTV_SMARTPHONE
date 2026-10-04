package tv.own.owntv.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

class ExtraTrustAnchorsTest {
    private val anchors = ExtraTrustAnchors(File("src/main/res/raw/isrg_extra_roots.pem").inputStream())
    private val chain get() = arrayOf(anchors.certificates.last())

    @Test fun `roots match DER fingerprints verified against official HTTPS downloads`() {
        assertEquals(
            listOf(
                "69729b8e15a86efc177a57afb7171dfc64add28c2fca8cf1507e34453ccb1470",
                "e14ffcad5b0025731006caa43a121a22d8e9700f4fb9cf852f02a708aa5d5666",
                "e57b7e6f150c419102e8d5c055729ff967b9d1a829bf00cec89ca604ebf4a86f",
            ),
            anchors.certificates.map { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.encoded)
                    .joinToString("") { "%02x".format(it) }
            },
        )
        anchors.certificates.forEach { cert ->
            assertTrue(cert.basicConstraints >= 0)
            cert.verify(cert.publicKey)
        }
    }

    @Test fun `bundled roots are accepted by real trust manager`() {
        anchors.certificates.forEach {
            anchors.trustManager.checkServerTrusted(arrayOf(it), it.publicKey.algorithm)
        }
    }

    @Test fun `system success never consults bundled trust`() {
        val system = RecordingTrust()
        val bundled = RecordingTrust(CertificateException("not trusted"))
        ExtraTrustAnchors.systemFirst(system, bundled).checkServerTrusted(chain, "RSA")
        assertEquals(1, system.serverChecks)
        assertEquals(0, bundled.serverChecks)
    }

    @Test fun `system certificate rejection tries bundled trust`() {
        val system = RecordingTrust(CertificateException("unknown root"))
        val bundled = RecordingTrust()
        ExtraTrustAnchors.systemFirst(system, bundled).checkServerTrusted(chain, "RSA")
        assertEquals(1, system.serverChecks)
        assertEquals(1, bundled.serverChecks)
    }

    @Test fun `both trust managers reject preserving system exception`() {
        val original = CertificateException("expired or untrusted chain")
        val trust = ExtraTrustAnchors.systemFirst(
            RecordingTrust(original), RecordingTrust(CertificateException("also rejected")),
        )
        val failure = runCatching { trust.checkServerTrusted(chain, "RSA") }.exceptionOrNull()
        assertSame(original, failure)
    }

    @Test fun `client rejection never falls back to bundled roots`() {
        val original = CertificateException("client not trusted")
        val system = RecordingTrust(clientFailure = original)
        val bundled = RecordingTrust()
        val failure = runCatching {
            ExtraTrustAnchors.systemFirst(system, bundled).checkClientTrusted(chain, "RSA")
        }.exceptionOrNull()
        assertSame(original, failure)
        assertEquals(1, system.clientChecks)
        assertEquals(0, bundled.clientChecks)
    }

    @Test fun `non certificate system failure does not trigger fallback`() {
        val original = IllegalStateException("unexpected provider failure")
        val system = RecordingTrust(original)
        val bundled = RecordingTrust()
        val failure = runCatching {
            ExtraTrustAnchors.systemFirst(system, bundled).checkServerTrusted(chain, "RSA")
        }.exceptionOrNull()
        assertSame(original, failure)
        assertEquals(0, bundled.serverChecks)
    }

    @Test fun `accepted issuers retain system roots and include extras`() {
        val system = RecordingTrust(issuers = arrayOf(anchors.certificates.first()))
        val bundled = RecordingTrust(issuers = chain)
        assertArrayEquals(
            system.acceptedIssuers + bundled.acceptedIssuers,
            ExtraTrustAnchors.systemFirst(system, bundled).acceptedIssuers,
        )
    }

    private class RecordingTrust(
        val serverFailure: Exception? = null,
        val clientFailure: CertificateException? = null,
        val issuers: Array<X509Certificate> = emptyArray(),
    ) : X509TrustManager {
        var serverChecks = 0
        var clientChecks = 0
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            serverChecks++
            serverFailure?.let { throw it }
        }
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            clientChecks++
            clientFailure?.let { throw it }
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = issuers
    }
}

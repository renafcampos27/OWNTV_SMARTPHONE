package tv.own.owntv.core.parser

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import tv.own.owntv.core.database.entity.SourceEntity
import tv.own.owntv.core.model.SourceType
import tv.own.owntv.core.network.HttpClient

@RunWith(AndroidJUnit4::class)
class XtreamClientTest {

    @Test fun accountRecognitionAndZonePreserveSourceHeaders() = runBlocking {
        var requests = 0
        val client = XtreamClient(HttpClient(OkHttpClient.Builder().addInterceptor { chain ->
            requests++
            assertEquals("custom-player", chain.request().header("User-Agent"))
            assertEquals("https://referer.invalid", chain.request().header("Referer"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"user_info":{"auth":1,"status":"Active"},"server_info":{"timezone":"Europe/Lisbon"}}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()))
        val source = testSource.copy(userAgent = "custom-player", httpReferer = "https://referer.invalid")
        assertTrue(client.isXtreamAccount(source))
        assertEquals("Europe/Lisbon", client.fetchAccountStatus(source).serverTimeZone)
        assertEquals(2, requests)
    }

    @Test fun plainOrOversizedDocumentsCannotBeRecognizedAsXtream() = runBlocking {
        assertFalse(createClient("{}").isXtreamAccount(testSource))
        assertFalse(createClient("""{"hello":"plain server"}""").isXtreamAccount(testSource))
        val large = org.json.JSONObject().put("padding", "x".repeat(1024 * 1024 + 1))
            .put("user_info", org.json.JSONObject().put("status", "Active")).toString()
        assertFalse(createClient(large).isXtreamAccount(testSource))
    }

    @Test fun cancelledRecognitionPropagatesInsteadOfStartingFallback() = runBlocking {
        val caller = kotlinx.coroutines.Job().apply { cancel() }
        var reachedFallback = false
        try {
            kotlinx.coroutines.withContext(caller) { createClient("{}").isXtreamAccount(testSource) }
            reachedFallback = true
        } catch (_: kotlinx.coroutines.CancellationException) { }
        assertFalse(reachedFallback)
    }

    private fun createClient(jsonResponse: String): XtreamClient {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toByteArray(Charsets.UTF_8).toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return XtreamClient(HttpClient(okHttpClient))
    }

    private val testSource = SourceEntity(
        id = 1,
        name = "Test Source",
        type = SourceType.XTREAM,
        url = "http://example.com",
        username = "user",
        password = "pass",
    )

    @Test
    fun fetchAccountDetails_detectsHlsSupport() = runBlocking {
        val json = """
            {
              "user_info": {
                "username": "testuser",
                "status": "Active",
                "exp_date": "1796598000",
                "allowed_output_formats": ["m3u8", "ts", "rtmp"]
              }
            }
        """.trimIndent()

        val client = createClient(json)
        val details = requireNotNull(client.fetchAccountDetails(testSource))
        assertTrue(details.hlsSupported)
        assertEquals(1796598000000L, details.expiryMs)
    }

    @Test
    fun fetchAccountDetails_hlsNotSupportedWhenOmitted() = runBlocking {
        val json = """
            {
              "user_info": {
                "username": "testuser",
                "status": "Active",
                "exp_date": "1796598000"
              }
            }
        """.trimIndent()

        val client = createClient(json)
        val details = requireNotNull(client.fetchAccountDetails(testSource))
        assertFalse(details.hlsSupported)
        assertEquals(1796598000000L, details.expiryMs)
    }

    @Test
    fun fetchAccountDetails_hlsNotSupportedWhenTsOnly() = runBlocking {
        val json = """
            {
              "user_info": {
                "username": "testuser",
                "status": "Active",
                "allowed_output_formats": ["ts"]
              }
            }
        """.trimIndent()

        val client = createClient(json)
        val details = requireNotNull(client.fetchAccountDetails(testSource))
        assertFalse(details.hlsSupported)
    }
}

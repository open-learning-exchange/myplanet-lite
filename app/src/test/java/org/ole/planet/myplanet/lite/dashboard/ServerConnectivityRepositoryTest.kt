package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ServerConnectivityRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: ServerConnectivityRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        val client = OkHttpClient.Builder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .readTimeout(1, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
        repository = ServerConnectivityRepository(client, moshi)
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun checkServerConnectivity_invalidUrl_returnsUnreachable() {
        val result = repository.checkServerConnectivity("not a url")
        assertFalse(result.reachable)
        assertNull(result.parentCode)
        assertNull(result.code)
    }

    @Test
    fun checkServerConnectivity_networkError_returnsUnreachable() {
        val baseUrl = mockWebServer.url("/").toString()
        mockWebServer.shutdown() // Simulate connection failure
        val result = repository.checkServerConnectivity(baseUrl)
        assertFalse(result.reachable)
    }

    @Test
    fun checkServerConnectivity_httpError_returnsUnreachable() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.checkServerConnectivity(baseUrl)

        assertFalse(result.reachable)
    }

    @Test
    fun checkServerConnectivity_http200_emptyBody_returnsReachableWithoutCodes() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(""))
        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.checkServerConnectivity(baseUrl)

        assertTrue(result.reachable)
        assertNull(result.parentCode)
        assertNull(result.code)
    }

    @Test
    fun checkServerConnectivity_http200_validMetadata_returnsReachableWithCodes() {
        val json = """
            {
                "rows": [
                    {
                        "doc": {
                            "parentCode": "pCode",
                            "code": "mCode"
                        }
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))
        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.checkServerConnectivity(baseUrl)

        assertTrue(result.reachable)
        assertEquals("pCode", result.parentCode)
        assertEquals("mCode", result.code)
    }
}

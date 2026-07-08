package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
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
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

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

    @Test
    fun checkServerConnectivity_http200_invalidJson_returnsReachableWithoutCodes() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("invalid json"))
        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.checkServerConnectivity(baseUrl)

        assertTrue(result.reachable)
        assertNull(result.parentCode)
        assertNull(result.code)
    }

    @Test
    fun checkServerConnectivity_http200_validJsonMissingCodes_returnsReachableWithoutCodes() {
        val json = """
            {
                "rows": [
                    {
                        "doc": {}
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))
        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.checkServerConnectivity(baseUrl)

        assertTrue(result.reachable)
        assertNull(result.parentCode)
        assertNull(result.code)
    }

    @Test
    fun checkServerConnectivity_http200_onlyParentCode_returnsReachableWithParentCode() {
        val json = """
            {
                "rows": [
                    {
                        "doc": {
                            "parentCode": "pCode"
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
        assertNull(result.code)
    }

    @Test
    fun checkServerConnectivity_http200_onlyCode_returnsReachableWithCode() {
        val json = """
            {
                "rows": [
                    {
                        "doc": {
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
        assertNull(result.parentCode)
        assertEquals("mCode", result.code)
    }


    @Test
    fun recordLoginActivity_postsPayloadWithCookie() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val baseUrl = mockWebServer.url("/login_activities").toString()
        val payload = JSONObject().apply { put("testKey", "testValue") }

        repository.recordLoginActivity(baseUrl, payload, "test-cookie")

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/login_activities", request.path)
        assertEquals("test-cookie", request.headers["Cookie"])
        val body = request.body.readUtf8()
        assertEquals("{\"testKey\":\"testValue\"}", body)
    }

    @Test
    fun recordResourceActivity_postsPayloadWithBasicAuth() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val baseUrl = mockWebServer.url("/db/resource_activities").toString()
        val payload = JSONObject().apply { put("resourceId", "resource-1") }

        repository.recordResourceActivity(baseUrl, payload, "Basic abc123")

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/db/resource_activities", request.path)
        assertEquals("Basic abc123", request.headers["Authorization"])
        assertEquals("{\"resourceId\":\"resource-1\"}", request.body.readUtf8())
    }

    @Test
    fun recordCourseActivity_postsPayloadWithCookie() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(200))
        val baseUrl = mockWebServer.url("/db/course_activities").toString()
        val payload = JSONObject().apply { put("courseId", "course-1") }

        repository.recordCourseActivity(baseUrl, payload, "AuthSession=test-cookie")

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/db/course_activities", request.path)
        assertEquals("AuthSession=test-cookie", request.headers["Cookie"])
        assertEquals("{\"courseId\":\"course-1\"}", request.body.readUtf8())
    }

    @Test
    fun recordLoginActivity_ignoresServerError_doesNotThrow() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        val baseUrl = mockWebServer.url("/login_activities").toString()
        val payload = JSONObject().apply { put("testKey", "testValue") }

        // This should not throw an exception, fulfilling the "Intentionally ignoring response" contract
        repository.recordLoginActivity(baseUrl, payload, "test-cookie")

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/login_activities", request.path)
    }
}

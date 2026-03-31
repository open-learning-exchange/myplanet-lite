package org.ole.planet.myplanet.lite.dashboard

import org.ole.planet.myplanet.lite.profile.StoredCredentials
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class DashboardSurveySubmissionsRepositoryTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardSurveySubmissionsRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardSurveySubmissionsRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `fetchExistingSubmission success returns submission lookup`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "docs": [
                            {
                                "_id": "sub_123",
                                "_rev": "1-abc"
                            }
                        ]
                    }
                """.trimIndent())
        )

        val result = repository.fetchExistingSubmission(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("testuser", "testpass"),
            sessionCookie = "session_cookie=value",
            parentId = "parent_456",
            userId = "user_789",
            userName = "Test User",
            parentRev = "2-def"
        )

        assertTrue(result.isSuccess)
        val lookup = result.getOrNull()
        assertEquals("sub_123", lookup?.id)
        assertEquals("1-abc", lookup?.rev)

        val request = mockWebServer.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/db/submissions/_find", request.path)
        assertEquals("Basic dGVzdHVzZXI6dGVzdHBhc3M=", request.getHeader("Authorization"))
        assertEquals("session_cookie=value", request.getHeader("Cookie"))
        val bodyStr = request.body.readUtf8()
        assertTrue(bodyStr.contains("\"parentId\":\"parent_456\""))
        assertTrue(bodyStr.contains("\"user._id\":\"user_789\""))
        assertTrue(bodyStr.contains("\"user.name\":\"Test User\""))
        assertTrue(bodyStr.contains("\"parent._rev\":\"2-def\""))
    }

    @Test
    fun `fetchExistingSubmission with empty docs returns null`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""
                    {
                        "docs": []
                    }
                """.trimIndent())
        )

        val result = repository.fetchExistingSubmission(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = null,
            sessionCookie = null,
            parentId = "parent_456",
            userId = "user_789",
            userName = "Test User",
            parentRev = "2-def"
        )

        assertTrue(result.isSuccess)
        assertEquals(null, result.getOrNull())
    }

    @Test
    fun `fetchExistingSubmission returns failure on error response`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
        )

        val result = repository.fetchExistingSubmission(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = null,
            sessionCookie = null,
            parentId = "parent_456",
            userId = "user_789",
            userName = null,
            parentRev = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Unexpected response \${response.code}", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchExistingSubmission missing base url returns failure`() = runTest {
        val result = repository.fetchExistingSubmission(
            baseUrl = "   ",
            credentials = null,
            sessionCookie = null,
            parentId = "parent_456",
            userId = "user_789",
            userName = null,
            parentRev = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchExistingSubmission missing user identifier returns failure`() = runTest {
        val result = repository.fetchExistingSubmission(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = null,
            sessionCookie = null,
            parentId = "parent_456",
            userId = "",
            userName = "   ",
            parentRev = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing user identifier", result.exceptionOrNull()?.message)
    }
}

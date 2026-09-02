package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionTeam
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionUser
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.profile.StoredCredentials

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

    private fun createSubmission() = SurveySubmission(
        parentId = "parent123",
        parent = SubmissionParent(id = "parent123", rev = "1", name = "Test Survey", description = "Test", questions = emptyList()),
        user = SubmissionUser(id = "user1", name = "Test User", planetCode = "planet", parentCode = "parent"),
        team = SubmissionTeam(id = "team1", name = "Team A", type = "type"),
        answers = emptyList(),
        status = "pending",
        startTime = System.currentTimeMillis(),
        lastUpdateTime = System.currentTimeMillis(),
        source = "test",
        parentCode = "code"
    )

    @Test
    fun `submitSurvey success returns success result`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"ok":true,"id":"sub1","rev":"1-abc"}""")
        )

        val submission = createSubmission()
        val baseUrl = mockWebServer.url("/").toString()
        val credentials = StoredCredentials("user", "password")
        val sessionCookie = "AuthSession=cookie123"

        val result = repository.submitSurvey(baseUrl, credentials, sessionCookie, submission)

        assertTrue(result.isSuccess)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/submissions", request.path)
        assertEquals("POST", request.method)
        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.getHeader("Authorization"))
        assertEquals("AuthSession=cookie123", request.getHeader("Cookie"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"parentId\":\"parent123\""))
        assertTrue(body.contains("\"app\":\"myplanet-lite\""))
    }

    @Test
    fun `submitPublicSurvey success posts answers to public endpoint`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(201).setBody("""{"ok":true}""")
        )

        val result = repository.submitPublicSurvey(
            baseUrl = mockWebServer.url("/").toString(),
            teamId = "team1",
            surveyId = "survey1",
            answers = listOf("Yes", 7, null),
        )

        assertTrue(result.isSuccess)

        val request = mockWebServer.takeRequest()
        assertEquals("/api/public/surveys/team1/survey1/submissions", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"answers":["Yes",7,null]}""", request.body.readUtf8())
    }

    @Test
    fun `submitSurvey missing base url returns failure`() = runTest {
        val submission = createSubmission()
        val invalidUrls = listOf("", "   ", "/", " / ")
        for (url in invalidUrls) {
            val result = repository.submitSurvey(url, null, null, submission)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `submitSurvey server error returns failure`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(500)
        )

        val submission = createSubmission()
        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.submitSurvey(baseUrl, null, null, submission)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Unexpected response 500. body=", result.exceptionOrNull()?.message)
    }

    @Test
    fun `submitSurvey network error returns failure`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val submission = createSubmission()
        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.submitSurvey(baseUrl, null, null, submission)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `fetchExistingSubmission success returns lookup result`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "docs": [
                        {
                            "_id": "sub123",
                            "_rev": "1-abc",
                            "app": "myplanet"
                        }
                    ]
                }
            """.trimIndent())
        )

        val baseUrl = mockWebServer.url("/").toString()
        val credentials = StoredCredentials("user", "password")
        val sessionCookie = "AuthSession=cookie123"

        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = credentials,
            sessionCookie = sessionCookie,
            parentId = "parent123",
            userId = "user1",
            userName = "Test User",
            parentRev = "1"
        )

        assertTrue(result.isSuccess)
        val lookup = result.getOrNull()
        assertNotNull(lookup)
        assertEquals("sub123", lookup?.id)
        assertEquals("1-abc", lookup?.rev)
        assertEquals("myplanet", lookup?.app)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/submissions/_find", request.path)
        assertEquals("POST", request.method)
        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.getHeader("Authorization"))
        assertEquals("AuthSession=cookie123", request.getHeader("Cookie"))

        val body = request.body.readUtf8()
        assertTrue(body.contains("\"selector\":{"))
        assertTrue(body.contains("\"type\":\"survey\""))
        assertTrue(body.contains("\"parentId\":\"parent123\""))
        assertTrue(body.contains("\"user._id\":\"user1\""))
        assertTrue(body.contains("\"user.name\":\"Test User\""))
        assertTrue(body.contains("\"parent._rev\":\"1\""))
    }

    @Test
    fun `fetchExistingSubmission success empty docs returns null`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "docs": []
                }
            """.trimIndent())
        )

        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = "user1",
            userName = "Test User",
            parentRev = "1"
        )

        assertTrue(result.isSuccess)
        val lookup = result.getOrNull()
        assertTrue(lookup == null)
    }

    @Test
    fun `fetchExistingSubmission missing base url returns failure`() = runTest {
        val invalidUrls = listOf("", "   ", "/", " / ")
        for (url in invalidUrls) {
            val result = repository.fetchExistingSubmission(
                baseUrl = url,
                credentials = null,
                sessionCookie = null,
                parentId = "parent123",
                userId = "user1",
                userName = "Test User",
                parentRev = "1"
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
        }
    }

    @Test
    fun `fetchExistingSubmission missing user identifier returns failure`() = runTest {
        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = "  ",
            userName = null,
            parentRev = "1"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Missing user identifier", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchExistingSubmission server error returns failure`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(404)
        )

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = "user1",
            userName = "Test User",
            parentRev = "1"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Unexpected response 404. body=", result.exceptionOrNull()?.message)
    }

    @Test
    fun `fetchExistingSubmission network error returns failure`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
        )

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = "user1",
            userName = "Test User",
            parentRev = "1"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `fetchExistingSubmission empty body returns null`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("")
        )

        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = "user1",
            userName = "Test User",
            parentRev = "1"
        )

        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull() == null)
    }

    @Test
    fun `fetchExistingSubmission with only userId succeeds`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "docs": [
                        {
                            "_id": "sub123",
                            "_rev": "1-abc"
                        }
                    ]
                }
            """.trimIndent())
        )

        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = "user1",
            userName = null,
            parentRev = "1"
        )

        assertTrue(result.isSuccess)
        val lookup = result.getOrNull()
        assertNotNull(lookup)
        assertEquals("sub123", lookup?.id)

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"user._id\":\"user1\""))
    }

    @Test
    fun `fetchExistingSubmission with only userName succeeds`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("""
                {
                    "docs": [
                        {
                            "_id": "sub123",
                            "_rev": "1-abc"
                        }
                    ]
                }
            """.trimIndent())
        )

        val baseUrl = mockWebServer.url("/").toString()

        val result = repository.fetchExistingSubmission(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            parentId = "parent123",
            userId = null,
            userName = "Test User",
            parentRev = "1"
        )

        assertTrue(result.isSuccess)
        val lookup = result.getOrNull()
        assertNotNull(lookup)
        assertEquals("sub123", lookup?.id)

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"user.name\":\"Test User\""))
    }
}

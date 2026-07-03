package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import java.io.IOException

class DashboardSurveysRepositoryTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardSurveysRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardSurveysRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun fetchTeamSurveys_success() = runTest {
        val jsonResponse = """
            {
              "docs": [
                {
                  "_id": "survey1",
                  "name": "Test Survey"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchTeamSurveys(
            baseUrl = baseUrl,
            credentials = StoredCredentials("user", "pass"),
            sessionCookie = "cookie",
            teamId = "team1"
        )

        assertTrue(result.isSuccess)
        val surveys = result.getOrNull()
        assertEquals(1, surveys?.size)
        assertEquals("survey1", surveys?.get(0)?.id)
        assertEquals("Test Survey", surveys?.get(0)?.name)

        val request = mockWebServer.takeRequest()
        assertEquals("/db/exams/_find", request.path)
        assertEquals("POST", request.method)
        assertTrue(request.getHeader("Authorization")?.startsWith("Basic") == true)
        assertEquals("cookie", request.getHeader("Cookie"))

        val expectedRequestBody = """{"selector":{"type":"surveys","teamId":"team1","isArchived":{"${'$'}exists":false}}}"""
        assertEquals(expectedRequestBody, request.body.readUtf8())
    }

    @Test
    fun getCompletionCountsWithDefaults_returnsMapWithZeros() = runTest {
        val jsonResponse = """
            {
              "docs": [
                {
                  "parentId": "survey1@team1"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val result = repository.getCompletionCountsWithDefaults(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = null,
            sessionCookie = null,
            teamId = "team1",
            surveyIds = listOf("survey1", "survey2")
        )

        assertEquals(1, result["survey1"])
        assertEquals(0, result["survey2"])
    }

    @Test
    fun getCompletionCountsWithDefaults_emptyIdsReturnsEmptyMap() = runTest {
        val result = repository.getCompletionCountsWithDefaults(
            baseUrl = "http://localhost",
            credentials = null,
            sessionCookie = null,
            teamId = "team1",
            surveyIds = emptyList()
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun getCompletionCountsWithDefaults_returnsDefaultsOnFailure() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val result = repository.getCompletionCountsWithDefaults(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = null,
            sessionCookie = null,
            teamId = "team1",
            surveyIds = listOf("survey1")
        )

        assertEquals(0, result["survey1"])
    }

    @Test
    fun fetchPublicSurvey_success() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "survey": {
                    "_id": "survey1",
                    "name": "Public Survey",
                    "questions": [
                      {
                        "body": "How are you?",
                        "type": "input",
                        "choices": []
                      }
                    ]
                  },
                  "team": {
                    "_id": "team1",
                    "name": "Public Team",
                    "type": "team"
                  }
                }
                """.trimIndent()
            )
        )

        val result = repository.fetchPublicSurvey(
            baseUrl = mockWebServer.url("/").toString(),
            teamId = "team1",
            surveyId = "survey1",
        )

        assertTrue(result.isSuccess)
        assertEquals("survey1", result.getOrNull()?.survey?.id)
        assertEquals("Public Survey", result.getOrNull()?.survey?.name)
        assertEquals("Public Team", result.getOrNull()?.team?.name)

        val request = mockWebServer.takeRequest()
        assertEquals("/api/public/surveys/team1/survey1", request.path)
        assertEquals("GET", request.method)
    }

    @Test
    fun fetchTeamSurveys_parsesMixedSurveyFieldTypes() = runTest {
        val jsonResponse = """
            {
              "docs": [
                {
                  "_id": "survey1",
                  "_rev": "1-rev",
                  "name": "Imported Survey",
                  "passingPercentage": "",
                  "createdDate": 1773949429414,
                  "totalMarks": 0,
                  "sourceSurveyId": "source1",
                  "teamId": "team1",
                  "questions": [
                    {
                      "body": "How are you?",
                      "type": "ratingScale",
                      "marks": "",
                      "correctChoice": [],
                      "choices": [],
                      "hasOtherOption": false,
                      "scaleMax": "5"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val result = repository.fetchTeamSurveys(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = null,
            sessionCookie = null,
            teamId = "team1"
        )

        assertTrue(result.isSuccess)
        val survey = result.getOrThrow().single()
        assertEquals("survey1", survey.id)
        assertEquals("1773949429414", survey.createdDate)
        assertEquals(null, survey.passingPercentage)
        assertEquals(0, survey.totalMarks)
        assertEquals(null, survey.questions?.single()?.marks)
        assertEquals(5, survey.questions?.single()?.scaleMax)
    }

    @Test
    fun fetchTeamSurveys_missingBaseUrl() = runTest {
        val result = repository.fetchTeamSurveys(
            baseUrl = "  ",
            credentials = null,
            sessionCookie = null,
            teamId = "team1"
        )
        assertTrue(result.isFailure)
        assertEquals("Missing server base URL", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchTeamSurveys_missingTeamId() = runTest {
        val result = repository.fetchTeamSurveys(
            baseUrl = "http://localhost",
            credentials = null,
            sessionCookie = null,
            teamId = "  "
        )
        assertTrue(result.isFailure)
        assertEquals("Missing team id", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchTeamSurveys_httpError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchTeamSurveys(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            teamId = "team1"
        )

        assertTrue(result.isFailure)
        assertEquals("Unexpected response 404", result.exceptionOrNull()?.message)
    }

    @Test
    fun fetchTeamSurveys_networkError() = runTest {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val baseUrl = mockWebServer.url("/").toString()
        val result = repository.fetchTeamSurveys(
            baseUrl = baseUrl,
            credentials = null,
            sessionCookie = null,
            teamId = "team1"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun await_onFailure_throwsException() = runTest {
        val mockCall = mock<Call>()
        val expectedException = IOException("Network error")

        doAnswer { invocation ->
            val callback = invocation.arguments[0] as Callback
            callback.onFailure(mockCall, expectedException)
            null
        }.`when`(mockCall).enqueue(any())

        val result = runCatching { mockCall.await() }

        assertTrue(result.isFailure)
        assertEquals(expectedException.message, result.exceptionOrNull()?.message)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun await_onResponse_returnsResponse() = runTest {
        val mockCall = mock<Call>()
        val mockRequest = Request.Builder().url("http://localhost/").build()
        val expectedResponse = Response.Builder()
            .request(mockRequest)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        doAnswer { invocation ->
            val callback = invocation.arguments[0] as Callback
            callback.onResponse(mockCall, expectedResponse)
            null
        }.`when`(mockCall).enqueue(any())

        val result = mockCall.await()

        assertEquals(expectedResponse, result)
    }

    @Test
    fun await_onCancellation_cancelsCall() = runTest {
        val mockCall = mock<Call>()

        // Suspend indefinitely on enqueue to allow cancellation
        doAnswer {
            // Do not invoke callback to keep coroutine suspended
            null
        }.`when`(mockCall).enqueue(any())

        val job = launch {
            mockCall.await()
        }

        // Wait for coroutine to start and suspend
        delay(10)

        // Cancel the job, triggering invokeOnCancellation
        job.cancel()
        job.join()

        verify(mockCall).cancel()
        assertTrue(job.isCancelled)
    }

    @Test
    fun await_onCancellation_ignoresCancelException() = runTest {
        val mockCall = mock<Call>()

        // Setup cancel() to throw an exception to test the catch block
        doThrow(kotlinx.coroutines.CancellationException("Cancel failed")).`when`(mockCall).cancel()

        // Suspend indefinitely on enqueue to allow cancellation
        doAnswer {
            // Do not invoke callback to keep coroutine suspended
            null
        }.`when`(mockCall).enqueue(any())

        val job = launch {
            mockCall.await()
        }

        // Wait for coroutine to start and suspend
        delay(10)

        // Cancel the job, triggering invokeOnCancellation
        job.cancel()
        job.join()

        // The test passes if no exception is thrown up, meaning the catch block worked
        assertTrue(job.isCancelled)
    }

    @Test
    fun await_onFailure_afterCancellation_isIgnored() = runTest {
        val mockCall = mock<Call>()
        lateinit var capturedCallback: Callback

        doAnswer { invocation ->
            capturedCallback = invocation.arguments[0] as Callback
            null
        }.`when`(mockCall).enqueue(any())

        val job = launch {
            mockCall.await()
        }

        delay(10)
        job.cancel()
        job.join()

        // Simulate OkHttp failing after cancellation has already occurred
        // The onFailure should just return and not throw or crash
        capturedCallback.onFailure(mockCall, IOException("Late failure"))

        assertTrue(job.isCancelled)
    }
}

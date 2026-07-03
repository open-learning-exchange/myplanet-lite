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
    fun loadSurveysState_offlineMode() = runTest {
        val mockLocalRepo = mock<org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository>()

        val doc1 = DashboardSurveysRepository.SurveyDocument(id = "s1", sourceSurveyId = null)
        val doc2 = DashboardSurveysRepository.SurveyDocument(id = "s2", sourceSurveyId = "adopted1")
        val outbox = listOf(mock<org.ole.planet.myplanet.lite.dashboard.DashboardSurveyOutboxStore.OutboxEntry>())

        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveysForTeam("team1")).thenReturn(listOf(doc1, doc2))
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveyIds()).thenReturn(setOf("s1"))
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveyRevisions()).thenReturn(mapOf("s1" to "rev1"))
        org.mockito.Mockito.`when`(mockLocalRepo.getPendingForTeam("team1")).thenReturn(outbox)

        val result = repository.loadSurveysState(
            baseUrl = "http://localhost",
            credentials = StoredCredentials("user", "pass"),
            sessionCookie = "cookie",
            teamId = "team1",
            offlineMode = true,
            localRepository = mockLocalRepo
        )

        if (result.isFailure) throw result.exceptionOrNull()!!
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertEquals(1, state.teamSurveys.size)
        assertEquals("s1", state.teamSurveys[0].id)

        assertEquals(1, state.adoptedSurveys.size)
        assertEquals("s2", state.adoptedSurveys[0].id)

        assertEquals(2, state.completionCounts.size)
        assertEquals(0, state.completionCounts["s1"])
        assertEquals(0, state.completionCounts["s2"])

        assertEquals(setOf("s1"), state.savedSurveyIds)
        assertEquals("rev1", state.savedSurveyRevisions["s1"])
        assertEquals(outbox, state.outboxEntries)

        // Ensure no network calls
        assertEquals(0, mockWebServer.requestCount)
    }

    @Test
    fun loadSurveysState_online_fetchFails_fallbackEmpty() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val mockLocalRepo = mock<org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository>()
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveysForTeam("team1")).thenReturn(emptyList())
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveyIds()).thenReturn(emptySet())
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveyRevisions()).thenReturn(emptyMap())
        org.mockito.Mockito.`when`(mockLocalRepo.getPendingForTeam("team1")).thenReturn(emptyList())

        val result = repository.loadSurveysState(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("user", "pass"),
            sessionCookie = "cookie",
            teamId = "team1",
            offlineMode = false,
            localRepository = mockLocalRepo
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("Error loading surveys and no cached versions available", result.exceptionOrNull()?.message)
    }

    @Test
    fun loadSurveysState_online_fetchFails_fallbackSuccess() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val mockLocalRepo = mock<org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository>()
        val doc1 = DashboardSurveysRepository.SurveyDocument(id = "s1")
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveysForTeam("team1")).thenReturn(listOf(doc1))
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveyIds()).thenReturn(emptySet())
        org.mockito.Mockito.`when`(mockLocalRepo.getSavedSurveyRevisions()).thenReturn(emptyMap())
        org.mockito.Mockito.`when`(mockLocalRepo.getPendingForTeam("team1")).thenReturn(emptyList())

        // Submissions API fallback counts
        val countsResponse = """{ "docs": [ { "parentId": "s1@x" } ] }"""
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(countsResponse))

        val result = repository.loadSurveysState(
            baseUrl = mockWebServer.url("/").toString(),
            credentials = StoredCredentials("user", "pass"),
            sessionCookie = "cookie",
            teamId = "team1",
            offlineMode = false,
            localRepository = mockLocalRepo
        )

        if (result.isFailure) throw result.exceptionOrNull()!!
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()
        assertEquals(1, state.teamSurveys.size)
        assertEquals(1, state.completionCounts["s1"])
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

        if (result.isFailure) throw result.exceptionOrNull()!!
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

        if (result.isFailure) throw result.exceptionOrNull()!!
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

        if (result.isFailure) throw result.exceptionOrNull()!!
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

package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

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
}

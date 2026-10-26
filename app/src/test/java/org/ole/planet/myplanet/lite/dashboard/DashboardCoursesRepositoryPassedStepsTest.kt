package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardCoursesRepositoryPassedStepsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardCoursesRepository
    private val credentials = StoredCredentials("learner", "pass")

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardCoursesRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `passed steps are collected per course from passed progress docs`() = runTest {
        val body = "{\"docs\": [" +
            "{\"_id\": \"a\", \"courseId\": \"course1\", \"stepNum\": 2, \"passed\": true}," +
            "{\"_id\": \"b\", \"courseId\": \"course1\", \"stepNum\": 2, \"passed\": true}," +
            "{\"_id\": \"c\", \"courseId\": \"course1\", \"stepNum\": 4, \"passed\": true}," +
            "{\"_id\": \"d\", \"courseId\": \"course1\", \"stepNum\": 3, \"passed\": false}," +
            "{\"_id\": \"e\", \"courseId\": \"course2\", \"stepNum\": 1, \"passed\": true}" +
            "]}"
        mockWebServer.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val result = repository.fetchCoursesPassedSteps(mockWebServer.url("/").toString(), credentials, listOf("course1", "course2"))

        assertEquals(setOf(2, 4), result.getOrThrow()["course1"])
        assertEquals(setOf(1), result.getOrThrow()["course2"])
        val request = mockWebServer.takeRequest()
        assertEquals("/db/courses_progress/_find", request.path)
        val sent = request.body.readUtf8()
        assertTrue(sent.contains("\"passed\":true"))
        assertTrue(sent.contains("\"userId\":\"org.couchdb.user:learner\""))
    }

    @Test
    fun `progress lookups without a pass filter don't send one`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("{\"docs\": []}").setResponseCode(200))

        repository.fetchCoursesProgressDocuments(mockWebServer.url("/").toString(), credentials, listOf("course1"))

        assertFalse(mockWebServer.takeRequest().body.readUtf8().contains("passed"))
    }

    @Test
    fun `course steps read passingRequired`() = runTest {
        val body = "{\"rows\": [{\"doc\": {\"_id\": \"course1\", \"courseTitle\": \"Course\", \"description\": \"\", \"steps\": [" +
            "{\"stepTitle\": \"Intro\"}," +
            "{\"stepTitle\": \"Quiz\", \"passingRequired\": true, \"exam\": {\"_id\": \"exam1\", \"questions\": [{\"body\": \"Q\", \"type\": \"select\"}]}}" +
            "]}}]}"
        mockWebServer.enqueue(MockResponse().setBody(body).setResponseCode(200))

        val course = repository.fetchCourses(mockWebServer.url("/").toString(), credentials, listOf("course1")).getOrThrow().single()

        assertNull(course.steps[0].passingRequired)
        assertEquals(true, course.steps[1].passingRequired)
    }
}

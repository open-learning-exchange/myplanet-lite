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
import kotlin.system.measureTimeMillis

class DashboardCoursesRepositoryFetchProgressTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardCoursesRepository

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
    fun benchmarkFetchCoursesProgress() = runTest {
        val courseIds = (1..5000).map { "course_$it" }

        // Mock single response
        val jsonResponse = "{\"docs\": []}"
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val credentials = StoredCredentials("testuser", "pass")

        val time = measureTimeMillis {
            val result = repository.fetchCoursesProgress(baseUrl, credentials, courseIds)
            assertTrue(result.isSuccess)
        }

        java.io.File("baseline_time.txt").writeText("fetchCoursesProgress baseline took $time ms")
    }

    @Test
    fun fetchCoursesProgress_largeBatch() = runTest {
        val courseIds = (1..50).map { "course_$it" }
        val jsonResponse = "{\"docs\": [" +
                "{\"_id\": \"doc1\", \"courseId\": \"course_1\", \"stepNum\": 1}," +
                "{\"_id\": \"doc2\", \"courseId\": \"course_2\", \"stepNum\": 2}" +
                "]}"
        mockWebServer.enqueue(MockResponse().setBody(jsonResponse).setResponseCode(200))

        val baseUrl = mockWebServer.url("/").toString()
        val credentials = StoredCredentials("testuser", "pass")

        val result = repository.fetchCoursesProgress(baseUrl, credentials, courseIds)

        assertTrue(result.isSuccess)
        val progressByCourse = result.getOrNull()!!
        assertEquals(1, progressByCourse["course_1"])
        assertEquals(2, progressByCourse["course_2"])
    }
}

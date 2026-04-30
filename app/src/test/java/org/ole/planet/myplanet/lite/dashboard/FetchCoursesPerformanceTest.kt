package org.ole.planet.myplanet.lite.dashboard

import java.util.regex.Pattern
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class FetchCoursesPerformanceTest {

    companion object {
        private val COURSE_PATTERN = Pattern.compile("course_\\d+")
    }

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardCoursesRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path ?: return MockResponse().setResponseCode(404)
                val body = request.body.readUtf8()

                return when {
                    path.contains("/_all_docs") -> {
                        val count = body.split("\"").count { it.startsWith("course_") }

                        val rows = (1..count).joinToString(",") { i ->
                            "{\"doc\": {\"_id\": \"course_$i\", \"courseTitle\": \"Course $i\"}}"
                        }
                        MockResponse().setBody("{\"rows\": [$rows]}").setResponseCode(200)
                    }
                    path.contains("/courses_progress/_find") -> {
                        val matcher = COURSE_PATTERN.matcher(body)
                        val courseIds = mutableListOf<String>()
                        while (matcher.find()) {
                            courseIds.add(matcher.group())
                        }
                        val docs = courseIds.joinToString(",") { id ->
                            "{\"_id\": \"cp_$id\", \"courseId\": \"$id\", \"stepNum\": 1}"
                        }
                        MockResponse().setBody("{\"docs\": [$docs]}").setResponseCode(200)
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        mockWebServer.start()
        repository = DashboardCoursesRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun benchmarkFetchCourses() = runTest {
        val courseIds = (1..5000).map { "course_$it" }
        val baseUrl = mockWebServer.url("/").toString()

        var fetchedCoursesCount = 0
        val time = measureTimeMillis {
            val result = repository.fetchCourses(
                baseUrl = baseUrl,
                credentials = StoredCredentials("testuser", "pass"),
                courseIds = courseIds
            )
            fetchedCoursesCount = result.getOrNull()?.size ?: 0
        }

        val requestCount = mockWebServer.requestCount
        println("=== BENCHMARK RESULT ===")
        System.err.println("Performance test result fetchCourses: ${time}ms, requests made: ${requestCount}, fetched: $fetchedCoursesCount")
        assertEquals(5000, fetchedCoursesCount)

        var fetchedProgressCount = 0
        val time2 = measureTimeMillis {
            val result = repository.fetchCoursesProgress(
                baseUrl = baseUrl,
                credentials = StoredCredentials("testuser", "pass"),
                courseIds = courseIds
            )
            fetchedProgressCount = result.getOrNull()?.size ?: 0
        }
        val requestCount2 = mockWebServer.requestCount - requestCount
        System.err.println("Performance test result fetchCoursesProgress: ${time2}ms, requests made: ${requestCount2}, fetched: $fetchedProgressCount")
        assertEquals(5000, fetchedProgressCount)

        var fetchedProgressDocsCount = 0
        val time3 = measureTimeMillis {
            val result = repository.fetchCoursesProgressDocuments(
                baseUrl = baseUrl,
                credentials = StoredCredentials("testuser", "pass"),
                courseIds = courseIds
            )
            fetchedProgressDocsCount = result.getOrNull()?.size ?: 0
        }
        val requestCount3 = mockWebServer.requestCount - requestCount - requestCount2
        System.err.println("Performance test result fetchCoursesProgressDocuments: ${time3}ms, requests made: ${requestCount3}, fetched: $fetchedProgressDocsCount")
        assertEquals(5000, fetchedProgressDocsCount)
    }
}

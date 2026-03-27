package org.ole.planet.myplanet.lite.dashboard

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
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class DashboardCoursesRepositoryBenchmark {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardCoursesRepository
    private val credentials = StoredCredentials("user", "pass")

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse()
                    .setResponseCode(200)
                    .setBody("""{"docs": [{"courseId": "c1", "stepNum": 1}, {"courseId": "c2", "stepNum": 2}]}""")
                    .setBodyDelay(100, TimeUnit.MILLISECONDS)
            }
        }
        mockWebServer.dispatcher = dispatcher
        mockWebServer.start()
        repository = DashboardCoursesRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun benchmarkFetchCoursesProgressDocuments() = runTest {
        val courseIds = (1..30).map { "course_$it" }
        val baseUrl = mockWebServer.url("/").toString()

        // Warmup
        repository.fetchCoursesProgressDocuments(baseUrl, credentials, listOf("warmup"))

        val timeTaken = measureTimeMillis {
            val result = repository.fetchCoursesProgressDocuments(baseUrl, credentials, courseIds)
            assert(result.isSuccess)
        }

        println("Benchmark result: ${timeTaken}ms")
    }
}

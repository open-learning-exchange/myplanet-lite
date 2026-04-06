package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.system.measureTimeMillis
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.After
import org.junit.Before

class DashboardTeamsRepositoryBenchmarkTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardTeamsRepository

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardTeamsRepository()
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun benchmarkBatch() = runBlocking {
        val n = 20
        // Enqueue a single response for batch request
        val responseDocs = (1..n).joinToString(",") { """{"_id":"mem_$it","teamId":"team_$it"}""" }
        val response = """{"docs":[$responseDocs]}"""
        mockWebServer.enqueue(MockResponse().setBody(response))

        val baseUrl = mockWebServer.url("/").toString()
        val teamIds = (1..n).map { "team_$it" }

        val timeBatch = measureTimeMillis {
            repository.fetchMemberCounts(baseUrl, null, null, teamIds)
        }

        println("===============================")
        println("Batch time: $timeBatch ms for $n queries combined")
        println("===============================")
    }
}

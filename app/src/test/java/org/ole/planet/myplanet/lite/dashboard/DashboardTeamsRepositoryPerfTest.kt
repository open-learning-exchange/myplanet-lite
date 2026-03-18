package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.system.measureTimeMillis
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardTeamsRepositoryPerfTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: DashboardTeamsRepository

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        repository = DashboardTeamsRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testFetchAllUsersPerformance() = runBlocking {
        // Enqueue the initial bulk find response
        val usersJson = """
            {
                "docs": [
                    ${(1..20).joinToString(",") { """{"_id": "org.couchdb.user:user$it", "name": "user$it", "planetCode": "planet1", "parentCode": "parent1", "firstName": "First$it", "lastName": "Last$it"}""" }}
                ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(usersJson))

        // Enqueue 20 responses for individual user fetches
        for (i in 1..20) {
            val userJson = """{"_id": "org.couchdb.user:user$i", "name": "user$i", "planetCode": "planet1", "parentCode": "parent1", "firstName": "First$i", "lastName": "Last$i"}"""
            mockWebServer.enqueue(MockResponse().setBody(userJson).setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS))
        }

        val credentials = StoredCredentials("test", "test")

        val time = measureTimeMillis {
            val result = repository.fetchAllUsers(
                baseUrl = mockWebServer.url("/").toString(),
                credentials = credentials,
                sessionCookie = null,
                planetCode = "planet1",
                parentCode = "parent1",
                pageSize = 20
            )
            println("Result size: ${result.getOrNull()?.size}")
        }

        println("Performance time: ${time}ms")
    }
}

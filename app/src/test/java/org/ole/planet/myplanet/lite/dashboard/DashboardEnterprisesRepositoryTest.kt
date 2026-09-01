package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardEnterprisesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DashboardEnterprisesRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = DashboardEnterprisesRepository(
            client = OkHttpClient(),
            dispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `snapshot queries active enterprises and user relationships`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"docs":[
                {"_id":"enterprise-1","name":"One","type":"enterprise","status":"active","teamType":"sync"},
                {"_id":"enterprise-2","name":"Two","type":"enterprise","status":"active","teamType":"sync"}
            ]}""".trimIndent(),
        ))
        server.enqueue(MockResponse().setBody(
            """{"docs":[
                {"_id":"membership-1","_rev":"1-a","teamId":"enterprise-1","userId":"org.couchdb.user:ana","userPlanetCode":"planet-a","docType":"membership","isLeader":true},
                {"_id":"request-old","_rev":"1-b","teamId":"enterprise-1","userId":"org.couchdb.user:ana","userPlanetCode":"planet-a","docType":"request"},
                {"_id":"request-2","_rev":"1-c","teamId":"enterprise-2","userId":"org.couchdb.user:ana","userPlanetCode":"planet-a","docType":"request"}
            ]}""".trimIndent(),
        ))

        val result = repository.fetchSnapshot(
            server.url("/").toString(),
            StoredCredentials("ana", "secret"),
            "AuthSession=cookie",
            "org.couchdb.user:ana",
            "planet-a",
        ).getOrThrow()

        assertEquals(2, result.enterprises.size)
        assertTrue(result.membershipsByEnterpriseId.getValue("enterprise-1").isLeader == true)
        assertFalse(result.requestsByEnterpriseId.containsKey("enterprise-1"))
        assertTrue(result.requestsByEnterpriseId.containsKey("enterprise-2"))

        val enterpriseRequest = server.takeRequest()
        val enterpriseSelector = JSONObject(enterpriseRequest.body.readUtf8()).getJSONObject("selector")
        assertEquals("enterprise", enterpriseSelector.getString("type"))
        assertEquals("active", enterpriseSelector.getString("status"))
        assertEquals("AuthSession=cookie", enterpriseRequest.getHeader("Cookie"))

        val relationshipRequest = server.takeRequest()
        val relationshipSelector = JSONObject(relationshipRequest.body.readUtf8()).getJSONObject("selector")
        assertEquals("org.couchdb.user:ana", relationshipSelector.getString("userId"))
        assertEquals("planet-a", relationshipSelector.getString("userPlanetCode"))
    }

    @Test
    fun `members remain private when current user has no membership`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"_id":"enterprise-1","name":"One","type":"enterprise","status":"active","teamPlanetCode":"planet-a"}""",
        ))
        server.enqueue(MockResponse().setBody("""{"docs":[]}"""))

        val result = repository.fetchEnterpriseMembers(
            server.url("/").toString(),
            StoredCredentials("ana", "secret"),
            "AuthSession=cookie",
            "enterprise-1",
            "org.couchdb.user:ana",
            "planet-a",
        ).getOrThrow()

        assertEquals(DashboardEnterprisesRepository.EnterpriseMembersResult.NotMember, result)
        assertEquals("/db/teams/enterprise-1", server.takeRequest().path)
        val membershipRequest = server.takeRequest()
        val body = JSONObject(membershipRequest.body.readUtf8())
        val selector = body.getJSONObject("selector")
        assertEquals("enterprise-1", selector.getString("teamId"))
        assertEquals("org.couchdb.user:ana", selector.getString("userId"))
        assertEquals("planet-a", selector.getString("userPlanetCode"))
        assertEquals("membership", selector.getString("docType"))
        assertEquals(1, body.getInt("limit"))
        assertEquals(0, server.requestCount - 2)
    }
}

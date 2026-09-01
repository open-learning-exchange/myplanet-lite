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
class DashboardEnterpriseTasksRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: DashboardEnterpriseTasksRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        repository = DashboardEnterpriseTasksRepository(OkHttpClient(), UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `fetches active tasks linked to enterprise and grants member actions`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"_id":"enterprise-1","type":"enterprise","status":"active","public":false,"teamType":"sync","teamPlanetCode":"planet-a"}""",
        ))
        server.enqueue(MockResponse().setBody(
            """{"docs":[{"_id":"membership-1","docType":"membership"}]}""",
        ))
        server.enqueue(MockResponse().setBody(
            """{"docs":[{"_id":"task-1","_rev":"1-a","title":"Report","deadline":2000,"completed":false,"link":{"teams":"enterprise-1"}}]}""",
        ))

        val result = repository.fetchTasks(
            server.url("/").toString(), StoredCredentials("ana", "secret"), "cookie",
            "enterprise-1", "org.couchdb.user:ana", "planet-a",
        ).getOrThrow() as DashboardEnterpriseTasksRepository.EnterpriseTasksSnapshot.Success

        assertTrue(result.canManage)
        assertEquals("task-1", result.tasks.single().id)
        assertEquals("/db/teams/enterprise-1", server.takeRequest().path)
        val membershipSelector = JSONObject(server.takeRequest().body.readUtf8()).getJSONObject("selector")
        assertEquals("membership", membershipSelector.getString("docType"))
        val taskRequest = server.takeRequest()
        assertEquals("/db/tasks/_find", taskRequest.path)
        val taskSelector = JSONObject(taskRequest.body.readUtf8()).getJSONObject("selector")
        assertEquals("enterprise-1", taskSelector.getString("link.teams"))
    }

    @Test
    fun `public enterprise tasks are visible but read only to nonmember`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"_id":"enterprise-1","type":"enterprise","status":"active","public":true,"teamType":"sync","teamPlanetCode":"planet-a"}""",
        ))
        server.enqueue(MockResponse().setBody("""{"docs":[]}"""))
        server.enqueue(MockResponse().setBody("""{"docs":[]}"""))

        val result = repository.fetchTasks(
            server.url("/").toString(), null, "cookie", "enterprise-1",
            "org.couchdb.user:ana", "planet-a",
        ).getOrThrow() as DashboardEnterpriseTasksRepository.EnterpriseTasksSnapshot.Success

        assertFalse(result.canManage)
        assertTrue(result.tasks.isEmpty())
    }

    @Test
    fun `fetch task loads current complete document for editing`() = runTest {
        server.enqueue(MockResponse().setBody(
            """{"_id":"task-1","_rev":"4-current","title":"Updated","description":"**Markdown**","deadline":2000,"completed":false,"customField":"preserved"}""",
        ))

        val task = repository.fetchTask(
            server.url("/").toString(), StoredCredentials("ana", "secret"), "cookie", "task-1",
        ).getOrThrow()

        assertEquals("4-current", task.revision)
        assertEquals("Updated", task.title)
        assertEquals("**Markdown**", task.description)
        assertEquals("preserved", JSONObject(task.raw).getString("customField"))
        assertEquals("/db/tasks/task-1", server.takeRequest().path)
    }
}

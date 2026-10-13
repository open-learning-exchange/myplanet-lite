package org.ole.planet.myplanet.lite.dashboard

import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class TeamEventEditorRepositoryTest {
    private val credentials = StoredCredentials("ana", "password")
    private val date = LocalDate.of(2026, 10, 12)
    private val draft = TeamEventDraft("Updated", "New **description**", date.plusDays(2), date.plusDays(3), "10:00", "11:30")
    private fun event(rev: String = "1-abc", creator: String = "ana") = JSONObject()
        .put("_id", "event-1").put("_rev", rev).put("title", "Original").put("description", "Agenda")
        .put("startDate", date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        .put("startTime", "09:00").put("endTime", "10:00").put("recurring", "none")
        .put("createdBy", creator).put("createdDate", 12345).put("sourcePlanet", "source-planet")
        .put("link", JSONObject().put("teams", "team-1").put("extra", "preserve"))
        .put("sync", JSONObject().put("type", "sync").put("planetCode", "owner-planet"))
        .put("otherField", JSONObject().put("preserve", true))
    private fun MockWebServer.body(value: Any) = enqueue(MockResponse().setBody(value.toString()))
    private fun user(roles: List<String> = emptyList(), admin: Boolean = false) = JSONObject()
        .put("name", "ana").put("roles", JSONArray(roles)).put("isUserAdmin", admin)

    @Test fun `creator edit sends full latest document preserving immutable and unknown fields`() = runTest {
        MockWebServer().use { server ->
            server.start(); val original = event()
            server.body(original); server.body(user())
            server.body("""{"ok":true,"id":"event-1","rev":"2-def"}""")
            val result = TeamEventEditorRepository().save(server.url("/").toString(), credentials, "cookie",
                "team-1", "event-1", "1-abc", draft).getOrThrow()
            assertEquals("2-def", result.revision)
            assertEquals("Updated", result.title)
            assertEquals("/db/meetups/event-1", server.takeRequest().path)
            assertTrue(server.takeRequest().path!!.startsWith("/db/_users/"))
            val request = server.takeRequest()
            assertEquals("/db/meetups", request.path); assertEquals("POST", request.method)
            val sent = JSONObject(request.body.readUtf8())
            for (key in listOf("_id", "_rev", "createdBy", "createdDate", "sourcePlanet", "link", "sync", "otherField")) {
                assertEquals(key, original.get(key).toString(), sent.get(key).toString())
            }
            assertEquals("New **description**", sent.getString("description"))
            assertEquals("cookie", request.getHeader("Cookie"))
        }
    }

    @Test fun `normal member cannot edit someone elses event`() = runTest {
        MockWebServer().use { server ->
            server.start(); server.body(event(creator = "juan")); server.body(user())
            val result = TeamEventEditorRepository().save(server.url("/").toString(), credentials, null,
                "team-1", "event-1", "1-abc", draft)
            assertTrue(result.exceptionOrNull() is CalendarAccessDeniedException)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun `leaders and admins cannot open or save someone elses event`() = runTest {
        for (kind in listOf("community", "admin", "team", "couchdb-admin")) {
            MockWebServer().use { server ->
                server.start()
                val roles = when (kind) {
                    "community" -> listOf("leader")
                    "couchdb-admin" -> listOf("_admin")
                    else -> emptyList()
                }
                val repository = TeamEventEditorRepository()
                repeat(2) { server.body(event(creator = "juan")); server.body(user(roles, kind == "admin")) }
                assertTrue(repository.load(server.url("/").toString(), credentials, null,
                    "team-1", "event-1").exceptionOrNull() is CalendarAccessDeniedException)
                assertTrue(repository.save(server.url("/").toString(), credentials, null,
                    "team-1", "event-1", "1-abc", draft).exceptionOrNull() is CalendarAccessDeniedException)
                assertEquals(4, server.requestCount)
                repeat(4) { assertEquals("GET", server.takeRequest().method) }
            }
        }
    }

    @Test fun `permissions and team association are checked again when saving`() = runTest {
        MockWebServer().use { server ->
            server.start(); server.body(event().put("link", JSONObject().put("teams", "another-team")))
            assertTrue(TeamEventEditorRepository().load(server.url("/").toString(), credentials, null,
                "team-1", "event-1").exceptionOrNull() is CalendarAccessDeniedException)
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun `changed revision returns latest document before posting instead of overwriting`() = runTest {
        MockWebServer().use { server ->
            server.start(); server.body(event("2-new")); server.body(user())
            val failure = TeamEventEditorRepository().save(server.url("/").toString(), credentials, null,
                "team-1", "event-1", "1-abc", draft).exceptionOrNull() as CalendarEditConflictException
            assertEquals("2-new", failure.latest.meetup.revision)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun `409 downloads latest revision and requires explicit retry`() = runTest {
        MockWebServer().use { server ->
            server.start(); server.body(event()); server.body(user())
            server.enqueue(MockResponse().setResponseCode(409).setBody("""{"error":"conflict"}"""))
            server.body(event("3-new")); server.body(user())
            val failure = TeamEventEditorRepository().save(server.url("/").toString(), credentials, null,
                "team-1", "event-1", "1-abc", draft).exceptionOrNull() as CalendarEditConflictException
            assertEquals("3-new", failure.latest.meetup.revision)
            assertEquals(5, server.requestCount)
            val paths = (0 until 5).map { server.takeRequest().path }
            assertEquals("/db/meetups/event-1", paths[3])
        }
    }
}

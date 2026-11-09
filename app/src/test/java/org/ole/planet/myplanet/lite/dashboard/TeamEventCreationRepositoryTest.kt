package org.ole.planet.myplanet.lite.dashboard

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import okhttp3.Credentials
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class TeamEventCreationRepositoryTest {
    private val credentials = StoredCredentials("juan", "password")
    private val draft = TeamEventDraft("Weekly meeting", "**Agenda**", LocalDate.now().plusDays(1), null, "9:00", "10:30",
        recurring = "daily", recurringNumber = "5", category = "General", link = "https://meet.example.org/reunion")

    @Test fun `create uses team sync metadata and local source and retains returned identity`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"_id":"team-123","teamType":"sync","teamPlanetCode":"owner-planet"}"""))
            server.enqueue(MockResponse().setBody("""{"ok":true,"id":"meetup-123","rev":"1-abc"}"""))
            val result = TeamCalendarRepository().create(server.url("/db/").toString(), credentials,
                "AuthSession=cookie", "team-123", "juan", "local-planet", draft).getOrThrow()
            assertEquals("meetup-123", result.id); assertEquals("1-abc", result.revision)
            assertEquals("**Agenda**", result.description)
            assertEquals("/db/teams/team-123", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("POST", request.method); assertEquals("/db/meetups", request.path)
            assertEquals(Credentials.basic("juan", "password"), request.getHeader("Authorization"))
            assertEquals("AuthSession=cookie", request.getHeader("Cookie"))
            val json = JSONObject(request.body.readUtf8())
            assertFalse(json.has("_id")); assertFalse(json.has("_rev"))
            assertEquals("juan", json.getString("createdBy"))
            assertEquals("local-planet", json.getString("sourcePlanet"))
            assertEquals("owner-planet", json.getJSONObject("sync").getString("planetCode"))
            assertEquals("sync", json.getJSONObject("sync").getString("type"))
            assertEquals("team-123", json.getJSONObject("link").getString("teams"))
            assertEquals("09:00", json.getString("startTime"))
            assertTrue(json.getLong("createdDate") > 0)
            assertTrue(json.isNull("endDate"))
            assertEquals(7, json.getJSONArray("day").length())
        }
    }

    @Test fun `creation failures are returned and invalid input never reaches server`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val repository = TeamCalendarRepository()
            assertTrue(repository.create(server.url("/").toString(), credentials, null,
                "team-123", "juan", "local", draft.copy(title = "")).isFailure)
            assertEquals(0, server.requestCount)
            server.enqueue(MockResponse().setBody("""{"_id":"team-123","teamType":"local","teamPlanetCode":"local"}"""))
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"forbidden"}"""))
            assertTrue(repository.create(server.url("/").toString(), credentials, null,
                "team-123", "juan", "local", draft).exceptionOrNull() is CalendarAccessDeniedException)
        }
    }

    @Test fun `missing owner metadata prevents posting incorrect sync planet`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"_id":"team-123","teamType":"sync"}"""))
            assertTrue(TeamCalendarRepository().create(server.url("/").toString(), credentials, null,
                "team-123", "juan", "local", draft).isFailure)
            assertEquals(1, server.requestCount)
        }
    }
}

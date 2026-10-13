package org.ole.planet.myplanet.lite.dashboard

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import java.time.LocalDate
import java.time.ZoneOffset

class TeamCalendarRepositoryTest {
    private val credentials = StoredCredentials("ana", "password")
    private fun timestamp(date: String) = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun meetup(date: String = "2026-10-05") = JSONObject()
        .put("_id", "meetup-1").put("_rev", "2-rev").put("title", "Meeting")
        .put("description", "Planning").put("startDate", timestamp(date)).put("endDate", timestamp(date))
        .put("startTime", "09:00").put("endTime", "10:30").put("recurring", "none")
        .put("link", JSONObject().put("teams", "team-1"))

    @Test
    fun `public team events use session and team selector and retain document revision`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"_id":"team-1","public":true}"""))
            val other = meetup().put("_id", "other").put("link", JSONObject().put("teams", "team-2"))
            server.enqueue(MockResponse().setBody("""{"docs":[${meetup()},$other]}"""))
            val result = TeamCalendarRepository().fetch(server.url("/").toString(), credentials, "AuthSession=cookie", "team-1")
            assertTrue(result.isSuccess)
            assertEquals("2-rev", result.getOrThrow().single().revision)
            val teamRequest = server.takeRequest()
            assertEquals("GET", teamRequest.method)
            assertEquals("/db/teams/team-1", teamRequest.path)
            val eventRequest = server.takeRequest()
            assertEquals("POST", eventRequest.method)
            assertEquals("/db/meetups/_find", eventRequest.path)
            assertEquals("AuthSession=cookie", eventRequest.getHeader("Cookie"))
            assertTrue(eventRequest.getHeader("Content-Type")!!.startsWith("application/json"))
            val query = JSONObject(eventRequest.body.readUtf8())
            assertEquals("team-1", query.getJSONObject("selector").getJSONObject("link").getString("teams"))
            assertEquals(1000, query.getInt("limit"))
        }
    }

    @Test
    fun `private team rejects nonmember before querying events`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"_id":"team-1","public":false}"""))
            server.enqueue(MockResponse().setBody("""{"docs":[]}"""))
            val result = TeamCalendarRepository().fetch(server.url("/db/").toString(), credentials, "cookie", "team-1")
            assertTrue(result.exceptionOrNull() is CalendarAccessDeniedException)
            assertEquals(2, server.requestCount)
            server.takeRequest()
            val membershipRequest = server.takeRequest()
            assertEquals(okhttp3.Credentials.basic(credentials.username, credentials.password), membershipRequest.getHeader("Authorization"))
            val query = JSONObject(membershipRequest.body.readUtf8()).getJSONObject("selector")
            assertEquals("team-1", query.getString("teamId"))
            assertEquals("org.couchdb.user:ana", query.getString("userId"))
            assertFalse(query.has("userPlanetCode"))
            assertFalse(query.has("status"))
        }
    }

    @Test
    fun `private team member receives events and deleted documents are excluded`() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("""{"_id":"team-1","public":false}"""))
            server.enqueue(MockResponse().setBody("""{"docs":[{"teamId":"team-1","userId":"org.couchdb.user:ana"}]}"""))
            server.enqueue(MockResponse().setBody("""{"docs":[${meetup()},${meetup().put("_deleted", true)}]}"""))
            assertEquals(1, TeamCalendarRepository().fetch(server.url("/").toString(), credentials, "cookie", "team-1").getOrThrow().size)
            assertEquals(3, server.requestCount)
        }
    }

    @Test
    fun `all day date range is inclusive and daily recurrence is bounded by count`() {
        val from = LocalDate.of(2026, 10, 1)
        val through = LocalDate.of(2026, 10, 31)
        val multi = meetup().put("endDate", timestamp("2026-10-07")).put("startTime", "").put("endTime", "").toTeamMeetup()!!
        val days = multi.occurrences(from, through, ZoneOffset.UTC)
        assertEquals(listOf(5, 6, 7), days.map { it.date.dayOfMonth })
        assertTrue(days.all { it.timeLabel.isEmpty() })
        val daily = meetup().put("recurring", "daily").put("recurringNumber", 5).toTeamMeetup()!!
        assertEquals(listOf(5, 6, 7, 8, 9), daily.occurrences(from, through, ZoneOffset.UTC).map { it.date.dayOfMonth })
    }

    @Test
    fun `weekly recurrence uses English weekdays and six occurrences`() {
        val weekly = meetup().put("recurring", "weekly").put("recurringNumber", 6)
            .put("day", org.json.JSONArray().put("Monday").put("Wednesday")).toTeamMeetup()!!
        val dates = weekly.occurrences(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), ZoneOffset.UTC)
        assertEquals(listOf(5, 7, 12, 14, 19, 21), dates.map { it.date.dayOfMonth })
        assertEquals("09:00 – 10:30", dates.first().timeLabel)
    }

    @Test
    fun `date timestamps preserve the device timezone`() {
        val local = java.time.ZoneId.of("America/Guatemala")
        val date = LocalDate.of(2026, 10, 5)
        val document = meetup().put("startDate", date.atStartOfDay(local).toInstant().toEpochMilli())
            .put("endDate", JSONObject.NULL).toTeamMeetup()!!
        assertEquals(date, document.occurrences(date, date, local).single().date)
    }
}

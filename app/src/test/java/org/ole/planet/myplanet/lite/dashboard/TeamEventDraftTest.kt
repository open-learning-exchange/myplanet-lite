package org.ole.planet.myplanet.lite.dashboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.ole.planet.myplanet.lite.R

class TeamEventDraftTest {
    private val date = LocalDate.of(2026, 10, 12)
    private val draft = TeamEventDraft("Meeting", "**Agenda**", date, date, "9:30", "10:30")

    private fun invalid(input: TeamEventDraft, error: Int) {
        val failure = runCatching { input.toJson() }.exceptionOrNull()
        assertTrue(failure is EventValidationException)
        assertEquals(error, (failure as EventValidationException).messageRes)
    }

    @Test fun `times are normalized and markdown retained without CouchDB identity`() {
        val json = draft.toJson(ZoneOffset.UTC)
        assertEquals("09:30", json.getString("startTime"))
        assertEquals("**Agenda**", json.getString("description"))
        assertEquals(10, json.getInt("recurringNumber"))
        assertEquals(0, json.getJSONArray("day").length())
        assertFalse(json.has("_id")); assertFalse(json.has("_rev"))
    }

    @Test fun `all day end is inclusive and absent end is null`() {
        val json = draft.copy(endDate = date.plusDays(2), startTime = "", endTime = "").toJson(ZoneOffset.UTC)
        assertEquals(date.plusDays(2).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), json.getLong("endDate"))
        assertEquals("", json.getString("startTime"))
        assertTrue(draft.copy(endDate = null).toJson().isNull("endDate"))
    }

    @Test fun `required content and reversed dates are rejected`() {
        invalid(draft.copy(title = "  "), R.string.calendar_required_fields)
        invalid(draft.copy(description = ""), R.string.calendar_required_fields)
        invalid(draft.copy(endDate = date.minusDays(1)), R.string.calendar_invalid_dates)
    }

    @Test fun `time dependencies formats and chronological order are enforced`() {
        listOf(
            draft.copy(startTime = "", endTime = "10:30"),
            draft.copy(endDate = date.plusDays(1), endTime = ""),
            draft.copy(endTime = "08:30"),
            draft.copy(startTime = "25:00"),
            draft.copy(startTime = "9:3"),
            draft.copy(endTime = "10:60"),
        ).forEach { invalid(it, R.string.calendar_invalid_times) }
        assertEquals("", draft.copy(endTime = "").toJson().getString("endTime"))
        assertEquals("08:30", draft.copy(endDate = date.plusDays(1), endTime = "08:30").toJson().getString("endTime"))
    }

    @Test fun `recurrence requires integer counts and weekly days and stores English names`() {
        listOf("1", "2.5", "", "abc").forEach {
            invalid(draft.copy(recurring = "daily", recurringNumber = it), R.string.calendar_invalid_recurrence)
        }
        invalid(draft.copy(recurring = "weekly"), R.string.calendar_invalid_recurrence)
        invalid(draft.copy(recurring = "monthly"), R.string.calendar_invalid_recurrence)
        val daily = draft.copy(recurring = "daily", recurringNumber = "5").toJson()
        assertEquals(5, daily.getInt("recurringNumber"))
        assertEquals(7, daily.getJSONArray("day").length())
        assertEquals("Sunday", daily.getJSONArray("day").getString(0))
        val weekly = draft.copy(recurring = "weekly", recurringNumber = "6", days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)).toJson()
        assertEquals("[\"Monday\",\"Wednesday\"]", weekly.getJSONArray("day").toString())
    }

    @Test fun `optional links require HTTP scheme valid domain and no spaces`() {
        listOf("example.org/reunion", "www.example.org", "https:// reunión.example.org",
            "ftp://example.org", "https://example", "https://-bad.example.org", "https://example.org/a b").forEach {
            invalid(draft.copy(link = it), R.string.calendar_invalid_link)
        }
        listOf("", "https://example.org/reunion", "http://meet.example.org/sala/123").forEach {
            assertEquals(it, draft.copy(link = it).toJson().getString("meetupLink"))
        }
        invalid(draft.copy(category = "Other"), R.string.calendar_invalid_category)
        meetupCategories.forEach { assertEquals(it, draft.copy(category = it).toJson().getString("category")) }
    }
}

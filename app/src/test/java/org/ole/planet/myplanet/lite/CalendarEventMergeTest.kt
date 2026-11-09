package org.ole.planet.myplanet.lite

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarEventMergeTest {
    private val date = LocalDate.of(2026, 10, 12)
    private val planet = TeamMeetup("event-1", "1-rev", "Meeting", "Agenda",
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), null, "09:00", "10:00", "none", 10,
        emptySet(), "Room", "", "ana")
    private val base = "https://planet.example"
    private fun google(marker: Boolean = false): ExternalCalendarEvent {
        val export = planet.exportOccurrence(date, "Agenda")
        return ExternalCalendarEvent("10", export.title, export.beginMillis, export.endMillis!!, export.allDay,
            export.description + if (marker) "\n\n" + planetGoogleMarker(base, "team-1", planet.id, date.toString()) else "",
            export.location, "Personal")
    }
    @Test fun `Planet wins over marked and older Google copies while unrelated events remain`() {
        for (marked in listOf(true, false)) {
            val items = google(marked).occurrences(date, date) + google().copy(id = "99", title = "Personal event").occurrences(date, date)
            val merged = preferPlanetCalendarEvents(base, "team-1", planet.occurrences(date, date), items, setOf(planet.id)) { it }
            assertEquals(2, merged.size)
            assertEquals(planet.id, merged[0].id)
            assertEquals("Personal event", merged[1].title)
        }
    }
    @Test fun `Planet also wins when Google copy has different title or dates`() {
        val copy = google(true).copy(title = "Outdated Google title")
        val merged = preferPlanetCalendarEvents(base, "team-1", planet.occurrences(date, date), copy.occurrences(date, date), setOf(planet.id)) { it }
        assertEquals(listOf(planet.id), merged.map { it.id })
        val fallback = preferPlanetCalendarEvents(base, "team-1", emptyList(), copy.occurrences(date, date), emptySet()) { it }
        assertEquals(1, fallback.size)
    }
    @Test fun `pending deleted copies stay hidden and other teams are not suppressed`() {
        val items = google(true).occurrences(date, date)
        assertTrue(preferPlanetCalendarEvents(base, "team-1", emptyList(), items, emptySet(), setOf("10")) { it }.isEmpty())
        assertEquals(1, preferPlanetCalendarEvents(base, "other-team", emptyList(), items, setOf(planet.id)) { it }.size)
    }
}

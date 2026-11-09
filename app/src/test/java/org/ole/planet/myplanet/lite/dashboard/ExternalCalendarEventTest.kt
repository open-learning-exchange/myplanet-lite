package org.ole.planet.myplanet.lite.dashboard

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class ExternalCalendarEventTest {
    private val from = LocalDate.of(2026, 10, 1)
    private val through = LocalDate.of(2026, 10, 31)
    private fun event(start: String, end: String, allDay: Boolean) = ExternalCalendarEvent(
        "1", "Google meeting", Instant.parse(start).toEpochMilli(), Instant.parse(end).toEpochMilli(),
        allDay, "Private description", "Online", "Personal")

    @Test fun `all day dates use UTC and exclusive end without shifting in Guatemala`() {
        val events = event("2026-10-12T00:00:00Z", "2026-10-14T00:00:00Z", true)
            .occurrences(from, through, ZoneId.of("America/Guatemala"))
        assertEquals(listOf(12, 13), events.map { it.date.dayOfMonth })
        assertTrue(events.all { it.timeLabel.isBlank() && it.sourceCalendar == "Personal" })
        assertTrue(events.all { it.id.startsWith("google:") })
    }

    @Test fun `timed occurrences respect device timezone and midnight exclusion`() {
        val events = event("2026-10-13T05:00:00Z", "2026-10-13T06:00:00Z", false)
            .occurrences(from, through, ZoneId.of("America/Guatemala"))
        assertEquals(12, events.single().date.dayOfMonth)
        assertEquals("23:00 – 00:00", events.single().timeLabel)
    }

    @Test fun `separate recurring instances retain different identities and range is bounded`() {
        val one = event("2026-10-12T12:00:00Z", "2026-10-12T13:00:00Z", false)
        val two = event("2026-10-13T12:00:00Z", "2026-10-13T13:00:00Z", false)
        assertNotEquals(one.occurrences(from, through).single().id, two.occurrences(from, through).single().id)
        assertTrue(one.occurrences(LocalDate.of(2026, 10, 14), through).isEmpty())
    }
}

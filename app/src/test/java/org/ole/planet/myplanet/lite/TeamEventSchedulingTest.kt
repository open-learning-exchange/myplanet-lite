package org.ole.planet.myplanet.lite

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.EventValidationException
import org.ole.planet.myplanet.lite.dashboard.TeamEventDraft

class TeamEventSchedulingTest {
    private val now = LocalDateTime.of(2026, 10, 13, 14, 35, 40)
    private val today = now.toLocalDate()
    private val zone = ZoneId.of("America/Guatemala")
    private fun draft(date: LocalDate, start: String = "", end: String = "") =
        TeamEventDraft("Meeting", "Agenda", date, null, start, end)

    @Test fun `new events reject yesterday but accept all day today`() {
        val error = runCatching { draft(today.minusDays(1)).toCreationJson(zone, now) }.exceptionOrNull()
        assertEquals(R.string.calendar_past_date, (error as EventValidationException).messageRes)
        assertEquals("", draft(today).toCreationJson(zone, now).getString("startTime"))
    }

    @Test fun `today permits current minute or later and future days permit morning times`() {
        val error = runCatching { draft(today, "14:34").toCreationJson(zone, now) }.exceptionOrNull()
        assertEquals(R.string.calendar_past_time, (error as EventValidationException).messageRes)
        assertEquals("14:35", draft(today, "14:35").toCreationJson(zone, now).getString("startTime"))
        assertEquals("16:00", draft(today, "16:00").toCreationJson(zone, now).getString("startTime"))
        assertEquals("09:00", draft(today.plusDays(1), "9:00").toCreationJson(zone, now).getString("startTime"))
    }

    @Test fun `save revalidates expired choices and midnight rollover`() {
        val input = draft(today, "14:35")
        assertTrue(runCatching { input.toCreationJson(zone, now) }.isSuccess)
        assertTrue(runCatching { input.toCreationJson(zone, now.plusMinutes(1)) }.isFailure)
        assertTrue(runCatching { draft(today).toCreationJson(zone, today.plusDays(1).atStartOfDay()) }.isFailure)
    }

    @Test fun `time picker minimum combines date current time and start of event`() {
        assertNull(minimumEventTime(today.minusDays(1), null, now))
        assertEquals(LocalTime.of(14, 35), minimumEventTime(today, null, now))
        assertEquals(LocalTime.MIDNIGHT, minimumEventTime(today.plusDays(1), null, now))
        assertEquals(LocalTime.of(16, 0), minimumEventTime(today, LocalTime.of(16, 0), now))
        assertEquals(LocalTime.of(14, 35), minimumEventTime(today, LocalTime.of(9, 0), now))
        assertEquals(LocalTime.of(9, 0), minimumEventTime(today.plusDays(1), LocalTime.of(9, 0), now))
    }
}

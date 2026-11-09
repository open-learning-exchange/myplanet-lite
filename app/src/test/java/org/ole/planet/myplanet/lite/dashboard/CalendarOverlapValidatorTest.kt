package org.ole.planet.myplanet.lite.dashboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class CalendarOverlapValidatorTest {
    private val date = LocalDate.of(2027, 1, 4)
    private val zone = ZoneId.systemDefault()
    private fun event(start: String = "09:00", end: String = "10:00") = TeamMeetup(
        "id", "rev", "Meeting", "Agenda", date.atStartOfDay(zone).toInstant().toEpochMilli(),
        null, start, end, "none", 10, emptySet(), "", "")

    @Test fun `overlap includes containment but excludes adjacent appointments`() {
        val existing = event().timeRanges().single()
        assertTrue(existing.overlaps(event("09:30", "10:30").timeRanges().single()))
        assertTrue(existing.overlaps(event("09:15", "09:45").timeRanges().single()))
        assertFalse(existing.overlaps(event("10:00", "11:00").timeRanges().single()))
        assertFalse(existing.overlaps(event("08:00", "09:00").timeRanges().single()))
    }
    @Test fun `all day range includes its final day and excludes following day`() {
        val allDay = event("", "").copy(endDate = date.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli())
            .timeRanges().single()
        val lastDay = event().copy(startDate = date.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli())
        assertTrue(allDay.overlaps(lastDay.timeRanges().single()))
        assertFalse(allDay.overlaps(lastDay.copy(startDate = date.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli())
            .timeRanges().single()))
    }
    @Test fun `weekly and daily recurrence check later appearances`() {
        val weekly = event().copy(recurring = "weekly", recurringNumber = 4,
            days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)).timeRanges().toList()
        assertEquals(4, weekly.size)
        val secondWeek = event().copy(startDate = date.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli())
        assertTrue(weekly.any { it.overlaps(secondWeek.timeRanges().single()) })
        val daily = event().copy(recurring = "daily", recurringNumber = 3).timeRanges().toList()
        assertEquals(3, daily.size)
        assertFalse(daily.any { it.overlaps(secondWeek.timeRanges().single()) })
    }
    @Test fun `Google all day UTC dates represent local whole days`() {
        val google = ExternalCalendarEvent("1", "Day", date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true, "", "", "Google")
        val guatemala = ZoneId.of("America/Guatemala")
        val range = google.timeRange(guatemala)
        assertEquals(date.atStartOfDay(guatemala).toInstant().toEpochMilli(), range.start)
        assertEquals(date.plusDays(1).atStartOfDay(guatemala).toInstant().toEpochMilli(), range.end)
    }
    @Test fun `overnight events conflict on the following morning`() {
        val overnight = event("23:00", "02:00").copy(
            endDate = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()).timeRanges().single()
        val morning = event("01:00", "03:00").copy(startDate = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
        assertTrue(overnight.overlaps(morning.timeRanges().single()))
    }
}

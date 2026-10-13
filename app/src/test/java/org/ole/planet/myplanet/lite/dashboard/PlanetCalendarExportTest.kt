package org.ole.planet.myplanet.lite.dashboard

import android.content.Intent
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlanetCalendarExportTest {
    private val zone = ZoneId.of("America/Guatemala")
    private val first = LocalDate.of(2026, 10, 12)
    private fun timestamp(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
    private fun meetup() = TeamMeetup("planet-1", "1-rev", "Reunión semanal", "**Agenda**",
        timestamp(first), timestamp(first), "9:30", "10:30", "daily", 5, emptySet(),
        "Salón principal", "https://meet.example.org/room?a=1&b=2")

    @Test fun `timed export opens Google review with local times and original metadata`() {
        val export = meetup().exportOccurrence(first.plusDays(2), "Agenda", zone)
        val intent = export.appIntent()
        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals("com.google.android.calendar", intent.`package`)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals("Reunión semanal", intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals("Salón principal", intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertEquals("Agenda\n\nhttps://meet.example.org/room?a=1&b=2", intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertEquals(Instant.parse("2026-10-14T15:30:00Z").toEpochMilli(), export.beginMillis)
        assertEquals(Instant.parse("2026-10-14T16:30:00Z").toEpochMilli(), export.endMillis)
        assertFalse(intent.hasExtra(CalendarContract.Events.RRULE))
        assertFalse(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true))
    }

    @Test fun `all day export converts inclusive Planet end to exclusive UTC end`() {
        val original = meetup().copy(startTime = "", endTime = "", endDate = timestamp(first.plusDays(2)))
        val selected = first.plusDays(7)
        val export = original.exportOccurrence(selected, "Agenda", zone)
        assertEquals(selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), export.beginMillis)
        assertEquals(selected.plusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), export.endMillis)
        assertTrue(export.allDay)
        assertEquals("20261019/20261022", export.webIntent().data!!.getQueryParameter("dates"))
    }

    @Test fun `web fallback retains Unicode query parameters and UTC instants`() {
        val intent = meetup().exportOccurrence(first, "Agenda & goals", zone).webIntent()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        val uri = intent.data!!
        assertEquals("https", uri.scheme)
        assertEquals("calendar.google.com", uri.host)
        assertEquals("TEMPLATE", uri.getQueryParameter("action"))
        assertEquals("Reunión semanal", uri.getQueryParameter("text"))
        assertEquals("Agenda & goals\n\nhttps://meet.example.org/room?a=1&b=2", uri.getQueryParameter("details"))
        assertEquals("20261012T153000Z/20261012T163000Z", uri.getQueryParameter("dates"))
        assertEquals("America/Guatemala", uri.getQueryParameter("ctz"))
    }

    @Test fun `unspecified end is not invented and invalid legacy times are rejected`() {
        val export = meetup().copy(endTime = "").exportOccurrence(first, "Agenda", zone)
        assertNull(export.endMillis)
        assertFalse(export.appIntent().hasExtra(CalendarContract.EXTRA_EVENT_END_TIME))
        assertTrue(runCatching { meetup().copy(startTime = "25:00").exportOccurrence(first, "Agenda", zone) }.isFailure)
        assertTrue(runCatching { meetup().copy(endTime = "08:00").exportOccurrence(first, "Agenda", zone) }.isFailure)
    }

    @Test fun `multi day cards carry the original occurrence so exports do not shift its dates`() {
        val original = meetup().copy(recurring = "none", endDate = timestamp(first.plusDays(2)))
        val cards = original.occurrences(first, first.plusDays(5), zone)
        assertEquals(3, cards.size)
        assertTrue(cards.all { it.planetMeetup == original && it.occurrenceStart == first })
        val exports = cards.map { it.planetMeetup!!.exportOccurrence(it.occurrenceStart!!, "Agenda", zone) }
        assertEquals(1, exports.distinct().size)
    }
}

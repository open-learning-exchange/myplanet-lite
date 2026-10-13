package org.ole.planet.myplanet.lite.dashboard

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.ole.planet.myplanet.lite.TeamCalendarEvent

/** Read-only occurrence from an external calendar. End timestamps are exclusive. */
internal data class ExternalCalendarEvent(
    val id: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val description: String,
    val location: String,
    val calendarName: String,
) {
    fun occurrences(from: LocalDate, through: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TeamCalendarEvent> {
        val eventZone = if (allDay) ZoneOffset.UTC else zone
        val start = Instant.ofEpochMilli(startMillis).atZone(eventZone)
        val end = Instant.ofEpochMilli(endMillis.coerceAtLeast(startMillis)).atZone(eventZone)
        val finalDate = if (endMillis > startMillis) end.minusNanos(1).toLocalDate() else start.toLocalDate()
        val first = maxOf(from, start.toLocalDate())
        val last = minOf(through, finalDate)
        if (first > last) return emptyList()
        val format = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT)
        val time = if (allDay) "" else start.format(format) + " – " + end.format(format)
        return generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { date ->
            TeamCalendarEvent("google:$id:$startMillis", date, title, time,
                description, sourceCalendar = calendarName, location = location)
        }.toList()
    }
}

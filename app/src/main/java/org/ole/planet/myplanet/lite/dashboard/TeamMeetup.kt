package org.ole.planet.myplanet.lite.dashboard

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONObject
import org.ole.planet.myplanet.lite.TeamCalendarEvent

internal data class TeamMeetup(
    val id: String,
    val revision: String,
    val title: String,
    val description: String,
    val startDate: Long,
    val endDate: Long?,
    val startTime: String,
    val endTime: String,
    val recurring: String,
    val recurringNumber: Int,
    val days: Set<DayOfWeek>,
    val location: String,
    val link: String,
    val createdBy: String = "",
) {
    fun occurrences(from: LocalDate, through: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TeamCalendarEvent> {
        val start = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
        val end = endDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: start
        val duration = ChronoUnit.DAYS.between(start, end).coerceAtLeast(0)
        val count = recurringNumber.coerceAtLeast(0)
        val firstCandidate = maxOf(start, from.minusDays(duration))
        if (firstCandidate > through) return emptyList()
        val starts = when (recurring) {
            "daily" -> generateSequence(firstCandidate) { it.plusDays(1) }
                .takeWhile { it <= through && ChronoUnit.DAYS.between(start, it) < count }
            "weekly" -> generateSequence(firstCandidate) { it.plusDays(1) }
                .takeWhile { it <= through }
                .filter { date -> date.dayOfWeek in days && weeklyOccurrenceNumber(start, date) <= count }
            else -> sequenceOf(start).filter { it <= through && it.plusDays(duration) >= from }
        }
        val detail = listOf(description, location).filter(String::isNotBlank).joinToString("\n")
        val time = listOf(startTime, endTime).filter(String::isNotBlank).joinToString(" – ")
        return starts.flatMap { occurrence ->
            val first = maxOf(from, occurrence)
            val last = minOf(through, occurrence.plusDays(duration))
            generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.map { day ->
                TeamCalendarEvent(id, day, title, time, detail, meetupLink = link,
                    planetMeetup = this, occurrenceStart = occurrence)
            }
        }.toList()
    }

    private fun weeklyOccurrenceNumber(start: LocalDate, date: LocalDate): Long {
        val elapsed = ChronoUnit.DAYS.between(start, date) + 1
        val fullWeeks = elapsed / 7
        val remaining = elapsed % 7
        return fullWeeks * days.size + (0L until remaining).count { start.dayOfWeek.plus(it) in days }
    }
}

internal fun JSONObject.toTeamMeetup(): TeamMeetup? {
    val id = optString("_id").takeIf(String::isNotBlank) ?: return null
    if (!has("startDate") || isNull("startDate")) return null
    val start = optLong("startDate", Long.MIN_VALUE)
    if (start == Long.MIN_VALUE) return null
    val rawDays = optJSONArray("day")
    val days = (0 until (rawDays?.length() ?: 0)).mapNotNull { index ->
        runCatching { DayOfWeek.valueOf(rawDays!!.getString(index).uppercase(java.util.Locale.ROOT)) }.getOrNull()
    }.toSet()
    fun text(key: String) = if (isNull(key)) "" else optString(key)
    return TeamMeetup(id, text("_rev"), text("title"), text("description"), start,
        if (has("endDate") && !isNull("endDate")) optLong("endDate") else null,
        text("startTime"), text("endTime"), text("recurring"), optInt("recurringNumber", 10), days,
        text("meetupLocation"), text("meetupLink"), text("createdBy"))
}

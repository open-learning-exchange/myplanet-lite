package org.ole.planet.myplanet.lite.dashboard

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.googleCalendarDescription

internal fun linkedPlanetReference(description: String, base: String, team: String): Pair<String, LocalDate>? {
    val plain = googleCalendarDescription(description, "").toString()
    val urls = Regex("Planet: (https?://[^\\s]+)").findAll(plain)
    for (match in urls) {
        val uri = Uri.parse(match.groupValues[1])
        val event = uri.lastPathSegment ?: continue
        val expected = planetGoogleMarker(base, team, event, "").substringAfter("Planet: ").substringBefore('?')
        if (uri.buildUpon().clearQuery().fragment(null).build().toString() != expected) continue
        if (uri.getQueryParameter("team") != team) continue
        val date = runCatching { LocalDate.parse(uri.getQueryParameter("date")) }.getOrNull() ?: continue
        return event to date
    }
    return null
}
internal fun linkedOccurrence(description: String, base: String, team: String, event: String): LocalDate? =
    linkedPlanetReference(description, base, team)?.takeIf { it.first == event }?.second

internal fun TeamMeetup.startDay(zone: ZoneId = ZoneId.systemDefault()) = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
internal fun TeamMeetup.occurrenceOrdinal(date: LocalDate): Int? {
    val start = startDay()
    if (date < start) return null
    val difference = ChronoUnit.DAYS.between(start, date)
    val index = when (recurring) {
        "daily" -> difference
        "weekly" -> {
            if (date.dayOfWeek !in days) return null
            val elapsed = difference + 1
            (elapsed / 7) * days.size + (0L until elapsed % 7).count { start.dayOfWeek.plus(it) in days } - 1
        }
        else -> if (date == start) 0L else return null
    }
    return index.takeIf { it >= 0 && (recurring == "none" || recurring.isBlank() || it < recurringNumber) }?.toInt()
}
internal fun TeamMeetup.occurrenceAt(index: Int): LocalDate? {
    if (index < 0) return null
    val start = startDay()
    return when (recurring) {
        "daily" -> if (index < recurringNumber) start.plusDays(index.toLong()) else null
        "weekly" -> {
            val offsets = (0L..6L).filter { start.dayOfWeek.plus(it) in days }
            if (offsets.isEmpty() || index >= recurringNumber) null
            else start.plusDays((index / offsets.size) * 7L + offsets[index % offsets.size])
        }
        else -> start.takeIf { index == 0 }
    }
}
internal fun TeamMeetup.syncJson() = JSONObject().put("_id", id).put("_rev", revision).put("title", title)
    .put("description", description).put("startDate", startDate).put("endDate", endDate ?: JSONObject.NULL)
    .put("startTime", startTime).put("endTime", endTime).put("recurring", recurring)
    .put("recurringNumber", recurringNumber).put("day", JSONArray(days.map { it.name }))
    .put("meetupLocation", location).put("meetupLink", link).put("createdBy", createdBy)

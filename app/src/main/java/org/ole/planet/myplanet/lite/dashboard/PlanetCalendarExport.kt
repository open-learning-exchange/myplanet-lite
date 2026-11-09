package org.ole.planet.myplanet.lite.dashboard

import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** A single occurrence, handed to Google Calendar for the user to review and save. */
internal data class PlanetCalendarExport(
    val title: String,
    val description: String,
    val location: String,
    val beginMillis: Long,
    val endMillis: Long?,
    val allDay: Boolean,
    val zoneId: String,
) {
    fun appIntent(): Intent = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
        .setPackage("com.google.android.calendar")
        .putExtra(CalendarContract.Events.TITLE, title)
        .putExtra(CalendarContract.Events.DESCRIPTION, description)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, location)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
        .putExtra(CalendarContract.Events.EVENT_TIMEZONE, zoneId)
        .apply { endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) } }

    fun webIntent(): Intent {
        val format = if (allDay) DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT)
            else DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).format(format)
        val uri = Uri.parse("https://calendar.google.com/calendar/render").buildUpon()
            .appendQueryParameter("action", "TEMPLATE")
            .appendQueryParameter("text", title)
            .appendQueryParameter("details", description)
            .appendQueryParameter("location", location)
            .appendQueryParameter("dates", date(beginMillis) + "/" + date(endMillis ?: beginMillis))
            .appendQueryParameter("ctz", zoneId).build()
        return Intent(Intent.ACTION_VIEW, uri)
    }
}

internal fun TeamMeetup.exportOccurrence(
    occurrenceStart: LocalDate,
    plainDescription: String,
    zone: ZoneId = ZoneId.systemDefault(),
): PlanetCalendarExport {
    val originalStart = Instant.ofEpochMilli(startDate).atZone(zone).toLocalDate()
    val originalEnd = endDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: originalStart
    val last = occurrenceStart.plusDays(ChronoUnit.DAYS.between(originalStart, originalEnd).coerceAtLeast(0))
    val allDay = startTime.isBlank() && endTime.isBlank()
    val format = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
    val begin = if (allDay) occurrenceStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else occurrenceStart.atTime(LocalTime.parse(startTime, format)).atZone(zone).toInstant().toEpochMilli()
    val end = if (allDay) last.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        else endTime.takeIf(String::isNotBlank)?.let {
            last.atTime(LocalTime.parse(it, format)).atZone(zone).toInstant().toEpochMilli()
        }
    require(end == null || end >= begin)
    return PlanetCalendarExport(title, listOf(plainDescription, link).filter(String::isNotBlank).joinToString("\n\n"),
        location, begin, end, allDay, if (allDay) "UTC" else zone.id)
}

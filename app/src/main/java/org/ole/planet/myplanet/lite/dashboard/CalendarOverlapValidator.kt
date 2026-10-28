package org.ole.planet.myplanet.lite.dashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal data class CalendarTimeRange(val start: Long, val end: Long) {
    fun overlaps(other: CalendarTimeRange): Boolean = start < other.end && other.start < end
}

/** Half-open intervals: adjacent appointments are allowed; all-day final dates are inclusive. */
internal fun TeamMeetup.timeRanges(zone: ZoneId = ZoneId.systemDefault()): Sequence<CalendarTimeRange> {
    val count = if (recurring in listOf("daily", "weekly")) recurringNumber else 1
    val first = startDay(zone)
    val last = endDate?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: first
    val duration = java.time.temporal.ChronoUnit.DAYS.between(first, last).coerceAtLeast(0)
    val format = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)
    return (0 until count).asSequence().mapNotNull { occurrenceAt(it) }.map { date ->
        val allDay = startTime.isBlank()
        val start = if (allDay) date.atStartOfDay(zone) else date.atTime(LocalTime.parse(startTime, format)).atZone(zone)
        val end = when {
            allDay -> date.plusDays(duration + 1).atStartOfDay(zone)
            endTime.isNotBlank() -> date.plusDays(duration).atTime(LocalTime.parse(endTime, format)).atZone(zone)
            else -> start.plusMinutes(1)
        }
        val begin = start.toInstant().toEpochMilli()
        CalendarTimeRange(begin, end.toInstant().toEpochMilli().coerceAtLeast(begin + 1))
    }
}

internal fun ExternalCalendarEvent.timeRange(zone: ZoneId = ZoneId.systemDefault()): CalendarTimeRange {
    fun localBoundary(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()
    val begin = if (allDay) localBoundary(startMillis) else startMillis
    val end = if (allDay) localBoundary(endMillis) else endMillis
    return CalendarTimeRange(begin, end.coerceAtLeast(begin + 1))
}

internal class CalendarOverlapValidator(private val context: Context) {
    suspend fun validate(base: String, credentials: StoredCredentials, cookie: String?, team: String,
        draft: TeamEventDraft, allowOverlaps: Boolean = false) = withContext(Dispatchers.IO) {
        val candidate = requireNotNull(draft.toCreationJson().put("_id", "new-event").toTeamMeetup())
        val ranges = candidate.timeRanges().toList()
        val existing = TeamCalendarRepository().fetch(base, credentials, cookie, team).getOrThrow()
        if (!allowOverlaps && existing.any { event -> event.timeRanges().any { old -> ranges.any { it.overlaps(old) } } }) {
            throw EventValidationException(R.string.calendar_overlap_planet)
        }
        val store = GoogleCalendarSelectionStore(context)
        val keys = store.scope(team)?.let(store::selected).orEmpty()
        if (keys.isEmpty()) return@withContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            throw GoogleSyncPermissionRequired(arrayOf(Manifest.permission.READ_CALENDAR))
        }
        try {
            val repository = GoogleDeviceCalendarRepository(context.contentResolver)
            val available = repository.calendars().map { it.key }.toSet()
            if (!available.containsAll(keys)) throw java.io.IOException("Connected calendar unavailable")
            val zone = ZoneId.systemDefault()
            for (range in ranges) {
                val from = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
                val through = Instant.ofEpochMilli(range.end - 1).atZone(zone).toLocalDate()
                if (repository.events(keys, from, through).any { range.overlaps(it.timeRange(zone)) } && !allowOverlaps) {
                    throw EventValidationException(R.string.calendar_overlap_google)
                }
            }
        } catch (error: EventValidationException) { throw error }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { throw EventValidationException(R.string.calendar_overlap_check_failed) }
    }
}

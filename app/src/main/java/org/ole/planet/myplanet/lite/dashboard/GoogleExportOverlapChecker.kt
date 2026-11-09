package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import java.time.Instant
import java.time.ZoneId

/** Check only new exports. An existing linked copy is handled by the duplicate checker first. */
internal class GoogleExportOverlapChecker(private val context: Context) {
    fun overlaps(team: String, export: PlanetCalendarExport, marker: String): Boolean {
        val repository = GoogleDeviceCalendarRepository(context.contentResolver)
        val calendars = repository.calendars()
        val store = GoogleCalendarSelectionStore(context)
        val selected = store.scope(team)?.let(store::selected).orEmpty()
        val available = calendars.map { it.key }.toSet()
        if (!available.containsAll(selected)) throw java.io.IOException("Connected calendar unavailable")
        val keys = selected.ifEmpty { available }
        if (keys.isEmpty()) return false
        val zone = ZoneId.systemDefault()
        val range = ExternalCalendarEvent("", export.title, export.beginMillis,
            export.endMillis ?: (export.beginMillis + 60_000), export.allDay, "", "", "").timeRange(zone)
        val from = Instant.ofEpochMilli(range.start).atZone(zone).toLocalDate()
        val through = Instant.ofEpochMilli(range.end - 1).atZone(zone).toLocalDate()
        return repository.events(keys, from, through).any { event ->
            !isOwnGoogleOccurrence(event, marker) && range.overlaps(event.timeRange(zone))
        }
    }
}

internal fun isOwnGoogleOccurrence(event: ExternalCalendarEvent, marker: String): Boolean {
    val plain = org.ole.planet.myplanet.lite.googleCalendarDescription(event.description, "").toString()
    return Regex("Planet: (https?://[^\\s]+)").findAll(plain).any {
        it.value.substringBefore("&revision=") == marker.substringBefore("&revision=")
    }
}

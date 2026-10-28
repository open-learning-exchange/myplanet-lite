package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.PlanetCalendarExport
import org.ole.planet.myplanet.lite.dashboard.exportOccurrence
import org.ole.planet.myplanet.lite.dashboard.linkedPlanetReference
import org.ole.planet.myplanet.lite.dashboard.sameGoogleOccurrence

internal fun preferPlanetCalendarEvents(
    base: String,
    team: String,
    planet: List<TeamCalendarEvent>,
    google: List<TeamCalendarEvent>,
    knownPlanetIds: Set<String>,
    linkedGoogleIds: Set<String> = emptySet(),
    plainDescription: (String) -> String,
): List<TeamCalendarEvent> {
    val descriptions = mutableMapOf<String, String>()
    val exports = planet.mapNotNull { item ->
        val event = item.planetMeetup ?: return@mapNotNull null
        val date = item.occurrenceStart ?: return@mapNotNull null
        runCatching { event.exportOccurrence(date, descriptions.getOrPut(event.id) { plainDescription(event.description) }) }.getOrNull()
    }.distinct().groupBy { it.title to it.beginMillis }
    return planet + google.filter { item ->
        val event = item.googleEvent ?: return@filter true
        if (event.id in linkedGoogleIds) return@filter false
        if (linkedPlanetReference(event.description, base, team)?.first in knownPlanetIds) return@filter false
        val export = PlanetCalendarExport(event.title, event.description, event.location, event.startMillis,
            event.endMillis, event.allDay, "UTC")
        exports[event.title to event.startMillis].orEmpty().none { sameGoogleOccurrence(it, export) }
    }
}

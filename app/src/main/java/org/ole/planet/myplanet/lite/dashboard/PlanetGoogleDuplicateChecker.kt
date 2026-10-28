package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentResolver
import android.provider.CalendarContract.Events
import android.net.Uri
import org.ole.planet.myplanet.lite.googleCalendarDescription

internal fun planetGoogleMarker(base: String, team: String, event: String, occurrence: String): String {
    val source = Uri.parse(base.trimEnd('/').removeSuffix("/db")).buildUpon()
        .appendPath("spa").appendPath("meetups").appendPath("view").appendPath(event)
        .appendQueryParameter("team", team).appendQueryParameter("date", occurrence).build()
    return "Planet: $source"
}

internal class PlanetGoogleDuplicateChecker(private val resolver: ContentResolver) {
    fun exists(export: PlanetCalendarExport, marker: String): Boolean {
        val calendars = GoogleDeviceCalendarRepository(resolver).calendars(includeHidden = true)
        if (calendars.isEmpty()) return false
        val ids = calendars.map { it.id.toString() }
        val placeholders = ids.joinToString(",") { "?" }
        val projection = arrayOf(Events.TITLE, Events.DESCRIPTION, Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.EVENT_LOCATION)
        val selection = "${Events.CALENDAR_ID} IN ($placeholders) AND ${Events.DELETED} = 0 " +
            "AND (${Events.STATUS} IS NULL OR ${Events.STATUS} != ${Events.STATUS_CANCELED}) " +
            "AND (${Events.DESCRIPTION} LIKE ? OR ${Events.DTSTART} = ?)"
        val sourcePath = marker.substringAfter("Planet: ").substringBefore('?')
        val args = (ids + listOf("%$sourcePath%", export.beginMillis.toString())).toTypedArray()
        val cursor = resolver.query(Events.CONTENT_URI, projection, selection, args, null)
            ?: throw java.io.IOException("Calendar unavailable")
        return cursor.use {
            while (it.moveToNext()) {
                val description = googleCalendarDescription(it.getString(1).orEmpty(), "").toString()
                if (description.contains(marker)) return@use true
                val sameEnd = export.endMillis == null || (!it.isNull(3) && it.getLong(3) == export.endMillis)
                if (it.getString(0).orEmpty() == export.title && description.trim().replace(Regex("\\s+"), " ") == export.description.trim().replace(Regex("\\s+"), " ") &&
                    it.getLong(2) == export.beginMillis && sameEnd && (it.getInt(4) != 0) == export.allDay &&
                    it.getString(5).orEmpty() == export.location) return@use true
            }
            false
        }
    }
}

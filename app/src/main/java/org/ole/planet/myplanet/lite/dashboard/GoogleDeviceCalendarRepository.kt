package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal data class GoogleDeviceCalendar(val id: Long, val name: String, val account: String) {
    val key get() = "$id:$account"
}

/** Reads Android's Google calendar cache. Never writes to the provider or to Planet. */
internal class GoogleDeviceCalendarRepository(private val resolver: ContentResolver) {
    fun calendars(): List<GoogleDeviceCalendar> {
        val projection = arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME)
        val result = mutableListOf<GoogleDeviceCalendar>()
        val cursor = resolver.query(Calendars.CONTENT_URI, projection,
            "${Calendars.ACCOUNT_TYPE} = ? AND ${Calendars.SYNC_EVENTS} = 1 AND ${Calendars.VISIBLE} = 1",
            arrayOf("com.google"), "${Calendars.CALENDAR_DISPLAY_NAME} ASC") ?: throw IOException("Calendar provider unavailable")
        cursor.use {
            while (it.moveToNext()) {
                val account = it.getString(2).orEmpty()
                result += GoogleDeviceCalendar(it.getLong(0), it.getString(1).orEmpty().ifBlank { account }, account)
            }
        }
        return result
    }

    fun events(selectedKeys: Set<String>, from: LocalDate, through: LocalDate): List<ExternalCalendarEvent> {
        val selected = calendars().filter { it.key in selectedKeys }
        if (selected.isEmpty()) return emptyList()
        val names = selected.associate { it.id to it.name }
        val zone = ZoneId.systemDefault()
        // Cover both UTC all-day boundaries and local timed-event boundaries.
        val begin = minOf(from.atStartOfDay(zone).toInstant(), from.atStartOfDay(ZoneOffset.UTC).toInstant()).toEpochMilli()
        val end = maxOf(through.plusDays(1).atStartOfDay(zone).toInstant(),
            through.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()).toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().apply {
            ContentUris.appendId(this, begin); ContentUris.appendId(this, end)
        }.build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.CALENDAR_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DESCRIPTION, Instances.EVENT_LOCATION)
        val placeholders = selected.joinToString(",") { "?" }
        val selection = "${Instances.CALENDAR_ID} IN ($placeholders) AND ${CalendarContract.Events.DELETED} = 0 AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
        val cursor = resolver.query(uri, projection, selection, selected.map { it.id.toString() }.toTypedArray(),
            "${Instances.BEGIN} ASC") ?: throw IOException("Calendar instances unavailable")
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val name = names[it.getLong(1)] ?: continue
                    add(ExternalCalendarEvent(it.getLong(0).toString(), it.getString(2).orEmpty(), it.getLong(3),
                        it.getLong(4), it.getInt(5) != 0, it.getString(6).orEmpty(), it.getString(7).orEmpty(), name))
                }
            }
        }
    }
}

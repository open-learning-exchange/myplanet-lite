package org.ole.planet.myplanet.lite

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentUris
import android.content.ContentValues
import android.content.OperationApplicationException
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import org.ole.planet.myplanet.lite.dashboard.PlanetCalendarExport

internal class LinkedGoogleProviderFixture : ContentProvider() {
    val events = linkedMapOf<Long, ContentValues>()
    var account = "ana@gmail.com"
    var calendarAvailable = true
    var failId: Long? = null
    var loseReply = false
    var batches = 0
    var inserted = 0
    var deleted = 0
    private var nextId = 100L
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = null
    fun add(id: Long, export: PlanetCalendarExport, marker: String? = null) {
        events[id] = ContentValues().apply {
            put(Events._ID, id); put(Events.CALENDAR_ID, 1L); put(Events._SYNC_ID, "remote-$id")
            put(Events.TITLE, export.title); put(Events.DESCRIPTION, listOfNotNull(export.description, marker).joinToString("\n\n"))
            put(Events.EVENT_LOCATION, export.location); put(Events.DTSTART, export.beginMillis)
            export.endMillis?.let { put(Events.DTEND, it) }
            put(Events.ALL_DAY, if (export.allDay) 1 else 0); put(Events.DELETED, 0)
        }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, order: String?): MatrixCursor {
        val columns = projection!!
        val cursor = MatrixCursor(columns)
        if (uri.pathSegments.first() == "calendars") {
            if (calendarAvailable) {
                val data = mapOf(Calendars._ID to 1L, Calendars.ACCOUNT_NAME to account, Calendars.CALENDAR_DISPLAY_NAME to "Personal")
                cursor.addRow(columns.map { data[it] }.toTypedArray<Any?>())
            }
        } else {
            events.forEach { (id, values) ->
                if (values.getAsInteger(Events.DELETED) == 1) return@forEach
                if (selection!!.startsWith("${Events._ID} = ?") && id.toString() != args!![0]) return@forEach
                cursor.addRow(columns.map { column ->
                    if (uri.pathSegments.first() == "instances") when (column) {
                        CalendarContract.Instances.EVENT_ID -> id
                        CalendarContract.Instances.BEGIN -> values[Events.DTSTART]
                        CalendarContract.Instances.END -> values[Events.DTEND]
                        else -> values[column]
                    } else values[column]
                }.toTypedArray())
            }
        }
        return cursor
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = ++nextId
        events[id] = ContentValues(values!!).apply { put(Events._ID, id); put(Events.DELETED, 0) }
        inserted++
        return ContentUris.withAppendedId(Events.CONTENT_URI, id)
    }
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int {
        val id = ContentUris.parseId(uri)
        if (id == failId) throw SecurityException("Google write temporarily rejected")
        val value = events[id] ?: return 0
        if (value.getAsInteger(Events.DELETED) == 1) return 0
        value.put(Events.DELETED, 1); deleted++
        return 1
    }
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = error("Replacement must be delete + insert")
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> {
        batches++
        val snapshot = events.mapValues { ContentValues(it.value) }
        val oldInserted = inserted; val oldDeleted = deleted
        val results = try { super.applyBatch(operations) } catch (error: Exception) {
            events.clear(); events.putAll(snapshot); inserted = oldInserted; deleted = oldDeleted
            throw error
        }
        if (loseReply) { loseReply = false; throw OperationApplicationException("Committed response was lost") }
        return results
    }
    fun active() = events.filterValues { it.getAsInteger(Events.DELETED) != 1 }
}

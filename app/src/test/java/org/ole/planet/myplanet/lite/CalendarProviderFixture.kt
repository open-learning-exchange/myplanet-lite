package org.ole.planet.myplanet.lite

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CalendarContract
import java.time.Instant

internal class CalendarProviderFixture : ContentProvider() {
    var queries = 0
    var title: String? = "Google meeting"
    var instanceSelection = ""
    var instanceArguments: List<String> = emptyList()
    var calendarAccount = "ana@gmail.com"
    var visible = true
    var lastInstanceUri: Uri? = null
    override fun onCreate() = true
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Calendar writes forbidden")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Calendar writes forbidden")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Calendar writes forbidden")

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        queries++
        val columns = requireNotNull(projection)
        val calendarQuery = uri.pathSegments.first() == "calendars"
        val cursor = MatrixCursor(columns)
        if (calendarQuery) {
            check(selection!!.contains(CalendarContract.Calendars.ACCOUNT_TYPE))
            check(selection.contains(CalendarContract.Calendars.SYNC_EVENTS))
            check(selection.contains(CalendarContract.Calendars.VISIBLE))
            check(selectionArgs!!.single() == "com.google")
            if (visible) {
                val row: Map<String, Any?> = mapOf(CalendarContract.Calendars._ID to 1L,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME to "Personal",
                    CalendarContract.Calendars.ACCOUNT_NAME to calendarAccount)
                cursor.addRow(columns.map { row[it] }.toTypedArray())
            }
        } else {
            lastInstanceUri = uri
            instanceSelection = selection.orEmpty(); instanceArguments = selectionArgs.orEmpty().toList()
            if (title != null) {
                val row: Map<String, Any?> = mapOf(CalendarContract.Instances.EVENT_ID to 10L,
                    CalendarContract.Instances.CALENDAR_ID to 1L, CalendarContract.Instances.TITLE to title,
                    CalendarContract.Instances.BEGIN to Instant.parse("2026-10-13T12:00:00Z").toEpochMilli(),
                    CalendarContract.Instances.END to Instant.parse("2026-10-13T13:00:00Z").toEpochMilli(),
                    CalendarContract.Instances.ALL_DAY to 0, CalendarContract.Instances.DESCRIPTION to "Personal event",
                    CalendarContract.Instances.EVENT_LOCATION to "Online")
                cursor.addRow(columns.map { row[it] }.toTypedArray())
            }
        }
        return cursor
    }
}

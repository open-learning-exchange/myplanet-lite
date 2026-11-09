package org.ole.planet.myplanet.lite.dashboard

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import io.noties.markwon.Markwon
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.googleCalendarDescription

internal class GoogleSyncPermissionRequired(val permissions: Array<String>) : Exception()
internal class GoogleSyncPendingException : Exception()
internal data class GoogleSyncPlan(val base: String, val user: String, val team: String, val event: String, val links: List<GoogleEventLink>)
internal data class GoogleSyncRow(val id: Long, val calendar: GoogleDeviceCalendar, val remote: String, val export: PlanetCalendarExport)

/** Only previously linked Planet copies are written; all other Google events remain read-only. */
internal class PlanetGoogleSync(private val context: Context) {
    private val resolver = context.contentResolver
    private val markdown by lazy { Markwon.builder(context).build() }
    private fun plain(value: String) = markdown.toMarkdown(value).toString()
    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun prepare(base: String, user: String, team: String, original: TeamMeetup, preview: Boolean = false): GoogleSyncPlan = withContext(Dispatchers.IO) {
        if (!granted(Manifest.permission.READ_CALENDAR)) throw GoogleSyncPermissionRequired(arrayOf(Manifest.permission.READ_CALENDAR))
        val store = GoogleEventLinkStore(context, base, user)
        if (store.jobs().any { it.getString("team") == team && it.getString("event") == original.id }) {
            if (!granted(Manifest.permission.WRITE_CALENDAR)) throw GoogleSyncPermissionRequired(arrayOf(Manifest.permission.WRITE_CALENDAR))
            if (preview) throw GoogleSyncPendingException()
            retry(base, user)
            if (store.jobs().any { it.getString("team") == team && it.getString("event") == original.id }) throw GoogleSyncPendingException()
        }
        val previous = store.links(team, original.id).associateBy { it.googleId }
        val rows = rows(original.title, original.id, base, previous.keys)
        val availableCalendars = GoogleDeviceCalendarRepository(resolver).calendars(includeHidden = true)
            .map { it.id to it.account }.toSet()
        val unavailableLinks = previous.values.filter { (it.calendarId to it.account) !in availableCalendars }
        val found = (rows.mapNotNull { row ->
            val saved = previous[row.id]?.takeIf {
                it.calendarId == row.calendar.id && it.account == row.calendar.account &&
                    (it.remoteId.isBlank() || it.remoteId == row.remote)
            }
            val identified = linkedOccurrence(row.export.description, base, team, original.id)
            val legacyDate = Instant.ofEpochMilli(row.export.beginMillis)
                .atZone(if (row.export.allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()
            val legacy = original.occurrences(legacyDate, legacyDate).mapNotNull { it.occurrenceStart }.distinct().firstOrNull { date ->
                sameGoogleOccurrence(original.exportOccurrence(date, plain(original.description)), row.export)
            }
            val date = saved?.occurrence?.let(LocalDate::parse) ?: identified ?: legacy ?: return@mapNotNull null
            val ordinal = saved?.ordinal ?: original.occurrenceOrdinal(date) ?: return@mapNotNull null
            GoogleEventLink(row.id, row.calendar.id, row.calendar.account, date.toString(), ordinal, row.remote)
        } + unavailableLinks).distinctBy { it.googleId }
        store.bind(team, original.id, found)
        if (!preview && found.isNotEmpty() && !granted(Manifest.permission.WRITE_CALENDAR)) {
            throw GoogleSyncPermissionRequired(arrayOf(Manifest.permission.WRITE_CALENDAR))
        }
        GoogleSyncPlan(base, user, team, original.id, found)
    }

    fun enqueue(plan: GoogleSyncPlan, updated: TeamMeetup?) {
        if (plan.links.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val job = JSONObject().put("id", id).put("team", plan.team).put("event", plan.event)
            .put("remaining", JSONArray(plan.links.map { it.json() }))
            .put("updated", updated?.syncJson() ?: JSONObject.NULL)
        GoogleEventLinkStore(context, plan.base, plan.user).job(id, job)
    }

    suspend fun retry(base: String, user: String): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            val store = GoogleEventLinkStore(context, base, user)
            val jobs = store.jobs()
            if (jobs.isEmpty()) return@withLock 0
            if (!granted(Manifest.permission.READ_CALENDAR) || !granted(Manifest.permission.WRITE_CALENDAR)) return@withLock jobs.size
            for (job in jobs) {
                val team = job.getString("team")
                val event = job.getString("event")
                val updated = job.optJSONObject("updated")?.toTeamMeetup()
                val remaining = job.getJSONArray("remaining")
                val failed = JSONArray()
                for (index in 0 until remaining.length()) {
                    val link = GoogleEventLink.parse(remaining.getJSONObject(index))
                    try {
                        val replacement = replace(base, team, event, link, updated)
                        val bindings = store.links(team, event).filter { it.googleId != link.googleId }.toMutableList()
                        if (replacement != null && bindings.none { it.googleId == replacement.googleId }) bindings += replacement
                        store.bind(team, event, bindings)
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed.put(link.json()) }
                }
                job.put("remaining", failed)
                store.job(job.getString("id"), job.takeIf { failed.length() > 0 })
            }
            store.jobs().size
        }
    }

    private fun replace(base: String, team: String, event: String, link: GoogleEventLink, updated: TeamMeetup?): GoogleEventLink? {
        val calendar = GoogleDeviceCalendarRepository(resolver).calendars(includeHidden = true)
            .firstOrNull { it.id == link.calendarId && it.account == link.account }
            ?: throw java.io.IOException("Linked Google account unavailable")
        val day = updated?.occurrenceAt(link.ordinal)
        val export = if (updated != null && day != null) updated.exportOccurrence(day, plain(updated.description)) else null
        val marker = if (updated != null && day != null) planetGoogleMarker(base, team, event, day.toString()) +
            "&revision=" + android.net.Uri.encode(updated.revision) else null
        val existingRows = rows(null, event, base)
        // Recover safely if the provider committed a replacement before the process stopped.
        if (marker != null) {
            val recovered = existingRows.firstOrNull { it.calendar.id == calendar.id &&
                googleCalendarDescription(it.export.description, "").toString().contains(marker) }
            if (recovered != null) {
                val old = rowById(link.googleId, calendar)
                if (old != null && old.id != recovered.id) {
                    if (link.remoteId.isNotBlank() && old.remote != link.remoteId) throw java.io.IOException("Google identity changed")
                    resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, old.id), null, null)
                }
                return GoogleEventLink(recovered.id, calendar.id, calendar.account, day.toString(), link.ordinal, recovered.remote)
            }
        }
        val current = rowById(link.googleId, calendar) ?: return null
        if (link.remoteId.isNotBlank() && current.remote != link.remoteId) throw java.io.IOException("Google event identity changed")
        val oldUri = ContentUris.withAppendedId(Events.CONTENT_URI, link.googleId)
        if (export == null) {
            resolver.delete(oldUri, null, null)
            return null
        }
        val duration = (current.export.endMillis ?: current.export.beginMillis) - current.export.beginMillis
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendar.id)
            put(Events.TITLE, export.title); put(Events.DESCRIPTION, listOf(export.description, marker!!).filter(String::isNotBlank).joinToString("\n\n"))
            put(Events.EVENT_LOCATION, export.location); put(Events.DTSTART, export.beginMillis)
            put(Events.DTEND, export.endMillis ?: (export.beginMillis + duration.coerceAtLeast(0)))
            put(Events.ALL_DAY, if (export.allDay) 1 else 0); put(Events.EVENT_TIMEZONE, export.zoneId)
        }
        val results = resolver.applyBatch(CalendarContract.AUTHORITY, arrayListOf(
            ContentProviderOperation.newDelete(oldUri).withExpectedCount(1).build(),
            ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values).build(),
        ))
        val id = ContentUris.parseId(requireNotNull(results[1].uri))
        return GoogleEventLink(id, calendar.id, calendar.account, day.toString(), link.ordinal)
    }

    private fun rows(title: String?, event: String, base: String, linkedIds: Set<Long> = emptySet()): List<GoogleSyncRow> {
        val calendars = GoogleDeviceCalendarRepository(resolver).calendars(includeHidden = true)
        if (calendars.isEmpty()) return emptyList()
        val path = planetGoogleMarker(base, "", event, "").substringAfter("Planet: ").substringBefore('?')
        val ids = calendars.map { it.id.toString() }
        val candidates = mutableListOf("${Events.DESCRIPTION} LIKE ?")
        val args = ids.toMutableList().apply { add("%$path%") }
        if (title != null) { candidates += "${Events.TITLE} = ?"; args += title }
        if (linkedIds.isNotEmpty()) {
            candidates += "${Events._ID} IN (${linkedIds.joinToString(",") { "?" }})"
            args += linkedIds.map(Long::toString)
        }
        val selection = "${Events.CALENDAR_ID} IN (${ids.joinToString(",") { "?" }}) AND ${Events.DELETED} = 0 " +
            "AND (${Events.STATUS} IS NULL OR ${Events.STATUS} != ${Events.STATUS_CANCELED}) AND (${candidates.joinToString(" OR ")})"
        return query(selection, args.toTypedArray(), calendars)
    }
    private fun rowById(id: Long, calendar: GoogleDeviceCalendar): GoogleSyncRow? = query(
        "${Events._ID} = ? AND ${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0",
        arrayOf(id.toString(), calendar.id.toString()), listOf(calendar)).singleOrNull()
    private fun query(selection: String, args: Array<String>, calendars: List<GoogleDeviceCalendar>): List<GoogleSyncRow> {
        val projection = arrayOf(Events._ID, Events.CALENDAR_ID, Events._SYNC_ID, Events.TITLE, Events.DESCRIPTION,
            Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND, Events.ALL_DAY)
        val cursor = resolver.query(Events.CONTENT_URI, projection, selection, args, null)
            ?: throw java.io.IOException("Google calendar unavailable")
        val byId = calendars.associateBy { it.id }
        return cursor.use { buildList {
            while (it.moveToNext()) {
                val calendar = byId[it.getLong(1)] ?: continue
                add(GoogleSyncRow(it.getLong(0), calendar, it.getString(2).orEmpty(), PlanetCalendarExport(
                    it.getString(3).orEmpty(), it.getString(4).orEmpty(), it.getString(5).orEmpty(), it.getLong(6),
                    if (it.isNull(7)) null else it.getLong(7), it.getInt(8) != 0, ZoneId.systemDefault().id)))
            }
        } }
    }
    private companion object { val mutex = Mutex() }
}

internal fun sameGoogleOccurrence(planet: PlanetCalendarExport, google: PlanetCalendarExport): Boolean {
    fun normalized(value: String) = googleCalendarDescription(value, "").toString().trim().replace(Regex("\\s+"), " ")
    return planet.title == google.title && planet.beginMillis == google.beginMillis && planet.allDay == google.allDay &&
        (planet.endMillis == null || planet.endMillis == google.endMillis) && planet.location == google.location &&
        normalized(planet.description) == normalized(google.description)
}

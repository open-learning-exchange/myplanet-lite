package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal data class EditableTeamEvent(val raw: String, val meetup: TeamMeetup)
internal data class EventActionPermissions(val canEdit: Boolean, val canDelete: Boolean)
internal class CalendarEditConflictException(val latest: EditableTeamEvent) : IOException()

internal class TeamEventEditorRepository(client: OkHttpClient = AuthDependencies.client) {
    private val transport = TeamCalendarRepository(client)

    suspend fun load(base: String, credentials: StoredCredentials, cookie: String?, teamId: String, eventId: String): Result<EditableTeamEvent> =
        withContext(Dispatchers.IO) { catching { checkedDocument(base, credentials, cookie, teamId, eventId) } }

    suspend fun save(base: String, credentials: StoredCredentials, cookie: String?, teamId: String,
        eventId: String, revision: String, draft: TeamEventDraft): Result<TeamMeetup> = withContext(Dispatchers.IO) {
        catching {
            val changes = draft.toJson()
            val latest = checkedDocument(base, credentials, cookie, teamId, eventId)
            if (latest.meetup.revision != revision) throw CalendarEditConflictException(latest)
            val document = JSONObject(latest.raw)
            changes.keys().forEach { key -> document.put(key, changes.get(key)) }
            val response = try {
                transport.request(root(base) + "meetups", document, credentials, cookie)
            } catch (error: CalendarHttpException) {
                if (error.status == 409) {
                    throw CalendarEditConflictException(checkedDocument(base, credentials, cookie, teamId, eventId))
                }
                throw error
            }
            if (!response.optBoolean("ok") || response.getString("id") != eventId) throw IOException("Invalid event update response")
            val nextRevision = response.getString("rev").also { require(it.isNotBlank()) }
            document.put("_rev", nextRevision)
            document.toTeamMeetup() ?: throw IOException("Invalid updated event")
        }
    }

    suspend fun loadForDeletion(base: String, credentials: StoredCredentials, cookie: String?, teamId: String, eventId: String): Result<EditableTeamEvent> =
        withContext(Dispatchers.IO) { catching { checkedDocument(base, credentials, cookie, teamId, eventId, forDeletion = true) } }

    suspend fun permissions(base: String, credentials: StoredCredentials, cookie: String?, teamId: String,
        eventId: String): Result<EventActionPermissions> = withContext(Dispatchers.IO) {
        catching {
            try {
                val snapshot = checkedDocument(base, credentials, cookie, teamId, eventId, forDeletion = true)
                EventActionPermissions(snapshot.meetup.createdBy == credentials.username, true)
            } catch (_: CalendarAccessDeniedException) { EventActionPermissions(false, false) }
        }
    }

    suspend fun delete(base: String, credentials: StoredCredentials, cookie: String?, teamId: String,
        eventId: String, revision: String): Result<String> = withContext(Dispatchers.IO) {
        catching {
            val latest = checkedDocument(base, credentials, cookie, teamId, eventId, forDeletion = true)
            if (latest.meetup.revision != revision) throw CalendarEditConflictException(latest)
            val tombstone = JSONObject().put("_id", eventId).put("_rev", revision).put("_deleted", true)
            val response = try {
                transport.requestArray(root(base) + "meetups/_bulk_docs",
                    JSONObject().put("docs", JSONArray().put(tombstone)), credentials, cookie)
            } catch (error: CalendarHttpException) {
                if (error.status == 409) throw CalendarEditConflictException(checkedDocument(base, credentials, cookie, teamId, eventId, forDeletion = true))
                throw error
            }
            require(response.length() == 1)
            val row = response.getJSONObject(0)
            require(row.optString("id") == eventId)
            when {
                row.optString("error") == "conflict" ->
                    throw CalendarEditConflictException(checkedDocument(base, credentials, cookie, teamId, eventId, forDeletion = true))
                row.optString("error") in setOf("forbidden", "unauthorized") -> throw CalendarAccessDeniedException()
                !row.optBoolean("ok") -> throw IOException("Event deletion rejected")
            }
            row.getString("rev").also { require(it.isNotBlank()) }
        }
    }

    private fun checkedDocument(base: String, credentials: StoredCredentials, cookie: String?, teamId: String, eventId: String, forDeletion: Boolean = false): EditableTeamEvent {
        val root = root(base)
        fun get(database: String, id: String): JSONObject {
            val url = root.toHttpUrl().newBuilder().addPathSegment(database).addPathSegment(id).build()
            return transport.request(url.toString(), null, credentials, cookie)
        }
        val document = get("meetups", eventId)
        require(document.getString("_id") == eventId && !document.optBoolean("_deleted"))
        if (document.optJSONObject("link")?.optString("teams") != teamId) throw CalendarAccessDeniedException()
        val meetup = document.toTeamMeetup() ?: throw IOException("Invalid event document")
        require(meetup.revision.isNotBlank())
        val user = get("_users", "org.couchdb.user:${credentials.username}")
        val name = user.getString("name")
        require(name == credentials.username)
        if (document.optString("createdBy") != name) {
            if (!forDeletion) throw CalendarAccessDeniedException()
            val roles = user.optJSONArray("roles")
            val admin = user.optBoolean("isUserAdmin") || (0 until (roles?.length() ?: 0)).any { roles!!.optString(it) == "_admin" }
            if (!admin) {
                val team = get("teams", teamId)
                require(team.getString("_id") == teamId)
                if (team.optString("createdBy") !in setOf(name, "org.couchdb.user:$name")) throw CalendarAccessDeniedException()
            }
        }
        return EditableTeamEvent(document.toString(), meetup)
    }

    private fun root(base: String) = base.trim().trimEnd('/').removeSuffix("/db") + "/db/"
    private inline fun <T> catching(block: () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
}

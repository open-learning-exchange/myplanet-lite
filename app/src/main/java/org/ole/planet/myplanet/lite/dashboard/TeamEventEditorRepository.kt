package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal data class EditableTeamEvent(val raw: String, val meetup: TeamMeetup)
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

    private fun checkedDocument(base: String, credentials: StoredCredentials, cookie: String?, teamId: String, eventId: String): EditableTeamEvent {
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
        if (document.optString("createdBy") != name) throw CalendarAccessDeniedException()
        return EditableTeamEvent(document.toString(), meetup)
    }

    private fun root(base: String) = base.trim().trimEnd('/').removeSuffix("/db") + "/db/"
    private inline fun <T> catching(block: () -> T): Result<T> = try { Result.success(block()) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
}

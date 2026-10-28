package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

/** Persists only notification work. Event updates are never retried by this outbox. */
internal class CalendarNotificationOutbox(
    context: Context,
    client: OkHttpClient = AuthDependencies.client,
) {
    private val preferences = SecurePreferencesProvider.getEncryptedPreferences(context.applicationContext, "calendar_notification_outbox")
    private val transport = TeamCalendarRepository(client)
    private fun key(base: String, user: String) = "calendar_notifications_" + digest(base.trimEnd('/').removeSuffix("/db") + "\u0000" + user)
    private fun read(key: String) = JSONArray(preferences.getString(key, "[]"))

    fun enqueue(base: String, user: String, eventId: String, title: String) {
        synchronized(storeLock) {
            val key = key(base, user)
            val jobs = read(key)
            jobs.put(JSONObject().put("id", UUID.randomUUID().toString()).put("event", eventId)
                .put("title", title).put("time", System.currentTimeMillis()))
            check(preferences.edit().putString(key, jobs.toString()).commit())
        }
    }

    fun pending(base: String, user: String): Int = synchronized(storeLock) { read(key(base, user)).length() }

    fun sendInBackground(base: String, credentials: StoredCredentials, cookie: String?) {
        worker.launch {
            try { retry(base, credentials, cookie) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Keep pending work without affecting the saved event. */ }
        }
    }

    suspend fun retry(base: String, credentials: StoredCredentials, cookie: String?): Int = withContext(Dispatchers.IO) {
        sendLock.withLock {
            val key = key(base, credentials.username)
            val root = base.trim().trimEnd('/').removeSuffix("/db") + "/db/"
            val jobs = synchronized(storeLock) { read(key) }
            for (index in 0 until jobs.length()) {
                val job = jobs.getJSONObject(index)
                try {
                    if (!job.has("docs")) {
                        job.put("docs", prepare(root, credentials, cookie, job))
                        update(key, job)
                    }
                    val docs = job.getJSONArray("docs")
                    if (docs.length() == 0) { update(key, job, remove = true); continue }
                    val results = transport.requestArray(root + "notifications/_bulk_docs", JSONObject().put("docs", docs), credentials, cookie)
                    val accepted = mutableSetOf<String>()
                    val documents = (0 until docs.length()).associateBy({ docs.getJSONObject(it).getString("_id") }, { docs.getJSONObject(it) })
                    for (rowIndex in 0 until results.length()) {
                        val row = results.optJSONObject(rowIndex) ?: continue
                        val id = row.optString("id")
                        val original = documents[id] ?: continue
                        if (row.optBoolean("ok") && row.optString("rev").isNotBlank()) accepted += id
                        else if (row.optString("error") == "conflict") {
                            // A prior request may have succeeded before its response was lost.
                            val url = root.toHttpUrl().newBuilder().addPathSegment("notifications").addPathSegment(id).build()
                            val existing = transport.request(url.toString(), null, credentials, cookie)
                            if (listOf("_id", "user", "item", "message", "link", "time").all {
                                existing.opt(it)?.toString() == original.opt(it)?.toString()
                            }) accepted += id
                        }
                    }
                    val remaining = JSONArray((0 until docs.length()).map { docs.getJSONObject(it) }
                        .filter { it.getString("_id") !in accepted })
                    job.put("docs", remaining)
                    update(key, job, remove = remaining.length() == 0)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep this notification job for the next explicit retry or calendar load. */ }
            }
            pending(base, credentials.username)
        }
    }

    private fun prepare(root: String, credentials: StoredCredentials, cookie: String?, job: JSONObject): JSONArray {
        val users = linkedSetOf<String>()
        var bookmark: String? = null
        do {
            val query = JSONObject().put("selector", JSONObject().put("meetupIds", JSONObject().put("\$in", JSONArray().put(job.getString("event")))))
                .put("fields", JSONArray().put("_id")).put("limit", 1000)
            bookmark?.let { query.put("bookmark", it) }
            val response = transport.request(root + "shelf/_find", query, credentials, cookie)
            val shelves = response.getJSONArray("docs")
            for (index in 0 until shelves.length()) {
                val id = shelves.getJSONObject(index).optString("_id")
                if (id.isNotBlank()) users += if (id.startsWith("org.couchdb.user:")) id else "org.couchdb.user:$id"
            }
            val next = response.optString("bookmark").takeIf(String::isNotBlank)
            if (shelves.length() < 1000 || next == null || next == bookmark) break
            bookmark = next
        } while (true)
        val title = job.getString("title").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        return JSONArray(users.map { user ->
            JSONObject().put("_id", "meetup-update-${job.getString("id")}-${digest(user)}")
                .put("user", user).put("message", "<b>\"$title\"</b> has been updated.")
                .put("link", "/meetups/view/${job.getString("event")}").put("item", job.getString("event"))
                .put("type", "meetup").put("priority", 1).put("status", "unread").put("time", job.getLong("time"))
        })
    }

    private fun update(key: String, job: JSONObject, remove: Boolean = false) {
        synchronized(storeLock) {
            val jobs = read(key)
            val next = JSONArray()
            for (index in 0 until jobs.length()) {
                val item = jobs.getJSONObject(index)
                if (item.getString("id") != job.getString("id")) next.put(item)
                else if (!remove) next.put(job)
            }
            check(preferences.edit().putString(key, next.toString()).commit())
        }
    }

    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private companion object {
        val storeLock = Any()
        val sendLock = Mutex()
        val worker = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

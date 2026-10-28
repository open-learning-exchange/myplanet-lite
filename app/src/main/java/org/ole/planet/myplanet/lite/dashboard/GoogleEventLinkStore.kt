package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal data class GoogleEventLink(val googleId: Long, val calendarId: Long, val account: String,
    val occurrence: String, val ordinal: Int, val remoteId: String = "") {
    fun json() = JSONObject().put("id", googleId).put("calendar", calendarId).put("account", account)
        .put("occurrence", occurrence).put("ordinal", ordinal).put("remote", remoteId)
    companion object {
        fun parse(json: JSONObject) = GoogleEventLink(json.getLong("id"), json.getLong("calendar"),
            json.getString("account"), json.getString("occurrence"), json.getInt("ordinal"), json.optString("remote"))
    }
}

internal class GoogleEventLinkStore(context: Context, base: String, user: String) {
    private val prefs = SecurePreferencesProvider.getEncryptedPreferences(context.applicationContext, "planet_google_event_links")
    private val key = "planet_google_sync_" + MessageDigest.getInstance("SHA-256")
        .digest((base.trimEnd('/').removeSuffix("/db") + "\u0000" + user).toByteArray()).joinToString("") { "%02x".format(it) }
    private fun state() = JSONObject(prefs.getString(key, "{\"links\":{},\"jobs\":{}}")!!)
    fun links(team: String, event: String): List<GoogleEventLink> = synchronized(lock) {
        val array = state().getJSONObject("links").optJSONArray("$team/$event") ?: JSONArray()
        (0 until array.length()).map { GoogleEventLink.parse(array.getJSONObject(it)) }
    }
    fun bind(team: String, event: String, links: List<GoogleEventLink>) = edit { root ->
        root.getJSONObject("links").put("$team/$event", JSONArray(links.map { it.json() }))
    }
    fun jobs(): List<JSONObject> = synchronized(lock) {
        val jobs = state().getJSONObject("jobs")
        jobs.keys().asSequence().map { jobs.getJSONObject(it) }.toList()
    }
    fun job(id: String, json: JSONObject?) = edit { root ->
        if (json == null) root.getJSONObject("jobs").remove(id) else root.getJSONObject("jobs").put(id, json)
    }
    fun linkedIds(team: String, events: Set<String>): Set<String> = synchronized(lock) {
        val all = state().getJSONObject("links")
        events.flatMap { event ->
            val array = all.optJSONArray("$team/$event") ?: JSONArray()
            (0 until array.length()).map { array.getJSONObject(it).getLong("id").toString() }
        }.toSet()
    }
    fun hiddenIds(): Set<String> = jobs().flatMap { job ->
        val links = job.getJSONArray("remaining")
        (0 until links.length()).map { links.getJSONObject(it).getLong("id").toString() }
    }.toSet()
    private fun edit(block: (JSONObject) -> Unit) = synchronized(lock) {
        val root = state(); block(root)
        check(prefs.edit().putString(key, root.toString()).commit())
    }
    private companion object { val lock = Any() }
}

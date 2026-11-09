package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import java.security.MessageDigest
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal class GoogleCalendarSelectionStore(private val context: Context) {
    fun scope(teamId: String?): String? {
        val user = ProfileCredentialsStore.getStoredCredentials(context)?.username ?: return null
        val base = DashboardServerPreferences.getServerBaseUrl(context) ?: return null
        if (teamId.isNullOrBlank()) return null
        val key = listOf(base.trimEnd('/'), user, teamId).joinToString("\u0000")
        return "google_calendars_" + MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    fun selected(scope: String): Set<String> = SecurePreferencesProvider.getServerPreferences(context)
        .getStringSet(scope, emptySet()).orEmpty().toSet()

    fun save(scope: String, keys: Set<String>) {
        SecurePreferencesProvider.getServerPreferences(context).edit().putStringSet(scope, keys).apply()
    }
}

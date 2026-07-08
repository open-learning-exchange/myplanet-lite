/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-24
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class DashboardSurveyStatusStore(
    context: Context,
    private val username: String? = null,
) {
    private val prefs = SecurePreferencesProvider.getServerPreferences(context)

    fun getStatus(id: String?): SurveyStatus? {
        val key = namespacedKey(id) ?: return null
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { SurveyStatus.valueOf(stored) }.getOrNull()
    }

    fun ensureNewDefaults(ids: Collection<String?>) {
        if (ids.isEmpty()) return
        val editor = prefs.edit()
        var changed = false
        ids.forEach { id ->
            val key = namespacedKey(id)
            if (key != null && !prefs.contains(key)) {
                editor.putString(key, SurveyStatus.NEW.name)
                changed = true
            }
        }
        if (changed) {
            editor.apply()
        }
    }

    fun markViewed(id: String?) {
        namespacedKey(id)?.let {
            prefs.edit().putString(it, SurveyStatus.VIEWED.name).apply()
        }
    }

    fun markCompleted(id: String?) {
        namespacedKey(id)?.let {
            prefs.edit().putString(it, SurveyStatus.COMPLETED.name).apply()
        }
    }

    private fun namespacedKey(id: String?): String? {
        val base = id?.takeIf { it.isNotBlank() } ?: return null
        val user = username?.takeIf { it.isNotBlank() } ?: return base
        return "$user:$base"
    }

    companion object {
        private const val PREFS_NAME = "dashboard_survey_statuses"
    }
}

enum class SurveyStatus {
    NEW,
    VIEWED,
    COMPLETED,
}

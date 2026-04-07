/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-23
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

object DashboardTeamSelectionPreferences {
    private const val PREFS_NAME = "team_selection_preferences"
    private const val KEY_SELECTED_TEAM_ID = "selected_team_id"
    private const val KEY_SELECTED_TEAM_NAME = "selected_team_name"

    fun getSelectedTeamId(context: Context): String? {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        return prefs.getString(KEY_SELECTED_TEAM_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun getSelectedTeamName(context: Context): String? {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        return prefs.getString(KEY_SELECTED_TEAM_NAME, null)?.takeIf { it.isNotBlank() }
    }

    fun setSelectedTeam(context: Context, teamId: String?, teamName: String?) {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        prefs.edit().apply {
            if (teamId.isNullOrBlank()) {
                remove(KEY_SELECTED_TEAM_ID)
                remove(KEY_SELECTED_TEAM_NAME)
            } else {
                putString(KEY_SELECTED_TEAM_ID, teamId)
                if (teamName.isNullOrBlank()) {
                    remove(KEY_SELECTED_TEAM_NAME)
                } else {
                    putString(KEY_SELECTED_TEAM_NAME, teamName)
                }
            }
        }.apply()
    }
}

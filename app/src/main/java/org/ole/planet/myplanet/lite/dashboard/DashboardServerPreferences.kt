/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

object DashboardServerPreferences {
    internal const val KEY_SERVER_URL = "server_url"
    internal const val KEY_SERVER_CODE = "server_code"
    internal const val KEY_SERVER_PARENT_CODE = "server_parent_code"

    fun getServerBaseUrl(context: Context): String? {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        return prefs.getString(KEY_SERVER_URL, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getServerCode(context: Context): String? {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        return prefs.getString(KEY_SERVER_CODE, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun getServerParentCode(context: Context): String? {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        return prefs.getString(KEY_SERVER_PARENT_CODE, null)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

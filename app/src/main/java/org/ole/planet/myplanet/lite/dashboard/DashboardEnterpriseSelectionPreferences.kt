package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.core.content.edit
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

object DashboardEnterpriseSelectionPreferences {
    private const val KEY_ID = "selected_enterprise_id"
    private const val KEY_NAME = "selected_enterprise_name"

    fun getSelectedEnterpriseId(context: Context): String? =
        SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            .getString(KEY_ID, null)
            ?.takeIf(String::isNotBlank)

    fun setSelectedEnterprise(context: Context, id: String?, name: String?) {
        SecurePreferencesProvider.getServerPreferences(context.applicationContext).edit {
            if (id.isNullOrBlank()) {
                remove(KEY_ID)
                remove(KEY_NAME)
            } else {
                putString(KEY_ID, id)
                if (name.isNullOrBlank()) remove(KEY_NAME) else putString(KEY_NAME, name)
            }
        }
    }
}

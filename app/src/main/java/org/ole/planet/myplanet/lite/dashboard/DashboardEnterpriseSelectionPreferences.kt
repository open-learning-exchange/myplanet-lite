package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.core.content.edit
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

object DashboardEnterpriseSelectionPreferences {
    private const val KEY_ID = "selected_enterprise_id"
    private const val KEY_NAME = "selected_enterprise_name"
    private const val KEY_TYPE = "selected_enterprise_type"
    private const val KEY_PLANET_CODE = "selected_enterprise_planet_code"

    fun getSelectedEnterpriseId(context: Context): String? =
        SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            .getString(KEY_ID, null)
            ?.takeIf(String::isNotBlank)

    fun getSelectedEnterpriseName(context: Context): String? = value(context, KEY_NAME)
    fun getSelectedEnterpriseType(context: Context): String? = value(context, KEY_TYPE)
    fun getSelectedEnterprisePlanetCode(context: Context): String? = value(context, KEY_PLANET_CODE)

    fun setSelectedEnterprise(
        context: Context,
        id: String?,
        name: String?,
        type: String? = null,
        planetCode: String? = null,
    ) {
        SecurePreferencesProvider.getServerPreferences(context.applicationContext).edit {
            if (id.isNullOrBlank()) {
                remove(KEY_ID)
                remove(KEY_NAME)
                remove(KEY_TYPE)
                remove(KEY_PLANET_CODE)
            } else {
                putString(KEY_ID, id)
                if (name.isNullOrBlank()) remove(KEY_NAME) else putString(KEY_NAME, name)
                if (type.isNullOrBlank()) remove(KEY_TYPE) else putString(KEY_TYPE, type)
                if (planetCode.isNullOrBlank()) remove(KEY_PLANET_CODE) else putString(KEY_PLANET_CODE, planetCode)
            }
        }
    }

    private fun value(context: Context, key: String): String? =
        SecurePreferencesProvider.getServerPreferences(context.applicationContext)
            .getString(key, null)?.takeIf(String::isNotBlank)
}

package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

object DashboardServerCatalog {
    private const val KEY_CUSTOM_SERVERS = "custom_servers"
    private const val DEFAULT_COUNTRY_CODE = "GT"

    fun addObservedServer(
        context: Context,
        baseUrl: String,
        displayName: String? = null,
        countryCode: String = DEFAULT_COUNTRY_CODE,
    ): Boolean {
        val sanitizedUrl = normalizeServerUrl(baseUrl)
        if (sanitizedUrl.isEmpty()) return false
        val key = baseUrlKey(sanitizedUrl)
        if (key.isEmpty()) return false

        val servers = loadCustomServers(context).toMutableList()
        if (servers.any { baseUrlKey(it.baseUrl) == key }) {
            return false
        }

        servers.add(
            CustomServer(
                displayName = displayName?.trim()?.takeIf { it.isNotBlank() }
                    ?: displayNameFromBaseUrl(sanitizedUrl),
                baseUrl = sanitizedUrl,
                countryCode = countryCode.uppercase(Locale.ROOT),
            ),
        )
        persistCustomServers(context, servers)
        return true
    }

    fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val normalized = withScheme.toHttpUrlOrNull() ?: return ""
        return normalized.newBuilder().build().toString().trimEnd('/')
    }

    fun baseUrlKey(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.trimEnd('/').lowercase(Locale.ROOT)
    }

    fun displayNameFromBaseUrl(baseUrl: String): String {
        return baseUrl.toHttpUrlOrNull()?.host?.takeIf { it.isNotBlank() }
            ?: baseUrl.trim().trimEnd('/')
    }

    private fun loadCustomServers(context: Context): List<CustomServer> {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        val raw = prefs.getString(KEY_CUSTOM_SERVERS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val baseUrl = item.optString("baseUrl").trim()
                    val countryCode = item.optString("countryCode").trim()
                    if (baseUrl.isBlank() || countryCode.isBlank()) continue
                    add(
                        CustomServer(
                            displayName = item.optString("displayName").trim()
                                .ifBlank { baseUrl },
                            baseUrl = baseUrl,
                            countryCode = countryCode.uppercase(Locale.ROOT),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistCustomServers(context: Context, servers: List<CustomServer>) {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        val array = JSONArray()
        servers.forEach { server ->
            array.put(
                JSONObject()
                    .put("displayName", server.displayName)
                    .put("baseUrl", server.baseUrl)
                    .put("countryCode", server.countryCode),
            )
        }
        prefs.edit()
            .putString(KEY_CUSTOM_SERVERS, array.toString())
            .apply()
    }

    private data class CustomServer(
        val displayName: String,
        val baseUrl: String,
        val countryCode: String,
    )
}

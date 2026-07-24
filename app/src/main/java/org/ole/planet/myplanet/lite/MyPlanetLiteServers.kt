@file:Suppress("DEPRECATION")
/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-24
 */

package org.ole.planet.myplanet.lite
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import com.blongho.country_data.World
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

internal data class ServerDialogViews(
    val serverUrlLayout: TextInputLayout,
    val serverUrlInput: MaterialAutoCompleteTextView,
    val serverNameLayout: TextInputLayout,
    val serverNameInput: TextInputEditText,
    val countryLayout: TextInputLayout,
    val countryInput: MaterialAutoCompleteTextView,
)


internal fun MyPlanetLite.showServerConfigurationDialogImpl(
    serverInput: MaterialAutoCompleteTextView,
    serverLayout: TextInputLayout,
) {
    serverLayout.error = null
    val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_configuration, null)
    val views = setupServerConfigurationViews(dialogView)

    val countryList = getFilteredCountries()
        val currentConfig = loadServerConfigurationImpl()
    val serverSuggestionsAdapter = setupCountryAndServerAdapters(views, countryList, currentConfig)

    val dialog =
        AlertDialog
            .Builder(this)
            .setTitle(R.string.server_configuration_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.server_configuration_save, null)
            .create()

    dialog.setOnShowListener {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            handleServerConfigurationSave(
                views,
                countryList,
                serverInput,
                serverLayout,
                serverSuggestionsAdapter,
                dialog,
            )
        }
    }

    dialog.show()
}

internal fun MyPlanetLite.setupServerConfigurationViews(dialogView: View): ServerDialogViews =
    ServerDialogViews(
        serverUrlLayout = dialogView.findViewById(R.id.serverUrlInputLayout),
        serverUrlInput = dialogView.findViewById(R.id.serverUrlInput),
        serverNameLayout = dialogView.findViewById(R.id.serverNameInputLayout),
        serverNameInput = dialogView.findViewById(R.id.serverNameInput),
        countryLayout = dialogView.findViewById(R.id.countryInputLayout),
        countryInput = dialogView.findViewById(R.id.countryInput),
    )

internal fun MyPlanetLite.getFilteredCountries(): List<com.blongho.country_data.Country> {
    val excludedCountryCodes = setOf("CN", "HK", "TW", "IL", "PS")
    return World
        .getAllCountries()
        .filterNot { excludedCountryCodes.contains(it.alpha2.uppercase(Locale.ROOT)) }
        .sortedBy { it.name }
}

internal fun MyPlanetLite.setupCountryAndServerAdapters(
    views: ServerDialogViews,
    countryList: List<com.blongho.country_data.Country>,
    currentConfig: ServerConfiguration,
): ArrayAdapter<String> {
    val serverSuggestionsAdapter =
        ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            buildServerSuggestions(currentConfig),
        )
    views.serverUrlInput.setAdapter(serverSuggestionsAdapter)

    val countryNames = countryList.map { it.name }
    views.countryInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, countryNames))

    views.serverUrlInput.setOnClickListener { showDropDownWhenSafe(views.serverUrlInput) }
    views.serverUrlInput.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            showDropDownWhenSafe(views.serverUrlInput)
        }
    }
    views.countryInput.setOnClickListener { showDropDownWhenSafe(views.countryInput) }
    views.countryInput.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            showDropDownWhenSafe(views.countryInput)
        }
    }

    views.serverUrlInput.setText(DEFAULT_SERVER_URL_PREFIX, false)
    views.serverUrlInput.setSelection(views.serverUrlInput.text?.length ?: 0)
    views.serverNameInput.text = null
    countryList.firstOrNull()?.let { firstCountry ->
        views.countryInput.setText(firstCountry.name, false)
    }

    return serverSuggestionsAdapter
}

internal fun MyPlanetLite.handleServerConfigurationSave(
    views: ServerDialogViews,
    countryList: List<com.blongho.country_data.Country>,
    serverInput: MaterialAutoCompleteTextView,
    serverLayout: TextInputLayout,
    serverSuggestionsAdapter: ArrayAdapter<String>,
    dialog: AlertDialog,
) {
    views.serverUrlLayout.error = null
    views.serverNameLayout.error = null
    views.countryLayout.error = null

    val url =
        views.serverUrlInput.text
            ?.toString()
            ?.trim()
            .orEmpty()
    val normalizedUrl = normalizeServerUrl(url)
    val serverName =
        views.serverNameInput.text
            ?.toString()
            ?.trim()
            .orEmpty()
    val countryName =
        views.countryInput.text
            ?.toString()
            ?.trim()
            .orEmpty()
    val selectedCountry = countryList.firstOrNull { it.name.equals(countryName, ignoreCase = true) }

    if (normalizedUrl.isEmpty()) {
        views.serverUrlLayout.error = getString(R.string.server_configuration_url_error)
        return
    }
    if (serverName.isEmpty()) {
        views.serverNameLayout.error = getString(R.string.server_configuration_name_error)
        return
    }
    val nonNullCountry =
        selectedCountry ?: run {
            views.countryLayout.error = getString(R.string.server_configuration_country_error)
            return
        }

        val added = addCustomServerImpl(serverName, normalizedUrl, nonNullCountry.alpha2)
    if (added) {
        saveServerConfiguration(normalizedUrl, nonNullCountry.alpha2, serverName)
        Toast.makeText(this, R.string.server_configuration_added, Toast.LENGTH_SHORT).show()
        refreshServerOptions(serverInput, serverLayout)
            val updatedConfig = loadServerConfigurationImpl()
        serverSuggestionsAdapter.clear()
        serverSuggestionsAdapter.addAll(buildServerSuggestions(updatedConfig))
        serverSuggestionsAdapter.notifyDataSetChanged()
        dialog.dismiss()
    } else {
        views.serverUrlLayout.error = getString(R.string.server_configuration_duplicate_error)
    }
}

internal fun MyPlanetLite.loadServerConfigurationImpl(): ServerConfiguration {
    val builtInServers = builtInServerOptions()
    val customServers = loadCustomServers().map { it.toServerOption() }
    val storedUrl = serverPreferences.getString(KEY_SERVER_URL, builtInServers.firstOrNull()?.baseUrl).orEmpty().trim()
    val storedCountry = serverPreferences.getString(KEY_COUNTRY_CODE, DEFAULT_COUNTRY_CODE).orEmpty().uppercase(Locale.ROOT)
    val storedDisplayName = serverPreferences.getString(KEY_SERVER_DISPLAY_NAME, null)
    val baseUrl = if (storedUrl.isNotEmpty()) storedUrl else builtInServers.firstOrNull()?.baseUrl.orEmpty()
    val matchedServer =
        (builtInServers + customServers).firstOrNull {
            baseUrlKey(it.baseUrl) == baseUrlKey(baseUrl)
        }
    val countryCode =
        when {
            matchedServer != null -> matchedServer.countryCode
            storedCountry.isNotEmpty() -> storedCountry
            else -> DEFAULT_COUNTRY_CODE
        }
    val displayName =
        when {
            matchedServer != null -> matchedServer.displayName
            !storedDisplayName.isNullOrBlank() -> storedDisplayName
            baseUrl.isNotEmpty() -> baseUrl
            else -> builtInServers.firstOrNull()?.displayName ?: ""
        }
    return ServerConfiguration(
        baseUrl = baseUrl,
        countryCode = countryCode,
        displayName = displayName,
    )
}

internal fun MyPlanetLite.saveServerConfiguration(
    url: String,
    countryCode: String,
    displayName: String,
) {
    val sanitizedUrl = normalizeServerUrl(url)
    val resolvedDisplayName = displayName.ifBlank { sanitizedUrl }
    serverPreferences
        .edit()
        .putString(KEY_SERVER_URL, sanitizedUrl)
        .putString(KEY_COUNTRY_CODE, countryCode.uppercase(Locale.ROOT))
        .putString(KEY_SERVER_DISPLAY_NAME, resolvedDisplayName)
        .apply()
}

internal fun MyPlanetLite.updateServerFlag(
    serverLayout: TextInputLayout,
    countryCode: String,
) {
    val flagRes = World.getFlagOf(countryCode)
    if (flagRes != 0) {
        val drawable = AppCompatResources.getDrawable(this, flagRes)
        serverLayout.startIconDrawable = drawable
        serverLayout.isStartIconVisible = true
        serverLayout.doOnLayout { layout ->
            val startIconView = layout.findViewById<ImageView>(com.google.android.material.R.id.text_input_start_icon)
            val minWidth = resources.getDimensionPixelSize(R.dimen.server_flag_min_width)
            val maxWidth = resources.getDimensionPixelSize(R.dimen.server_flag_max_width)
            val widthRatio = resources.getFraction(R.fraction.server_flag_width_ratio, 1, 1)
            val desiredWidth = (layout.width * widthRatio).toInt().coerceIn(minWidth, maxWidth)
            val marginStart = resources.getDimensionPixelSize(R.dimen.server_flag_margin_start)
            startIconView?.apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    width = desiredWidth
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                    this.marginStart = marginStart
                }
                requestLayout()
            }
        }
    } else {
        serverLayout.startIconDrawable = null
        serverLayout.isStartIconVisible = false
    }
}

internal fun MyPlanetLite.refreshServerOptions(
    serverInput: MaterialAutoCompleteTextView,
    serverLayout: TextInputLayout,
) {
    val currentConfig = loadServerConfigurationImpl()
    val options = createServerOptionsImpl(currentConfig)
    serverAdapter.submitList(options)

    val selectedOption =
        options.firstOrNull {
            !it.isAction && baseUrlKey(it.baseUrl) == baseUrlKey(currentConfig.baseUrl)
        }
    val displayName = selectedOption?.displayName ?: currentConfig.displayName
    val countryCode = selectedOption?.countryCode ?: currentConfig.countryCode
    val resolvedBaseUrl = selectedOption?.baseUrl ?: currentConfig.baseUrl

    serverInput.setText(displayName, false)
    serverInput.tag = resolvedBaseUrl
    updateServerFlag(serverLayout, countryCode.ifEmpty { DEFAULT_COUNTRY_CODE })
    updateServerStatusIcon(resolvedBaseUrl)
}

internal fun MyPlanetLite.createServerOptionsImpl(currentConfig: ServerConfiguration): List<ServerOption> {
    val builtIns = builtInServerOptions()
    val customs = loadCustomServers().map { it.toServerOption() }
    val connectedKey = baseUrlKey(currentConfig.baseUrl)
    val builtInItems =
        builtIns
            .distinctBy { baseUrlKey(it.baseUrl) }
            .toMutableList()
    val customItems =
        customs
            .filter { custom -> builtInItems.none { baseUrlKey(it.baseUrl) == baseUrlKey(custom.baseUrl) } }
            .distinctBy { baseUrlKey(it.baseUrl) }
            .toMutableList()
    if (
        currentConfig.baseUrl.isNotEmpty() &&
        builtInItems.none { baseUrlKey(it.baseUrl) == connectedKey } &&
        customItems.none { baseUrlKey(it.baseUrl) == connectedKey }
    ) {
        customItems.add(ServerOption(currentConfig.displayName, currentConfig.baseUrl, currentConfig.countryCode))
    }
    val items = builtInItems.toMutableList()
    if (customItems.isNotEmpty()) {
        items.add(ServerOption.divider())
        items.addAll(customItems)
        items.add(ServerOption.divider())
    }
    items.add(ServerOption(getString(R.string.server_option_clear), "", currentConfig.countryCode, actionType = ServerAction.CLEAR))
    items.add(
        ServerOption(getString(R.string.server_option_configure), "", currentConfig.countryCode, actionType = ServerAction.CONFIGURE),
    )
    return items
}

internal fun MyPlanetLite.builtInServerOptions(): List<ServerOption> =
    BUILT_IN_SERVERS.map {
        ServerOption(getString(it.nameRes), it.baseUrl, it.countryCode)
    }

internal fun MyPlanetLite.buildServerSuggestions(currentConfig: ServerConfiguration): MutableList<String> {
    val unique = linkedMapOf<String, String>()
    loadCustomServers().forEach { server ->
        val key = baseUrlKey(server.baseUrl)
        if (key.isNotEmpty()) {
            unique.putIfAbsent(key, server.baseUrl)
        }
    }
    builtInServerOptions().forEach { option ->
        val key = baseUrlKey(option.baseUrl)
        if (key.isNotEmpty()) {
            unique.putIfAbsent(key, option.baseUrl)
        }
    }
    if (currentConfig.baseUrl.isNotBlank()) {
        val key = baseUrlKey(currentConfig.baseUrl)
        if (key.isNotEmpty()) {
            unique.putIfAbsent(key, currentConfig.baseUrl)
        }
    }
    return unique.values.toMutableList()
}

internal fun MyPlanetLite.addCustomServerImpl(
    displayName: String,
    baseUrl: String,
    countryCode: String,
): Boolean {
    val sanitizedUrl = normalizeServerUrl(baseUrl)
    if (sanitizedUrl.isEmpty()) return false
    val key = baseUrlKey(sanitizedUrl)
    if (key.isEmpty()) return false
    val existing = loadCustomServers()
    if (existing.any { baseUrlKey(it.baseUrl) == key }) {
        return false
    }
    existing.add(CustomServer(displayName.ifBlank { sanitizedUrl }, sanitizedUrl, countryCode.uppercase(Locale.ROOT)))
    persistCustomServers(existing)
    return true
}

internal fun MyPlanetLite.loadCustomServers(): MutableList<CustomServer> {
    val raw = serverPreferences.getString(KEY_CUSTOM_SERVERS, null) ?: return mutableListOf()
    return try {
        val decoded = customServerAdapter.fromJson(raw) ?: return mutableListOf()
        decoded
            .filter { it.baseUrl.isNotBlank() && it.countryCode.isNotBlank() }
            .map {
                it.copy(
                    baseUrl = it.baseUrl.trim(),
                    countryCode = it.countryCode.uppercase(Locale.ROOT),
                    displayName = it.displayName.ifBlank { it.baseUrl.trim() },
                )
            }.toMutableList()
    } catch (error: Exception) {
        mutableListOf()
    }
}

internal fun MyPlanetLite.clearCustomServers() {
    val builtIns = builtInServerOptions()
    val fallback = builtIns.firstOrNull()
    if (fallback != null) {
        saveServerConfiguration(fallback.baseUrl, fallback.countryCode, fallback.displayName)
    }
    serverPreferences
        .edit()
        .remove(KEY_CUSTOM_SERVERS)
        .apply()
}

internal fun MyPlanetLite.persistCustomServers(servers: List<CustomServer>) {
    val json = customServerAdapter.toJson(servers)
    serverPreferences
        .edit()
        .putString(KEY_CUSTOM_SERVERS, json)
        .apply()
}

internal fun MyPlanetLite.baseUrlKey(url: String): String {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.trimEnd('/').lowercase(Locale.ROOT)
}

internal fun MyPlanetLite.normalizeServerUrl(input: String): String {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
    val normalized = withScheme.toHttpUrlOrNull() ?: return ""
    return normalized
        .newBuilder()
        .build()
        .toString()
        .trimEnd('/')
}






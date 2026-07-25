@file:Suppress("DEPRECATION")
/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-24
 */

package org.ole.planet.myplanet.lite
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.ImageView
import android.widget.AbsListView
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import com.blongho.country_data.World
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import kotlin.math.roundToInt

internal const val SECURE_PREFS_NAME = "secure_server_prefs"
internal const val KEY_REMEMBER_CREDENTIALS = "remember_credentials"
internal const val KEY_REMEMBERED_USERNAME = "remembered_username"
internal const val KEY_REMEMBERED_PASSWORD = "remembered_password"
internal const val EXTRA_ALLOW_AUTO_LOGIN = "extra_allow_auto_login"
internal const val MIN_PASSWORD_LENGTH = 1
internal const val KEY_SERVER_URL = "server_url"
internal const val KEY_SERVER_PARENT_CODE = "server_parent_code"
internal const val KEY_SERVER_CODE = "server_code"
internal const val KEY_COUNTRY_CODE = "country_code"
internal const val KEY_SERVER_DISPLAY_NAME = "server_display_name"
internal const val KEY_CUSTOM_SERVERS = "custom_servers"
internal const val KEY_SURVEY_TRANSLATIONS_ENABLED = "survey_translations_enabled"
internal const val KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED = "survey_translation_consent_accepted"
internal const val KEY_DEVICE_ANDROID_ID = "device_android_id"
internal const val DEFAULT_COUNTRY_CODE = "GT"
internal const val DEFAULT_SERVER_URL_PREFIX = "https://"
internal const val DEFAULT_SURVEY_TRANSLATION_ENABLED = true
internal const val LOGO_SHRUNK_DP = 50f
internal const val APP_VERSION_SHRUNK_BOTTOM_MARGIN_DP = 5f
internal const val LOGIN_SCROLL_SHRUNK_PADDING_TOP_DP = 5f
internal const val LOGIN_TIME_LENGTH = 13
internal val BUILT_IN_SERVERS =
    listOf(
        BuiltInServer(R.string.server_planet_xela, "http://10.82.1.30/", DEFAULT_COUNTRY_CODE),
        BuiltInServer(R.string.server_planet_guatemala, "https://planet.gt/", DEFAULT_COUNTRY_CODE),
        BuiltInServer(R.string.server_planet_san_pablo, "https://sanpablo.planet.gt/", DEFAULT_COUNTRY_CODE),
        BuiltInServer(R.string.server_planet_somalia, "https://planet.somalia.ole.org", "SO"),
        BuiltInServer(R.string.server_planet_learning, "https://planet.learning.ole.org/", "US"),
        BuiltInServer(R.string.server_planet_earth, "https://planet.earth.ole.org/", "US"),
        BuiltInServer(R.string.server_planet_vi, "https://planet.vi.ole.org/", "US"),
        BuiltInServer(R.string.server_planet_uriur, "https://planet.uriur.ole.org/", "KE"),
    )

internal typealias ServerOption = MyPlanetLite.ServerOption
internal typealias ServerAction = MyPlanetLite.ServerAction
internal typealias ServerConfiguration = MyPlanetLite.ServerConfiguration
internal typealias BuiltInServer = MyPlanetLite.BuiltInServer
internal typealias CustomServer = MyPlanetLite.CustomServer
internal typealias RememberedCredentials = MyPlanetLite.RememberedCredentials

internal open class ServerOptionAdapterBase(
    context: Context,
) : ArrayAdapter<ServerOption>(context, 0, mutableListOf()) {
    private val allItems = mutableListOf<ServerOption>()
    private val visibleItems = mutableListOf<ServerOption>()

    open fun submitList(items: List<ServerOption>) {
        allItems.clear()
        allItems.addAll(items)
        visibleItems.clear()
        visibleItems.addAll(items)
        notifyDataSetChanged()
    }

    override fun getCount(): Int = visibleItems.size

    override fun getItem(position: Int): ServerOption? = visibleItems.getOrNull(position)

    override fun areAllItemsEnabled(): Boolean = false

    override fun isEnabled(position: Int): Boolean = getItem(position)?.isDivider != true

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View = createView(position, convertView, parent, isDropdown = false)

    override fun getDropDownView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
    ): View = createView(position, convertView, parent, isDropdown = true)

    override fun getFilter(): Filter =
        object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults =
                FilterResults().apply {
                    values = ArrayList(allItems)
                    count = allItems.size
                }

            override fun publishResults(
                constraint: CharSequence?,
                results: FilterResults?,
            ) {
                visibleItems.clear()
                @Suppress("UNCHECKED_CAST")
                val values = results?.values as? List<ServerOption>
                if (!values.isNullOrEmpty()) {
                    visibleItems.addAll(values)
                } else {
                    visibleItems.addAll(allItems)
                }
                notifyDataSetChanged()
            }

            override fun convertResultToString(resultValue: Any?): CharSequence =
                (resultValue as? ServerOption)?.displayName
                    ?: super.convertResultToString(resultValue)
        }

    private fun createView(
        position: Int,
        convertView: View?,
        parent: ViewGroup,
        isDropdown: Boolean,
    ): View {
        val option = getItem(position)
            ?: return convertView ?: LayoutInflater.from(context).inflate(R.layout.item_server_option, parent, false)
        if (option.isDivider) {
            return createDividerView()
        }
        val view =
            convertView
                ?.takeIf { it.findViewById<TextView>(R.id.serverOptionName) != null }
                ?: LayoutInflater.from(context).inflate(R.layout.item_server_option, parent, false)

        val flagView: ImageView = view.findViewById(R.id.serverOptionFlag)
        val nameView: TextView = view.findViewById(R.id.serverOptionName)

        nameView.text = option.displayName

        val desiredMargin =
            if (isDropdown) {
                context.resources.getDimensionPixelSize(R.dimen.server_option_flag_margin)
            } else {
                0
            }
        val layoutParams = nameView.layoutParams
        if (layoutParams is ViewGroup.MarginLayoutParams && layoutParams.marginStart != desiredMargin) {
            layoutParams.marginStart = desiredMargin
            nameView.layoutParams = layoutParams
        }
        if (!isDropdown) {
            flagView.setImageDrawable(null)
            flagView.isVisible = false
        } else if (option.isAction) {
            flagView.setImageDrawable(null)
            flagView.isVisible = false
        } else {
            val flagRes = World.getFlagOf(option.countryCode)
            if (flagRes != 0) {
                flagView.setImageResource(flagRes)
                flagView.isVisible = true
            } else {
                flagView.setImageDrawable(null)
                flagView.isVisible = false
            }
        }

        return view
    }

    private fun createDividerView(): View =
        View(context).apply {
            layoutParams =
                AbsListView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (1 * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1),
                )
            setBackgroundColor(ContextCompat.getColor(context, R.color.dashboard_drawer_divider))
        }
}

internal fun MyPlanetLite.updateServerStatusIcon(baseUrl: String?) {
    if (!isServerStatusIconInitialized()) {
        return
    }
    val sanitizedUrl = baseUrl?.trim().orEmpty()
    currentServerBaseUrl = sanitizedUrl
    serverStatusJob?.cancel()
    if (sanitizedUrl.isEmpty()) {
        showServerDisconnectedState(allowRetry = false)
        return
    }
    checkServerConnectivity(sanitizedUrl)
}

internal fun MyPlanetLite.checkServerConnectivity(baseUrl: String) {
    if (!isServerStatusIconInitialized()) {
        return
    }
    serverStatusIconView.isVisible = true
    serverStatusIconView.setOnClickListener(null)
    serverStatusJob =
        lifecycleScope.launch {
            showServerStatusChecking()
            val result = withContext(Dispatchers.IO) { serverConnectivityRepository.checkServerConnectivity(baseUrl) }
            if (!isActive) {
                return@launch
            }
            if (result.reachable) {
                persistServerMetadata(baseUrl, result.parentCode, result.code)
                showServerConnectedState()
            } else {
                showServerDisconnectedState(allowRetry = true)
            }
        }
}

internal fun MyPlanetLite.persistServerMetadata(
    baseUrl: String,
    parentCode: String?,
    code: String?,
) {
    serverPreferences
        .edit()
        .apply {
            putString(KEY_SERVER_URL, baseUrl)
            if (parentCode != null) {
                putString(KEY_SERVER_PARENT_CODE, parentCode)
            } else {
                remove(KEY_SERVER_PARENT_CODE)
            }
            if (code != null) {
                putString(KEY_SERVER_CODE, code)
            } else {
                remove(KEY_SERVER_CODE)
            }
        }.apply()
}

internal fun MyPlanetLite.showServerStatusChecking() {
    serverStatusIconView.setImageResource(R.drawable.ic_server_disconnected)
    serverStatusIconView.alpha = 0.5f
    serverStatusIconView.isEnabled = false
    serverStatusIconView.isClickable = false
    serverStatusIconView.contentDescription = getString(R.string.server_status_checking)
    serverStatusIconView.isVisible = true
    isServerReachable = false
    updateLoginButtonAvailability()
}

internal fun MyPlanetLite.showServerConnectedState() {
    serverStatusIconView.setImageResource(R.drawable.ic_server_connected)
    serverStatusIconView.alpha = 1f
    serverStatusIconView.isEnabled = false
    serverStatusIconView.isClickable = false
    serverStatusIconView.setOnClickListener(null)
    serverStatusIconView.contentDescription = getString(R.string.server_status_connected)
    serverStatusIconView.isVisible = true
    isServerReachable = true
    updateLoginButtonAvailability()
    maybeRestoreSessionOrAutoLogin()
}

internal fun MyPlanetLite.showServerDisconnectedState(allowRetry: Boolean) {
    serverStatusIconView.setImageResource(R.drawable.ic_server_disconnected)
    serverStatusIconView.alpha = 1f
    serverStatusIconView.isEnabled = allowRetry
    serverStatusIconView.isClickable = allowRetry
    val canRetry = allowRetry && currentServerBaseUrl.isNotEmpty()
    if (canRetry) {
        serverStatusIconView.setOnClickListener { checkServerConnectivity(currentServerBaseUrl) }
    } else {
        serverStatusIconView.setOnClickListener(null)
    }
    val descriptionRes =
        if (canRetry) {
            R.string.server_status_disconnected_retry
        } else {
            R.string.server_status_disconnected
        }
    serverStatusIconView.contentDescription = getString(descriptionRes)
    serverStatusIconView.isVisible = true
    isServerReachable = false
    updateLoginButtonAvailability()
}

internal fun MyPlanetLite.updateLoginButtonAvailability() {
    if (!isLoginButtonInitialized()) {
        return
    }
    val canAuthenticate = isServerReachable && !isLoginInProgress
    loginButtonView.isEnabled = canAuthenticate
    if (isSignupButtonInitialized()) {
        signupButtonView.isEnabled = canAuthenticate
        signupButtonView.alpha = if (canAuthenticate) 1f else 0.5f
    }
}

internal fun MyPlanetLite.showDropDownWhenSafe(view: MaterialAutoCompleteTextView) {
    if (isFinishing || isDestroyed) return
    if (view.isAttachedToWindow && view.windowToken != null && view.hasWindowFocus()) {
        view.showDropDown()
    } else {
        view.post {
            if (!isFinishing && !isDestroyed && view.isAttachedToWindow &&
                view.windowToken != null && view.hasWindowFocus()
            ) {
                view.showDropDown()
            }
        }
    }
}

internal fun MyPlanetLite.shrinkLogo(
    logo: ImageView,
    appVersion: TextView,
) {
    if (isLogoShrunk || originalLogoWidth == 0 || originalLogoHeight == 0 || shrunkLogoSizePx == 0) {
        return
    }
    logo.updateLayoutParams {
        width = shrunkLogoSizePx
        height = shrunkLogoSizePx
    }
    if (shrunkAppVersionBottomMarginPx != 0) {
        appVersion.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = shrunkAppVersionBottomMarginPx
        }
    }
    isLogoShrunk = true
}

internal fun MyPlanetLite.restoreLogo(
    logo: ImageView,
    appVersion: TextView,
) {
    if (!isLogoShrunk || originalLogoWidth == 0 || originalLogoHeight == 0) {
        return
    }
    logo.updateLayoutParams {
        width = originalLogoWidth
        height = originalLogoHeight
    }
    if (originalAppVersionBottomMargin != 0) {
        appVersion.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = originalAppVersionBottomMargin
        }
    }
    isLogoShrunk = false
}

internal fun MyPlanetLite.shrinkLoginScrollPadding(loginScroll: ScrollView) {
    if (isLoginScrollPaddingShrunk || originalLoginScrollPaddingTop == 0 ||
        shrunkLoginScrollPaddingTopPx == 0
    ) {
        return
    }
    loginScroll.setPadding(
        loginScroll.paddingLeft,
        shrunkLoginScrollPaddingTopPx,
        loginScroll.paddingRight,
        loginScroll.paddingBottom,
    )
    isLoginScrollPaddingShrunk = true
}

internal fun MyPlanetLite.restoreLoginScrollPadding(loginScroll: ScrollView) {
    if (!isLoginScrollPaddingShrunk || originalLoginScrollPaddingTop == 0) {
        return
    }
    loginScroll.setPadding(
        loginScroll.paddingLeft,
        originalLoginScrollPaddingTop,
        loginScroll.paddingRight,
        loginScroll.paddingBottom,
    )
    isLoginScrollPaddingShrunk = false
}






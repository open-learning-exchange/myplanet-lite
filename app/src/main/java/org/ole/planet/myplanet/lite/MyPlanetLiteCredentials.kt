@file:Suppress("DEPRECATION")
/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-24
 */

package org.ole.planet.myplanet.lite
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.putPlanetAppId


internal suspend fun MyPlanetLite.handleLoginResult(
    result: AuthResult,
    errorText: TextView,
    loginButton: Button,
    progress: ProgressBar,
    username: String,
    password: String,
    rememberCheckBox: MaterialCheckBox,
    serverBaseUrl: String,
) {
    setLoadingState(isLoading = false, loginButton = loginButton, progress = progress)
    when (result) {
        is AuthResult.Success -> {
            ProfileCredentialsStore.setSessionCredentials(StoredCredentials(username, password))
            if (rememberCheckBox.isChecked) {
                saveRememberedCredentials(username, password)
                rememberedLoginCredentials = RememberedCredentials(username, password)
            } else {
                clearRememberedCredentials()
                rememberedLoginCredentials = null
            }
            shouldAutoLoginOnLaunch = autoLoginEnabled && rememberCheckBox.isChecked && rememberedLoginCredentials != null
            credentialsAutoLoginAttempted = false
            sessionRestoreAttempted = false
            recordLoginActivity(serverBaseUrl, username, result.response.sessionCookie)
            val profileUsername = result.response.name?.takeIf { it.isNotBlank() } ?: username
            userProfileSync.clearProfile()
            val fetched =
                userProfileSync.refreshProfile(
                    serverBaseUrl = serverBaseUrl,
                    username = profileUsername,
                    sessionCookie = result.response.sessionCookie,
                )
            launchDashboard()
        }

        is AuthResult.Error -> {
            val errorMessage =
                when (result.code) {
                    401, 403 -> getString(R.string.login_invalid_credentials)
                    else -> result.message.takeIf { it.isNotBlank() } ?: getString(R.string.login_generic_error)
                }
            errorText.text = errorMessage
            errorText.isVisible = true
        }

        is AuthResult.Failure.InvalidCredentials -> {
            errorText.text = getString(R.string.login_invalid_credentials)
            errorText.isVisible = true
        }

        is AuthResult.Failure.NetworkError -> {
            errorText.text = getString(R.string.login_generic_error)
            errorText.isVisible = true
        }
    }
}

internal fun MyPlanetLite.recordLoginActivity(
    serverBaseUrl: String,
    username: String,
    sessionCookie: String?,
) {
    val sanitizedBaseUrl = serverBaseUrl.trim()
    if (sanitizedBaseUrl.isEmpty()) {
        return
    }
    val requestUrl = buildLoginActivityUrl(sanitizedBaseUrl) ?: return
    val payload = buildLoginActivityPayload(username) ?: return

    lifecycleScope.launch {
        serverConnectivityRepository.recordLoginActivity(requestUrl, payload, sessionCookie)
    }
}

internal fun MyPlanetLite.buildLoginActivityUrl(baseUrl: String): String? =
    baseUrl
        .toHttpUrlOrNull()
        ?.newBuilder()
        ?.addPathSegments("db/login_activities")
        ?.build()
        ?.toString()

internal fun MyPlanetLite.buildLoginActivityPayload(username: String): JSONObject? {
    val parentCode = serverPreferences.getString(KEY_SERVER_PARENT_CODE, null)
    val code = serverPreferences.getString(KEY_SERVER_CODE, null)
    val androidId = serverPreferences.getString(KEY_DEVICE_ANDROID_ID, null)
    val customDeviceName = serverPreferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)

    val deviceName =
        org.ole.planet.myplanet.lite.util.DeviceUtils
            .getDeviceName()
    val loginTimeMillis = System.currentTimeMillis()

    return runCatching {
        JSONObject().apply {
            put("user", username)
            put("type", "login")
            put("loginTime", loginTimeMillis)
            put("logoutTime", 0)
            put("createdOn", code?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("parentCode", parentCode?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("androidId", androidId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            put("deviceName", deviceName)
            put("customDeviceName", customDeviceName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            putPlanetAppId()
        }
    }.getOrNull()
}

internal fun MyPlanetLite.setLoadingState(
    isLoading: Boolean,
    loginButton: Button,
    progress: ProgressBar,
) {
    isLoginInProgress = isLoading
    if (isLoading) {
        loginButton.isEnabled = false
    }
    updateLoginButtonAvailability()
    progress.isVisible = isLoading
}

internal fun MyPlanetLite.saveRememberedCredentials(
    username: String,
    password: String,
) {
    securePreferences
        .edit()
        .putBoolean(KEY_REMEMBER_CREDENTIALS, true)
        .putString(KEY_REMEMBERED_USERNAME, username)
        .putString(
            org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
                .getPasswordKey(username),
            password,
        ).apply()
}

internal fun MyPlanetLite.clearRememberedCredentials() {
    val editor = securePreferences.edit()
    editor.putBoolean(KEY_REMEMBER_CREDENTIALS, false)
    val username = securePreferences.getString(KEY_REMEMBERED_USERNAME, null)
    if (username != null) {
        editor.remove(
            org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
                .getPasswordKey(username),
        )
    }
    editor.remove(KEY_REMEMBERED_USERNAME)
    editor.remove(KEY_REMEMBERED_PASSWORD)
    editor.apply()
}

internal fun MyPlanetLite.clearStoredSessionIfNotRemembered() {
    val rememberedInSecurePrefs = securePreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)
    val rememberedInLegacyPrefs = serverPreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)
    if (rememberedInSecurePrefs || rememberedInLegacyPrefs) {
        return
    }
    val baseUrl = loadServerConfigurationImpl().baseUrl.trim()
    if (baseUrl.isEmpty()) {
        return
    }
    lifecycleScope.launch {
        val authService = AuthDependencies.provideAuthService(this@clearStoredSessionIfNotRemembered, baseUrl)
        authService.logout()
    }
}

internal fun MyPlanetLite.loadRememberedCredentials(): RememberedCredentials? {
    if (serverPreferences.contains(KEY_REMEMBER_CREDENTIALS)) {
        val legacyUsername = serverPreferences.getString(KEY_REMEMBERED_USERNAME, null)
        val legacyPassword = serverPreferences.getString(KEY_REMEMBERED_PASSWORD, null)
        val legacyRemembered = serverPreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)

        if (legacyRemembered && legacyUsername != null && legacyPassword != null) {
            saveRememberedCredentials(legacyUsername, legacyPassword)
        }

        serverPreferences
            .edit()
            .remove(KEY_REMEMBER_CREDENTIALS)
            .remove(KEY_REMEMBERED_USERNAME)
            .remove(KEY_REMEMBERED_PASSWORD)
            .apply()
    }

    if (!securePreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)) {
        return null
    }
    val username = securePreferences.getString(KEY_REMEMBERED_USERNAME, null)
    var password: String? = null
    if (!username.isNullOrEmpty()) {
        val dynamicKey =
            org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
                .getPasswordKey(username)
        password = securePreferences.getString(dynamicKey, null)

        if (password.isNullOrEmpty()) {
            val legacyPassword = securePreferences.getString(KEY_REMEMBERED_PASSWORD, null)
            if (!legacyPassword.isNullOrEmpty()) {
                password = legacyPassword
                securePreferences
                    .edit()
                    .putString(dynamicKey, legacyPassword)
                    .remove(KEY_REMEMBERED_PASSWORD)
                    .apply()
            }
        }
    }
    if (username.isNullOrEmpty() && password.isNullOrEmpty()) {
        return null
    }
    return RememberedCredentials(username.orEmpty(), password.orEmpty())
}

internal fun MyPlanetLite.isSurveyTranslationEnabled(): Boolean =
    serverPreferences.getBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, DEFAULT_SURVEY_TRANSLATION_ENABLED)

internal fun MyPlanetLite.setSurveyTranslationEnabled(enabled: Boolean) {
    serverPreferences
        .edit()
        .putBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, enabled)
        .apply()
}

internal fun MyPlanetLite.isSurveyTranslationConsentAccepted(): Boolean = serverPreferences.getBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)

internal fun MyPlanetLite.setSurveyTranslationConsentAccepted(accepted: Boolean) {
    serverPreferences
        .edit()
        .putBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, accepted)
        .apply()
}

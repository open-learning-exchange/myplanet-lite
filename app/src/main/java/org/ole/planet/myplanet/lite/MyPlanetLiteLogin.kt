@file:Suppress("DEPRECATION")
/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-24
 */

package org.ole.planet.myplanet.lite
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies

internal fun MyPlanetLite.configureLogin() {
    initializeLoginViews()
    setupRememberMeCheckbox()
    setupServerDropdown()
    setupLoginButton()
    maybeRestoreSessionOrAutoLogin()
}

internal fun MyPlanetLite.initializeLoginViews() {
    loginButtonView = findViewById(R.id.loginButton)
    rememberMeCheckBox = findViewById(R.id.rememberCheckBox)
    loginErrorTextView = findViewById(R.id.errorText)
    loginProgressView = findViewById(R.id.loginProgress)

    updateLoginButtonAvailability()

    serverAdapter = ServerOptionAdapter(this)
    serverAutoCompleteView.setAdapter(serverAdapter)
    serverInputLayoutView.setStartIconTintList(null)

    refreshServerOptions(serverAutoCompleteView, serverInputLayoutView)
}

internal fun MyPlanetLite.setupRememberMeCheckbox() {
    val rememberedCredentials = loadRememberedCredentials()
    applyRememberedCredentials(rememberedCredentials)

    rememberMeCheckBox.setOnCheckedChangeListener { _, isChecked ->
        if (suppressRememberListener) {
            return@setOnCheckedChangeListener
        }
        if (!isChecked) {
            clearRememberedCredentials()
            rememberedLoginCredentials = null
            shouldAutoLoginOnLaunch = false
            credentialsAutoLoginAttempted = false
        } else {
            val restored = loadRememberedCredentials()
            rememberedLoginCredentials = restored
            shouldAutoLoginOnLaunch = autoLoginEnabled && restored?.let { creds ->
                creds.username.isNotBlank() && creds.password.length >= MIN_PASSWORD_LENGTH
            } ?: false
            if (!shouldAutoLoginOnLaunch) {
                credentialsAutoLoginAttempted = false
            }
        }
    }
}

internal fun MyPlanetLite.setupServerDropdown() {
    serverAutoCompleteView.setOnClickListener {
        showDropDownWhenSafe(serverAutoCompleteView)
    }
    serverAutoCompleteView.setOnFocusChangeListener { _, hasFocus ->
        if (hasFocus) {
            showDropDownWhenSafe(serverAutoCompleteView)
        }
    }
    serverAutoCompleteView.setOnItemClickListener { _, _, position, _ ->
        val selected = serverAdapter.getItem(position) ?: return@setOnItemClickListener
        if (selected.isAction) {
            when (selected.actionType) {
                ServerAction.CONFIGURE -> {
                        val previousConfig = loadServerConfigurationImpl()
                    serverAutoCompleteView.setText(previousConfig.displayName, false)
                    serverAutoCompleteView.tag = previousConfig.baseUrl
                    updateServerFlag(serverInputLayoutView, previousConfig.countryCode)
                    updateServerStatusIcon(previousConfig.baseUrl)
                    serverAutoCompleteView.dismissDropDown()
                        showServerConfigurationDialogImpl(serverAutoCompleteView, serverInputLayoutView)
                }

                ServerAction.CLEAR -> {
                    clearCustomServers()
                    refreshServerOptions(serverAutoCompleteView, serverInputLayoutView)
                    serverAutoCompleteView.dismissDropDown()
                }

                ServerAction.DIVIDER -> {
                    serverAutoCompleteView.dismissDropDown()
                }

                null -> {
                    serverAutoCompleteView.dismissDropDown()
                }
            }
        } else {
            serverAutoCompleteView.setText(selected.displayName, false)
            serverAutoCompleteView.tag = selected.baseUrl
            updateServerFlag(serverInputLayoutView, selected.countryCode)
            saveServerConfiguration(selected.baseUrl, selected.countryCode, selected.displayName)
            updateServerStatusIcon(selected.baseUrl)
            serverAutoCompleteView.dismissDropDown()
        }
    }
    serverAutoCompleteView.keyListener = null
}

internal fun MyPlanetLite.setupLoginButton() {
    val usernameLayout: TextInputLayout = findViewById(R.id.usernameInputLayout)
    val passwordLayout: TextInputLayout = findViewById(R.id.passwordInputLayout)

    loginButtonView.setOnClickListener {
        usernameLayout.error = null
        passwordLayout.error = null
        serverInputLayoutView.error = null
        loginErrorTextView.isVisible = false

        val username =
            loginUsernameInput.text
                ?.toString()
                ?.trim()
                .orEmpty()
        val password = loginPasswordInput.text?.toString().orEmpty()
        val serverBaseUrl = (serverAutoCompleteView.tag as? String).orEmpty().trim()

        var hasError = false
        if (username.isEmpty()) {
            usernameLayout.error = getString(R.string.login_username_error)
            hasError = true
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            passwordLayout.error = getString(R.string.login_password_error)
            hasError = true
        }
        if (serverBaseUrl.isEmpty()) {
            serverInputLayoutView.error = getString(R.string.login_server_error)
            hasError = true
        }
        if (hasError) return@setOnClickListener

        ensureSurveyTranslationConsent {
            val authService = AuthDependencies.provideAuthService(this, serverBaseUrl)

            setLoadingState(isLoading = true, loginButton = loginButtonView, progress = loginProgressView)

            lifecycleScope.launch {
                val result = authService.login(username, password)
                handleLoginResult(
                    result,
                    loginErrorTextView,
                    loginButtonView,
                    loginProgressView,
                    username,
                    password,
                    rememberMeCheckBox,
                    serverBaseUrl,
                )
            }
        }
    }
}

internal fun MyPlanetLite.ensureSurveyTranslationConsent(onConsentGranted: () -> Unit) {
    if (isSurveyTranslationConsentAccepted()) {
        onConsentGranted()
        return
    }

    if (!serverPreferences.contains(KEY_SURVEY_TRANSLATIONS_ENABLED)) {
        setSurveyTranslationEnabled(DEFAULT_SURVEY_TRANSLATION_ENABLED)
    }

    val dialogView =
        LayoutInflater
            .from(this)
            .inflate(R.layout.dialog_survey_translation_consent, null, false)
    val consentCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.surveyTranslationConsentCheckBox)
    val policyLink = dialogView.findViewById<TextView>(R.id.surveyTranslationPolicyLink)

    consentCheckBox.isChecked = isSurveyTranslationEnabled()

    val dialog =
        AlertDialog
            .Builder(this)
            .setTitle(R.string.login_survey_translation_consent_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dashboard_survey_translation_consent_accept) { alertDialog, _ ->
                val translationsEnabled = consentCheckBox.isChecked
                setSurveyTranslationEnabled(translationsEnabled)
                setSurveyTranslationConsentAccepted(true)
                alertDialog.dismiss()
                onConsentGranted()
            }.setNegativeButton(R.string.dashboard_survey_translation_consent_cancel) { alertDialog, _ ->
                setSurveyTranslationEnabled(false)
                setSurveyTranslationConsentAccepted(false)
                alertDialog.dismiss()
            }.create()

    dialog.setOnCancelListener {
        setSurveyTranslationEnabled(false)
        setSurveyTranslationConsentAccepted(false)
    }

    policyLink.setOnClickListener {
        if (!isLoginInProgress) {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
    }

    dialog.show()
}

internal fun MyPlanetLite.applyRememberedCredentials(remembered: RememberedCredentials? = loadRememberedCredentials()) {
    rememberedLoginCredentials = remembered
    suppressRememberListener = true
    if (isRememberCheckBoxInitialized()) {
        rememberMeCheckBox.isChecked = remembered != null
    }
    if (remembered != null) {
        loginUsernameInput.setText(remembered.username)
        loginPasswordInput.setText(remembered.password)
    } else {
        loginUsernameInput.text?.clear()
        loginPasswordInput.text?.clear()
    }
    suppressRememberListener = false
    shouldAutoLoginOnLaunch = autoLoginEnabled && remembered?.let { creds ->
        creds.username.isNotBlank() && creds.password.length >= MIN_PASSWORD_LENGTH
    } ?: false
    if (shouldAutoLoginOnLaunch) {
        credentialsAutoLoginAttempted = false
    }
    if (shouldAutoLoginOnLaunch && isServerReachable) {
        maybeRestoreSessionOrAutoLogin()
    }
}

internal fun MyPlanetLite.maybeRestoreSessionOrAutoLogin() {
    if (!autoLoginEnabled) {
        return
    }
    if (!isLoginButtonInitialized() || !isLoginProgressInitialized() || !isLoginErrorInitialized()) {
        return
    }
    if (!isServerReachable || isLoginInProgress || sessionRestoreInProgress) {
        return
    }
    val baseUrl = currentServerBaseUrl.takeIf { it.isNotBlank() } ?: return
    if (!sessionRestoreAttempted) {
        attemptStoredSessionRestoreImpl(baseUrl)
    } else {
        maybeAutoLogin()
    }
}

internal fun MyPlanetLite.attemptStoredSessionRestoreImpl(baseUrl: String) {
    if (isLoginInProgress || sessionRestoreInProgress) {
        return
    }
    sessionRestoreAttempted = true
    sessionRestoreInProgress = true
    lifecycleScope.launch {
        try {
            val authService = AuthDependencies.provideAuthService(this@attemptStoredSessionRestoreImpl, baseUrl)
            val storedToken = authService.getStoredToken()
            if (storedToken.isNullOrBlank()) {
                maybeAutoLogin()
                return@launch
            }
            val cachedUsername =
                withContext(Dispatchers.IO) {
                    userProfileDatabase.getProfile()?.username
                }
            if (cachedUsername.isNullOrBlank()) {
                maybeAutoLogin()
                return@launch
            }
            loginErrorTextView.isVisible = false
            setLoadingState(isLoading = true, loginButton = loginButtonView, progress = loginProgressView)
            val refreshed = userProfileSync.refreshProfile(baseUrl, cachedUsername, storedToken)
            if (refreshed) {
                launchDashboard()
            } else {
                authService.logout()
                setLoadingState(isLoading = false, loginButton = loginButtonView, progress = loginProgressView)
                maybeAutoLogin()
            }
        } catch (e: Exception) {
            Log.e("MyPlanetLite", "Error during session restore")
            setLoadingState(isLoading = false, loginButton = loginButtonView, progress = loginProgressView)
            maybeAutoLogin()
        } finally {
            sessionRestoreInProgress = false
        }
    }
}

internal fun MyPlanetLite.maybeAutoLogin() {
    if (!shouldAutoLoginOnLaunch || credentialsAutoLoginAttempted) {
        return
    }
    if (!isRememberCheckBoxInitialized() || !rememberMeCheckBox.isChecked) {
        return
    }
    if (!isLoginProgressInitialized() || !isLoginErrorInitialized()) {
        return
    }
    val credentials = rememberedLoginCredentials ?: return
    val baseUrl = currentServerBaseUrl.takeIf { it.isNotBlank() } ?: return
    if (!isServerReachable || isLoginInProgress) {
        return
    }
    val username = credentials.username.trim()
    val password = credentials.password
    if (username.isEmpty() || password.length < MIN_PASSWORD_LENGTH) {
        return
    }
    credentialsAutoLoginAttempted = true
    shouldAutoLoginOnLaunch = false
    loginErrorTextView.isVisible = false
    setLoadingState(isLoading = true, loginButton = loginButtonView, progress = loginProgressView)
    val authService = AuthDependencies.provideAuthService(this, baseUrl)
    lifecycleScope.launch {
        val result = authService.login(username, password)
        handleLoginResult(
            result,
            loginErrorTextView,
            loginButtonView,
            loginProgressView,
            username,
            password,
            rememberMeCheckBox,
            baseUrl,
        )
    }
}




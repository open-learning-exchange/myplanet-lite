/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Filter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.lifecycleScope

import com.blongho.country_data.World
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.model.ServerMetadataResponse
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.profile.UserProfileSync

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

import kotlin.math.roundToInt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.util.ArrayList
import java.util.Locale

class MyPlanetLite : AppCompatActivity() {

    private var originalLogoWidth = 0
    private var originalLogoHeight = 0
    private var shrunkLogoSizePx = 0
    private var isLogoShrunk = false
    private var originalAppVersionBottomMargin = 0
    private var shrunkAppVersionBottomMarginPx = 0
    private var originalLoginScrollPaddingTop = 0
    private var shrunkLoginScrollPaddingTopPx = 0
    private var isLoginScrollPaddingShrunk = false

    private lateinit var serverAdapter: ServerOptionAdapter
    private lateinit var serverInputLayoutView: TextInputLayout
    private lateinit var serverAutoCompleteView: MaterialAutoCompleteTextView
    private lateinit var loginUsernameInput: TextInputEditText
    private lateinit var loginPasswordInput: TextInputEditText
    private lateinit var rememberMeCheckBox: MaterialCheckBox
    private var suppressRememberListener = false
    private lateinit var serverStatusIconView: ImageView
    private lateinit var loginButtonView: Button
    private lateinit var loginProgressView: ProgressBar
    private lateinit var loginErrorTextView: TextView
    private lateinit var signupPromptView: TextView
    private lateinit var signupButtonView: Button
    private lateinit var privacyPolicyPromptView: TextView
    private val connectivityClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    private var serverStatusJob: Job? = null
    private var currentServerBaseUrl: String = ""
    private var isServerReachable = false
    private var isLoginInProgress = false
    private var rememberedLoginCredentials: RememberedCredentials? = null
    private var shouldAutoLoginOnLaunch = false
    private var sessionRestoreAttempted = false
    private var credentialsAutoLoginAttempted = false
    private var sessionRestoreInProgress = false
    private var autoLoginEnabled = false
    private val userProfileDatabase: UserProfileDatabase by lazy {
        UserProfileDatabase.getInstance(applicationContext)
    }
    private val userProfileSync: UserProfileSync by lazy {
        UserProfileSync(connectivityClient, userProfileDatabase)
    }
    private val serverPreferences: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }
    private val securePreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            applicationContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    private val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    private val serverMetadataAdapter by lazy { moshi.adapter(ServerMetadataResponse::class.java) }

    private val signupLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: run {
                applyRememberedCredentials()
                return@registerForActivityResult
            }
            val autoLogin = data.getBooleanExtra(SignupActivity.EXTRA_AUTO_LOGIN, false)
            if (autoLogin) {
                val username = data.getStringExtra(SignupActivity.EXTRA_USERNAME).orEmpty()
                val password = data.getStringExtra(SignupActivity.EXTRA_PASSWORD).orEmpty()
                loginUsernameInput.setText(username)
                loginPasswordInput.setText(password)
                suppressRememberListener = true
                rememberMeCheckBox.isChecked = true
                suppressRememberListener = false
            } else {
                applyRememberedCredentials()
            }
        }
    }

    private var deepLinkPostId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        LanguagePreferences.applySavedLocale(this, SUPPORTED_LANGUAGES.map { it.languageTag })
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.white)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true
        ProfileCredentialsStore.setSessionCredentials(null)
        migrateCredentialsIfNecessary()
        clearStoredSessionIfNotRemembered()
        autoLoginEnabled = intent?.getBooleanExtra(EXTRA_ALLOW_AUTO_LOGIN, false) == true
        deepLinkPostId = extractDeepLinkPostId(intent)

        if (savedInstanceState != null) {
            val contentRoot: View? = findViewById(android.R.id.content)
            contentRoot?.let { root ->
                root.alpha = 0f
                root.doOnPreDraw {
                    root.animate()
                        .alpha(1f)
                        .setDuration(LANGUAGE_TRANSITION_DURATION_MS)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                }
            }
        }

        val logoImageView: ImageView = findViewById(R.id.logoImageView)
        val languageSelectorIcon: ImageView = findViewById(R.id.languageSelectorIcon)
        serverInputLayoutView = findViewById(R.id.serverInputLayout)
        serverAutoCompleteView = findViewById(R.id.serverUrlInput)
        loginUsernameInput = findViewById(R.id.usernameInput)
        loginPasswordInput = findViewById(R.id.passwordInput)
        serverStatusIconView = findViewById(R.id.serverStatusIcon)
        val usernameInput: TextInputEditText = findViewById(R.id.usernameInput)
        val passwordInput: TextInputEditText = findViewById(R.id.passwordInput)
        val appVersionTextView: TextView = findViewById(R.id.appVersionTextView)
        val poweredByTextView: TextView = findViewById(R.id.poweredByText)
        val loginScroll: ScrollView = findViewById(R.id.loginScroll)
        signupPromptView = findViewById(R.id.signupPrompt)
        signupButtonView = findViewById(R.id.signupButton)
        privacyPolicyPromptView = findViewById(R.id.privacyPolicyPrompt)

        appVersionTextView.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        poweredByTextView.text = HtmlCompat.fromHtml(
            getString(R.string.powered_by_plataformas_informaticas),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        poweredByTextView.movementMethod = LinkMovementMethod.getInstance()

        signupPromptView.text = getString(R.string.login_signup_prompt)
        signupButtonView.setOnClickListener {
            if (!signupButtonView.isEnabled || isLoginInProgress || !isServerReachable) {
                return@setOnClickListener
            }
            val serverBaseUrl = (serverAutoCompleteView.tag as? String).orEmpty()
            val intent = Intent(this, SignupActivity::class.java).apply {
                putExtra(SignupActivity.EXTRA_SERVER_BASE_URL, serverBaseUrl)
            }
            signupLauncher.launch(intent)
        }

        privacyPolicyPromptView.text = HtmlCompat.fromHtml(
            getString(R.string.login_privacy_policy_prompt),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        privacyPolicyPromptView.setOnClickListener {
            if (!privacyPolicyPromptView.isEnabled || isLoginInProgress) {
                return@setOnClickListener
            }
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            startActivity(intent)
        }

        shrunkLogoSizePx = (resources.displayMetrics.density * LOGO_SHRUNK_DP).roundToInt()
        shrunkAppVersionBottomMarginPx =
            (resources.displayMetrics.density * APP_VERSION_SHRUNK_BOTTOM_MARGIN_DP).roundToInt()
        shrunkLoginScrollPaddingTopPx =
            (resources.displayMetrics.density * LOGIN_SCROLL_SHRUNK_PADDING_TOP_DP).roundToInt()
        logoImageView.doOnLayout {
            if (originalLogoWidth == 0 || originalLogoHeight == 0) {
                originalLogoWidth = it.width
                originalLogoHeight = it.height
            }
        }
        appVersionTextView.doOnLayout {
            if (originalAppVersionBottomMargin == 0) {
                val params = it.layoutParams as? ViewGroup.MarginLayoutParams
                originalAppVersionBottomMargin = params?.bottomMargin ?: 0
            }
        }
        loginScroll.doOnLayout {
            if (originalLoginScrollPaddingTop == 0) {
                originalLoginScrollPaddingTop = it.paddingTop
            }
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)

            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val focusedOnLoginFields = loginUsernameInput.hasFocus() || loginPasswordInput.hasFocus()
            if (imeVisible && focusedOnLoginFields) {
                shrinkLogo(logoImageView, appVersionTextView)
                shrinkLoginScrollPadding(loginScroll)
            } else {
                restoreLogo(logoImageView, appVersionTextView)
                restoreLoginScrollPadding(loginScroll)
            }

            insets
        }

        languageSelectorIcon.setOnClickListener {
            showLanguageSelectionDialog()
        }

        World.init(applicationContext)

        configureLogin()
    }

    private fun launchDashboard() {
        val dashboardIntent = Intent(this, DashboardActivity::class.java)
        deepLinkPostId?.let { postId ->
            dashboardIntent.putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
        }
        startActivity(dashboardIntent)
        deepLinkPostId = null
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (::serverAdapter.isInitialized && ::serverInputLayoutView.isInitialized && ::serverAutoCompleteView.isInitialized) {
            refreshServerOptions(serverAutoCompleteView, serverInputLayoutView)
        }
    }

    private fun applyDeviceOrientationLock() {
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    private fun showLanguageSelectionDialog() {
        val options = SUPPORTED_LANGUAGES
        val optionLabels = options.map { getString(it.labelRes) }.toTypedArray()
        val currentLanguage = LanguagePreferences.getSelectedLanguage(
            this,
            SUPPORTED_LANGUAGES.map { it.languageTag }
        )
        val currentIndex = options.indexOfFirst { it.languageTag.equals(currentLanguage, ignoreCase = true) }
            .takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle(R.string.language_selector_title)
            .setSingleChoiceItems(optionLabels, currentIndex) { dialog, which ->
                val selectedOption = options[which]
                val changed = LanguagePreferences.setSelectedLanguage(
                    this,
                    selectedOption.languageTag,
                    SUPPORTED_LANGUAGES.map { it.languageTag }
                )
                dialog.dismiss()
                if (changed) {
                    restartWithLanguageAnimation()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restartWithLanguageAnimation() {
        val contentRoot: View? = findViewById(android.R.id.content)
        if (contentRoot == null || isFinishing || isDestroyed) {
            recreate()
            return
        }

        contentRoot.animate().cancel()
        contentRoot.animate()
            .alpha(0f)
            .setDuration(LANGUAGE_TRANSITION_DURATION_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                recreate()
            }
            .start()
    }

    private fun configureLogin() {
        val usernameLayout: TextInputLayout = findViewById(R.id.usernameInputLayout)
        val passwordLayout: TextInputLayout = findViewById(R.id.passwordInputLayout)
        val serverLayout: TextInputLayout = serverInputLayoutView
        val serverInput: MaterialAutoCompleteTextView = serverAutoCompleteView
        loginButtonView = findViewById(R.id.loginButton)
        rememberMeCheckBox = findViewById(R.id.rememberCheckBox)
        loginErrorTextView = findViewById(R.id.errorText)
        loginProgressView = findViewById(R.id.loginProgress)

        updateLoginButtonAvailability()

        serverAdapter = ServerOptionAdapter(this)
        serverInput.setAdapter(serverAdapter)
        serverLayout.setStartIconTintList(null)

        refreshServerOptions(serverInput, serverLayout)

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

        serverInput.setOnClickListener {
            serverInput.showDropDownWhenSafe()
        }
        serverInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                serverInput.showDropDownWhenSafe()
            }
        }
        serverInput.setOnItemClickListener { _, _, position, _ ->
            val selected = serverAdapter.getItem(position) ?: return@setOnItemClickListener
            if (selected.isAction) {
                when (selected.actionType) {
                    ServerAction.CONFIGURE -> {
                        val previousConfig = loadServerConfiguration()
                        serverInput.setText(previousConfig.displayName, false)
                        serverInput.tag = previousConfig.baseUrl
                        updateServerFlag(serverLayout, previousConfig.countryCode)
                        updateServerStatusIcon(previousConfig.baseUrl)
                        serverInput.dismissDropDown()
                        showServerConfigurationDialog(serverInput, serverLayout)
                    }
                    ServerAction.CLEAR -> {
                        clearCustomServers()
                        refreshServerOptions(serverInput, serverLayout)
                        serverInput.dismissDropDown()
                    }
                    null -> {
                        serverInput.dismissDropDown()
                    }
                }
            } else {
                serverInput.setText(selected.displayName, false)
                serverInput.tag = selected.baseUrl
                updateServerFlag(serverLayout, selected.countryCode)
                saveServerConfiguration(selected.baseUrl, selected.countryCode, selected.displayName)
                updateServerStatusIcon(selected.baseUrl)
                serverInput.dismissDropDown()
            }
        }
        serverInput.keyListener = null

        loginButtonView.setOnClickListener {
            usernameLayout.error = null
            passwordLayout.error = null
            serverLayout.error = null
            loginErrorTextView.isVisible = false

            val username = loginUsernameInput.text?.toString()?.trim().orEmpty()
            val password = loginPasswordInput.text?.toString().orEmpty()
            val serverBaseUrl = (serverInput.tag as? String).orEmpty().trim()

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
                serverLayout.error = getString(R.string.login_server_error)
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
                        serverBaseUrl
                    )
                }
            }
        }

        maybeRestoreSessionOrAutoLogin()
    }

    private fun ensureSurveyTranslationConsent(onConsentGranted: () -> Unit) {
        if (isSurveyTranslationConsentAccepted()) {
            onConsentGranted()
            return
        }

        if (!serverPreferences.contains(KEY_SURVEY_TRANSLATIONS_ENABLED)) {
            setSurveyTranslationEnabled(DEFAULT_SURVEY_TRANSLATION_ENABLED)
        }

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_survey_translation_consent, null, false)
        val consentCheckBox = dialogView.findViewById<MaterialCheckBox>(R.id.surveyTranslationConsentCheckBox)
        val policyLink = dialogView.findViewById<TextView>(R.id.surveyTranslationPolicyLink)

        consentCheckBox.isChecked = isSurveyTranslationEnabled()

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.login_survey_translation_consent_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dashboard_survey_translation_consent_accept) { alertDialog, _ ->
                val translationsEnabled = consentCheckBox.isChecked
                setSurveyTranslationEnabled(translationsEnabled)
                setSurveyTranslationConsentAccepted(true)
                alertDialog.dismiss()
                onConsentGranted()
            }
            .setNegativeButton(R.string.dashboard_survey_translation_consent_cancel) { alertDialog, _ ->
                setSurveyTranslationEnabled(false)
                setSurveyTranslationConsentAccepted(false)
                alertDialog.dismiss()
            }
            .create()

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

    private fun applyRememberedCredentials(remembered: RememberedCredentials? = loadRememberedCredentials()) {
        rememberedLoginCredentials = remembered
        suppressRememberListener = true
        if (::rememberMeCheckBox.isInitialized) {
            rememberMeCheckBox.isChecked = remembered != null
        }
        suppressRememberListener = false
        if (remembered != null) {
            loginUsernameInput.setText(remembered.username)
            loginPasswordInput.setText(remembered.password)
        } else {
            loginUsernameInput.text?.clear()
            loginPasswordInput.text?.clear()
        }
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

    private fun maybeRestoreSessionOrAutoLogin() {
        if (!autoLoginEnabled) {
            return
        }
        if (!::loginButtonView.isInitialized || !::loginProgressView.isInitialized || !::loginErrorTextView.isInitialized) {
            return
        }
        if (!isServerReachable || isLoginInProgress || sessionRestoreInProgress) {
            return
        }
        val baseUrl = currentServerBaseUrl.takeIf { it.isNotBlank() } ?: return
        if (!sessionRestoreAttempted) {
            attemptStoredSessionRestore(baseUrl)
        } else {
            maybeAutoLogin()
        }
    }

    private fun attemptStoredSessionRestore(baseUrl: String) {
        if (isLoginInProgress || sessionRestoreInProgress) {
            return
        }
        sessionRestoreAttempted = true
        sessionRestoreInProgress = true
        lifecycleScope.launch {
            try {
                val authService = AuthDependencies.provideAuthService(this@MyPlanetLite, baseUrl)
                val storedToken = authService.getStoredToken()
                if (storedToken.isNullOrBlank()) {
                    maybeAutoLogin()
                    return@launch
                }
                val cachedUsername = withContext(Dispatchers.IO) {
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
            } finally {
                sessionRestoreInProgress = false
            }
        }
    }

    private fun maybeAutoLogin() {
        if (!shouldAutoLoginOnLaunch || credentialsAutoLoginAttempted) {
            return
        }
        if (!::rememberMeCheckBox.isInitialized || !rememberMeCheckBox.isChecked) {
            return
        }
        if (!::loginProgressView.isInitialized || !::loginErrorTextView.isInitialized) {
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
                baseUrl
            )
        }
    }

    private fun showServerConfigurationDialog(
        serverInput: MaterialAutoCompleteTextView,
        serverLayout: TextInputLayout
    ) {
        serverLayout.error = null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_configuration, null)
        val serverUrlLayout: TextInputLayout = dialogView.findViewById(R.id.serverUrlInputLayout)
        val serverUrlInput: MaterialAutoCompleteTextView = dialogView.findViewById(R.id.serverUrlInput)
        val serverNameLayout: TextInputLayout = dialogView.findViewById(R.id.serverNameInputLayout)
        val serverNameInput: TextInputEditText = dialogView.findViewById(R.id.serverNameInput)
        val countryLayout: TextInputLayout = dialogView.findViewById(R.id.countryInputLayout)
        val countryInput: MaterialAutoCompleteTextView = dialogView.findViewById(R.id.countryInput)

        val excludedCountryCodes = setOf("CN", "HK", "TW", "IL", "PS")
        val countryList = World.getAllCountries()
            .filterNot { excludedCountryCodes.contains(it.alpha2.uppercase(Locale.ROOT)) }
            .sortedBy { it.name }
        val countryNames = countryList.map { it.name }
        val currentConfig = loadServerConfiguration()
        val serverSuggestionsAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_list_item_1,
            buildServerSuggestions(currentConfig)
        )
        serverUrlInput.setAdapter(serverSuggestionsAdapter)
        countryInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, countryNames))
        serverUrlInput.setOnClickListener { serverUrlInput.showDropDownWhenSafe() }
        serverUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                serverUrlInput.showDropDownWhenSafe()
            }
        }
        countryInput.setOnClickListener { countryInput.showDropDownWhenSafe() }
        countryInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                countryInput.showDropDownWhenSafe()
            }
        }

        serverUrlInput.setText(currentConfig.baseUrl, false)
        serverNameInput.setText(currentConfig.displayName)
        val selectedCountryIndex = countryList.indexOfFirst { it.alpha2.equals(currentConfig.countryCode, ignoreCase = true) }
        if (selectedCountryIndex >= 0) {
            countryInput.setText(countryList[selectedCountryIndex].name, false)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.server_configuration_title)
            .setView(dialogView)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.server_configuration_save, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                serverUrlLayout.error = null
                serverNameLayout.error = null
                countryLayout.error = null

                val url = serverUrlInput.text?.toString()?.trim().orEmpty()
                val normalizedUrl = normalizeServerUrl(url)
                val serverName = serverNameInput.text?.toString()?.trim().orEmpty()
                val countryName = countryInput.text?.toString()?.trim().orEmpty()
                val selectedCountry = countryList.firstOrNull { it.name.equals(countryName, ignoreCase = true) }

                if (normalizedUrl.isEmpty()) {
                    serverUrlLayout.error = getString(R.string.server_configuration_url_error)
                    return@setOnClickListener
                }
                if (serverName.isEmpty()) {
                    serverNameLayout.error = getString(R.string.server_configuration_name_error)
                    return@setOnClickListener
                }
                val nonNullCountry = selectedCountry ?: run {
                    countryLayout.error = getString(R.string.server_configuration_country_error)
                    return@setOnClickListener
                }

                val added = addCustomServer(serverName, normalizedUrl, nonNullCountry.alpha2)
                if (added) {
                    saveServerConfiguration(normalizedUrl, nonNullCountry.alpha2, serverName)
                    Toast.makeText(this, R.string.server_configuration_added, Toast.LENGTH_SHORT).show()
                    refreshServerOptions(serverInput, serverLayout)
                    val updatedConfig = loadServerConfiguration()
                    serverSuggestionsAdapter.clear()
                    serverSuggestionsAdapter.addAll(buildServerSuggestions(updatedConfig))
                    serverSuggestionsAdapter.notifyDataSetChanged()
                    dialog.dismiss()
                } else {
                    serverUrlLayout.error = getString(R.string.server_configuration_duplicate_error)
                }
            }
        }

        dialog.show()
    }

    private fun loadServerConfiguration(): ServerConfiguration {
        val builtInServers = builtInServerOptions()
        val customServers = loadCustomServers().map { it.toServerOption() }
        val storedUrl = serverPreferences.getString(KEY_SERVER_URL, builtInServers.firstOrNull()?.baseUrl).orEmpty().trim()
        val storedCountry = serverPreferences.getString(KEY_COUNTRY_CODE, DEFAULT_COUNTRY_CODE).orEmpty().uppercase(Locale.ROOT)
        val storedDisplayName = serverPreferences.getString(KEY_SERVER_DISPLAY_NAME, null)
        val baseUrl = if (storedUrl.isNotEmpty()) storedUrl else builtInServers.firstOrNull()?.baseUrl.orEmpty()
        val matchedServer = (builtInServers + customServers).firstOrNull {
            baseUrlKey(it.baseUrl) == baseUrlKey(baseUrl)
        }
        val countryCode = when {
            matchedServer != null -> matchedServer.countryCode
            storedCountry.isNotEmpty() -> storedCountry
            else -> DEFAULT_COUNTRY_CODE
        }
        val displayName = when {
            matchedServer != null -> matchedServer.displayName
            !storedDisplayName.isNullOrBlank() -> storedDisplayName
            baseUrl.isNotEmpty() -> baseUrl
            else -> builtInServers.firstOrNull()?.displayName ?: ""
        }
        return ServerConfiguration(
            baseUrl = baseUrl,
            countryCode = countryCode,
            displayName = displayName
        )
    }

    private fun saveServerConfiguration(url: String, countryCode: String, displayName: String) {
        val sanitizedUrl = normalizeServerUrl(url)
        val resolvedDisplayName = displayName.ifBlank { sanitizedUrl }
        serverPreferences.edit()
            .putString(KEY_SERVER_URL, sanitizedUrl)
            .putString(KEY_COUNTRY_CODE, countryCode.uppercase(Locale.ROOT))
            .putString(KEY_SERVER_DISPLAY_NAME, resolvedDisplayName)
            .apply()
    }

    private fun updateServerFlag(serverLayout: TextInputLayout, countryCode: String) {
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

    private fun refreshServerOptions(
        serverInput: MaterialAutoCompleteTextView,
        serverLayout: TextInputLayout
    ) {
        val currentConfig = loadServerConfiguration()
        val options = createServerOptions(currentConfig)
        serverAdapter.submitList(options)

        val selectedOption = options.firstOrNull {
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

    private fun createServerOptions(currentConfig: ServerConfiguration): List<ServerOption> {
        val builtIns = builtInServerOptions()
        val customs = loadCustomServers().map { it.toServerOption() }
        val connectedKey = baseUrlKey(currentConfig.baseUrl)
        val items = (customs + builtIns)
            .distinctBy { baseUrlKey(it.baseUrl) }
            .toMutableList()
        if (currentConfig.baseUrl.isNotEmpty() && items.none { baseUrlKey(it.baseUrl) == connectedKey }) {
            items.add(0, ServerOption(currentConfig.displayName, currentConfig.baseUrl, currentConfig.countryCode))
        }
        items.add(ServerOption(getString(R.string.server_option_clear), "", currentConfig.countryCode, actionType = ServerAction.CLEAR))
        items.add(ServerOption(getString(R.string.server_option_configure), "", currentConfig.countryCode, actionType = ServerAction.CONFIGURE))
        return items
    }

    private fun builtInServerOptions(): List<ServerOption> = BUILT_IN_SERVERS.map {
        ServerOption(getString(it.nameRes), it.baseUrl, it.countryCode)
    }

    private fun buildServerSuggestions(currentConfig: ServerConfiguration): MutableList<String> {
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

    private fun addCustomServer(displayName: String, baseUrl: String, countryCode: String): Boolean {
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

    private fun loadCustomServers(): MutableList<CustomServer> {
        val raw = serverPreferences.getString(KEY_CUSTOM_SERVERS, null) ?: return mutableListOf()
        return try {
            val array = JSONArray(raw)
            val result = mutableListOf<CustomServer>()
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                val baseUrl = obj.optString("baseUrl", "").trim()
                val countryCode = obj.optString("countryCode", "").uppercase(Locale.ROOT)
                if (baseUrl.isBlank() || countryCode.isBlank()) continue
                val displayName = obj.optString("displayName", baseUrl)
                result.add(CustomServer(displayName, baseUrl, countryCode))
            }
            result
        } catch (error: JSONException) {
            mutableListOf()
        }
    }

    private fun clearCustomServers() {
        val builtIns = builtInServerOptions()
        val fallback = builtIns.firstOrNull()
        if (fallback != null) {
            saveServerConfiguration(fallback.baseUrl, fallback.countryCode, fallback.displayName)
        }
        serverPreferences.edit()
            .remove(KEY_CUSTOM_SERVERS)
            .apply()
    }

    private fun persistCustomServers(servers: List<CustomServer>) {
        val array = JSONArray()
        servers.forEach { server ->
            val obj = JSONObject()
            obj.put("displayName", server.displayName)
            obj.put("baseUrl", server.baseUrl)
            obj.put("countryCode", server.countryCode)
            array.put(obj)
        }
        serverPreferences.edit()
            .putString(KEY_CUSTOM_SERVERS, array.toString())
            .apply()
    }

    private fun baseUrlKey(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.trimEnd('/').lowercase(Locale.ROOT)
    }

    private fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""
        val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
        val normalized = withScheme.toHttpUrlOrNull() ?: return ""
        return normalized.newBuilder().build().toString().trimEnd('/')
    }

    private suspend fun handleLoginResult(
        result: AuthResult,
        errorText: TextView,
        loginButton: Button,
        progress: ProgressBar,
        username: String,
        password: String,
        rememberCheckBox: MaterialCheckBox,
        serverBaseUrl: String
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
                val fetched = userProfileSync.refreshProfile(
                    serverBaseUrl = serverBaseUrl,
                    username = profileUsername,
                    sessionCookie = result.response.sessionCookie
                )
                launchDashboard()
            }
            is AuthResult.Error -> {
                val errorMessage = when (result.code) {
                    401, 403 -> getString(R.string.login_invalid_credentials)
                    else -> result.message.takeIf { it.isNotBlank() } ?: getString(R.string.login_generic_error)
                }
                errorText.text = errorMessage
                errorText.isVisible = true
            }
        }
    }

    private fun recordLoginActivity(serverBaseUrl: String, username: String, sessionCookie: String?) {
        val sanitizedBaseUrl = serverBaseUrl.trim()
        if (sanitizedBaseUrl.isEmpty()) {
            return
        }
        val requestUrl = buildLoginActivityUrl(sanitizedBaseUrl) ?: return
        val payload = buildLoginActivityPayload(username) ?: return

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val body = payload.toString().toRequestBody(mediaType)
                    val requestBuilder = Request.Builder()
                        .url(requestUrl)
                        .post(body)
                    sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                        requestBuilder.addHeader("Cookie", cookie)
                    }
                    connectivityClient.newCall(requestBuilder.build()).execute().use { _ ->
                        // Intentionally ignoring response
                    }
                }
            }
        }
    }

    private fun buildLoginActivityUrl(baseUrl: String): String? {
        return baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("db/login_activities")
            ?.build()
            ?.toString()
    }

    private fun buildLoginActivityPayload(username: String): JSONObject? {
        val parentCode = serverPreferences.getString(KEY_SERVER_PARENT_CODE, null)
        val code = serverPreferences.getString(KEY_SERVER_CODE, null)
        val androidId = serverPreferences.getString(KEY_DEVICE_ANDROID_ID, null)
        val customDeviceName = serverPreferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)

        val deviceName = resolveDeviceName()
        val loginTimeMillis = System.currentTimeMillis()
        val loginTimeString = loginTimeMillis.toString()

        return runCatching {
            JSONObject().apply {
                put("user", username)
                put("type", "login")
                put("loginTime", loginTimeString)
                put("logoutTime", 0)
                put("createdOn", code?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("parentCode", parentCode?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("androidId", androidId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("deviceName", deviceName)
                put("customDeviceName", customDeviceName?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            }
        }.getOrNull()
    }

    private fun resolveDeviceName(): String {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        return when {
            manufacturer.isEmpty() && model.isEmpty() -> {
                val device = Build.DEVICE?.trim().orEmpty()
                if (device.isNotEmpty()) device else DEFAULT_DEVICE_NAME
            }
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    private fun setLoadingState(isLoading: Boolean, loginButton: Button, progress: ProgressBar) {
        isLoginInProgress = isLoading
        if (isLoading) {
            loginButton.isEnabled = false
        }
        updateLoginButtonAvailability()
        progress.isVisible = isLoading
    }

    private fun saveRememberedCredentials(username: String, password: String) {
        serverPreferences.edit()
            .putBoolean(KEY_REMEMBER_CREDENTIALS, true)
            .apply()
        securePreferences.edit()
            .putString(KEY_REMEMBERED_USERNAME, username)
            .putString(KEY_REMEMBERED_PASSWORD, password)
            .apply()
    }

    private fun clearRememberedCredentials() {
        serverPreferences.edit()
            .putBoolean(KEY_REMEMBER_CREDENTIALS, false)
            .remove(KEY_REMEMBERED_USERNAME)
            .remove(KEY_REMEMBERED_PASSWORD)
            .apply()
        securePreferences.edit()
            .remove(KEY_REMEMBERED_USERNAME)
            .remove(KEY_REMEMBERED_PASSWORD)
            .apply()
    }

    private fun clearStoredSessionIfNotRemembered() {
        if (serverPreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)) {
            return
        }
        val baseUrl = loadServerConfiguration().baseUrl.trim()
        if (baseUrl.isEmpty()) {
            return
        }
        lifecycleScope.launch {
            val authService = AuthDependencies.provideAuthService(this@MyPlanetLite, baseUrl)
            authService.logout()
        }
    }

    private fun migrateCredentialsIfNecessary() {
        val username = serverPreferences.getString(KEY_REMEMBERED_USERNAME, null)
        val password = serverPreferences.getString(KEY_REMEMBERED_PASSWORD, null)

        if (!username.isNullOrEmpty() || !password.isNullOrEmpty()) {
            securePreferences.edit()
                .putString(KEY_REMEMBERED_USERNAME, username)
                .putString(KEY_REMEMBERED_PASSWORD, password)
                .apply()

            serverPreferences.edit()
                .remove(KEY_REMEMBERED_USERNAME)
                .remove(KEY_REMEMBERED_PASSWORD)
                .apply()
        }
    }

    private fun loadRememberedCredentials(): RememberedCredentials? {
        if (!serverPreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)) {
            return null
        }
        val username = securePreferences.getString(KEY_REMEMBERED_USERNAME, null)
        val password = securePreferences.getString(KEY_REMEMBERED_PASSWORD, null)
        if (username.isNullOrEmpty() && password.isNullOrEmpty()) {
            return null
        }
        return RememberedCredentials(username.orEmpty(), password.orEmpty())
    }

    private fun isSurveyTranslationEnabled(): Boolean {
        return serverPreferences.getBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, DEFAULT_SURVEY_TRANSLATION_ENABLED)
    }

    private fun setSurveyTranslationEnabled(enabled: Boolean) {
        serverPreferences.edit()
            .putBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, enabled)
            .apply()
    }

    private fun isSurveyTranslationConsentAccepted(): Boolean {
        return serverPreferences.getBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
    }

    private fun setSurveyTranslationConsentAccepted(accepted: Boolean) {
        serverPreferences.edit()
            .putBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, accepted)
            .apply()
    }

    private fun extractDeepLinkPostId(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        val data = intent.data ?: return null
        val queryPostId = data.getQueryParameter("postId")
        if (!queryPostId.isNullOrBlank()) {
            return queryPostId
        }
        val segments = data.pathSegments
        if (segments.isEmpty()) {
            return null
        }
        val postIndex = segments.indexOfFirst { segment ->
            segment.equals("post", ignoreCase = true)
        }
        val candidate = when {
            postIndex >= 0 && postIndex + 1 < segments.size -> segments[postIndex + 1]
            else -> segments.last()
        }
        return candidate.takeIf { it.isNotBlank() }
    }

    override fun onDestroy() {
        serverStatusJob?.cancel()
        super.onDestroy()
    }

    private companion object {
        private const val MIN_PASSWORD_LENGTH = 4
        private const val PREFS_NAME = "server_preferences"
        private const val SECURE_PREFS_NAME = "secure_server_preferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        private const val KEY_SERVER_CODE = "server_code"
        private const val KEY_COUNTRY_CODE = "country_code"
        private const val KEY_SERVER_DISPLAY_NAME = "server_display_name"
        private const val KEY_CUSTOM_SERVERS = "custom_servers"
        private const val KEY_REMEMBER_CREDENTIALS = "remember_credentials"
        private const val KEY_REMEMBERED_USERNAME = "remembered_username"
        private const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        private const val KEY_SURVEY_TRANSLATIONS_ENABLED = "survey_translations_enabled"
        private const val KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED = "survey_translation_consent_accepted"
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        private const val EXTRA_ALLOW_AUTO_LOGIN = "extra_allow_auto_login"
        private const val DEFAULT_COUNTRY_CODE = "GT"
        private const val DEFAULT_SURVEY_TRANSLATION_ENABLED = true
        private const val DEFAULT_DEVICE_NAME = "Android Device"
        private const val LOGO_SHRUNK_DP = 50f
        private const val APP_VERSION_SHRUNK_BOTTOM_MARGIN_DP = 5f
        private const val LOGIN_SCROLL_SHRUNK_PADDING_TOP_DP = 5f
        private const val LANGUAGE_TRANSITION_DURATION_MS = 250L
        private const val LOGIN_TIME_LENGTH = 13
        private val BUILT_IN_SERVERS = listOf(
            BuiltInServer(R.string.server_planet_xela, "http://10.82.1.30/", DEFAULT_COUNTRY_CODE),
            BuiltInServer(R.string.server_planet_guatemala, "https://planet.gt/", DEFAULT_COUNTRY_CODE),
            BuiltInServer(R.string.server_planet_san_pablo, "https://sanpablo.planet.gt/", DEFAULT_COUNTRY_CODE),
            BuiltInServer(R.string.server_planet_somalia, "https://planet.somalia.ole.org", "SO"),
            BuiltInServer(R.string.server_planet_learning, "https://planet.learning.ole.org/", "US"),
            BuiltInServer(R.string.server_planet_earth, "https://planet.earth.ole.org/", "US"),
            BuiltInServer(R.string.server_planet_vi, "https://planet.vi.ole.org/", "US"),
            BuiltInServer(R.string.server_planet_uriur, "https://planet.uriur.ole.org/", "KE")
        )
        private val SUPPORTED_LANGUAGES = listOf(
            LanguageOption("en", R.string.language_name_english),
            LanguageOption("es", R.string.language_name_spanish),
            LanguageOption("fr", R.string.language_name_french),
            LanguageOption("pt", R.string.language_name_portuguese),
            LanguageOption("ne", R.string.language_name_nepali),
            LanguageOption("ar", R.string.language_name_arabic),
            LanguageOption("so", R.string.language_name_somali),
            LanguageOption("hi", R.string.language_name_hindi)
        )
    }

    private data class ServerOption(
        val displayName: String,
        val baseUrl: String,
        val countryCode: String,
        val actionType: ServerAction? = null
    ) {
        val isAction: Boolean
            get() = actionType != null

        override fun toString(): String = displayName
    }

    private enum class ServerAction {
        CONFIGURE,
        CLEAR
    }

    private data class ServerConfiguration(val baseUrl: String, val countryCode: String, val displayName: String)

    private data class ServerConnectivityResult(
        val reachable: Boolean,
        val parentCode: String? = null,
        val code: String? = null
    )

    private data class BuiltInServer(val nameRes: Int, val baseUrl: String, val countryCode: String)

    private data class CustomServer(val displayName: String, val baseUrl: String, val countryCode: String) {
        fun toServerOption(): ServerOption = ServerOption(displayName, baseUrl, countryCode)
    }

    private data class RememberedCredentials(val username: String, val password: String)

    private data class LanguageOption(val languageTag: String, val labelRes: Int)

    private inner class ServerOptionAdapter(context: Context) : ArrayAdapter<ServerOption>(context, 0, mutableListOf()) {

        private val allItems = mutableListOf<ServerOption>()
        private val visibleItems = mutableListOf<ServerOption>()

        fun submitList(items: List<ServerOption>) {
            allItems.clear()
            allItems.addAll(items)
            visibleItems.clear()
            visibleItems.addAll(items)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = visibleItems.size

        override fun getItem(position: Int): ServerOption? = visibleItems.getOrNull(position)

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent, isDropdown = false)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent, isDropdown = true)
        }

        override fun getFilter(): Filter = object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                return FilterResults().apply {
                    values = ArrayList(allItems)
                    count = allItems.size
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
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

            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? ServerOption)?.displayName
                    ?: super.convertResultToString(resultValue)
            }
        }

        private fun createView(position: Int, convertView: View?, parent: ViewGroup, isDropdown: Boolean): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_server_option, parent, false)
            val option = getItem(position) ?: return view

            val flagView: ImageView = view.findViewById(R.id.serverOptionFlag)
            val nameView: TextView = view.findViewById(R.id.serverOptionName)

            nameView.text = option.displayName

            val desiredMargin = if (isDropdown) {
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
    }

    private fun updateServerStatusIcon(baseUrl: String?) {
        if (!::serverStatusIconView.isInitialized) {
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

    private fun checkServerConnectivity(baseUrl: String) {
        if (!::serverStatusIconView.isInitialized) {
            return
        }
        serverStatusIconView.isVisible = true
        serverStatusIconView.setOnClickListener(null)
        serverStatusJob = lifecycleScope.launch {
            showServerStatusChecking()
            val result = withContext(Dispatchers.IO) { fetchServerConnectivity(baseUrl) }
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

    private fun fetchServerConnectivity(baseUrl: String): ServerConnectivityResult {
        val requestUrl = buildConfigurationRequestUrl(baseUrl) ?: return ServerConnectivityResult(false)
        return runCatching {
            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .build()
            connectivityClient.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    return@use ServerConnectivityResult(false)
                }
                val body = response.body.string()
                if (body.isBlank()) {
                    return@use ServerConnectivityResult(true)
                }
                val metadata = extractServerMetadata(body)
                ServerConnectivityResult(true, metadata?.first, metadata?.second)
            }
        }.getOrDefault(ServerConnectivityResult(false))
    }

    private fun buildConfigurationRequestUrl(baseUrl: String): String? {
        return baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("db/configurations/_all_docs")
            ?.addQueryParameter("include_docs", "true")
            ?.build()
            ?.toString()
    }

    private fun extractServerMetadata(payload: String): Pair<String?, String?>? {
        return runCatching {
            val response = serverMetadataAdapter.fromJson(payload)
            val rows = response?.rows ?: return@runCatching null
            for (row in rows) {
                val doc = row.doc ?: continue
                val parentCode = doc.parentCode?.takeIf { it.isNotBlank() }
                val code = doc.code?.takeIf { it.isNotBlank() }
                if (parentCode != null || code != null) {
                    return@runCatching Pair(parentCode, code)
                }
            }
            null
        }.getOrNull()
    }

    private fun persistServerMetadata(baseUrl: String, parentCode: String?, code: String?) {
        serverPreferences.edit().apply {
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

    private fun showServerStatusChecking() {
        serverStatusIconView.setImageResource(R.drawable.ic_server_disconnected)
        serverStatusIconView.alpha = 0.5f
        serverStatusIconView.isEnabled = false
        serverStatusIconView.isClickable = false
        serverStatusIconView.contentDescription = getString(R.string.server_status_checking)
        serverStatusIconView.isVisible = true
        isServerReachable = false
        updateLoginButtonAvailability()
    }

    private fun showServerConnectedState() {
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

    private fun showServerDisconnectedState(allowRetry: Boolean) {
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
        val descriptionRes = if (canRetry) {
            R.string.server_status_disconnected_retry
        } else {
            R.string.server_status_disconnected
        }
        serverStatusIconView.contentDescription = getString(descriptionRes)
        serverStatusIconView.isVisible = true
        isServerReachable = false
        updateLoginButtonAvailability()
    }

    private fun updateLoginButtonAvailability() {
        if (!::loginButtonView.isInitialized) {
            return
        }
        val canAuthenticate = isServerReachable && !isLoginInProgress
        loginButtonView.isEnabled = canAuthenticate
        if (::signupButtonView.isInitialized) {
            signupButtonView.isEnabled = canAuthenticate
            signupButtonView.alpha = if (canAuthenticate) 1f else 0.5f
        }
    }

    private fun MaterialAutoCompleteTextView.showDropDownWhenSafe() {
        if (this@MyPlanetLite.isFinishing || this@MyPlanetLite.isDestroyed) {
            return
        }
        if (isAttachedToWindow && windowToken != null && hasWindowFocus()) {
            showDropDown()
        } else {
            post {
                if (
                    !this@MyPlanetLite.isFinishing &&
                    !this@MyPlanetLite.isDestroyed &&
                    isAttachedToWindow &&
                    windowToken != null &&
                    hasWindowFocus()
                ) {
                    showDropDown()
                }
            }
        }
    }

    private fun shrinkLogo(logo: ImageView, appVersion: TextView) {
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

    private fun restoreLogo(logo: ImageView, appVersion: TextView) {
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

    private fun shrinkLoginScrollPadding(loginScroll: ScrollView) {
        if (isLoginScrollPaddingShrunk || originalLoginScrollPaddingTop == 0 ||
            shrunkLoginScrollPaddingTopPx == 0
        ) {
            return
        }
        loginScroll.setPadding(
            loginScroll.paddingLeft,
            shrunkLoginScrollPaddingTopPx,
            loginScroll.paddingRight,
            loginScroll.paddingBottom
        )
        isLoginScrollPaddingShrunk = true
    }

    private fun restoreLoginScrollPadding(loginScroll: ScrollView) {
        if (!isLoginScrollPaddingShrunk || originalLoginScrollPaddingTop == 0) {
            return
        }
        loginScroll.setPadding(
            loginScroll.paddingLeft,
            originalLoginScrollPaddingTop,
            loginScroll.paddingRight,
            loginScroll.paddingBottom
        )
        isLoginScrollPaddingShrunk = false
    }
}

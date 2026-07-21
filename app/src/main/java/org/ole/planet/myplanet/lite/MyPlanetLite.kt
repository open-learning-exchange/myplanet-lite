@file:Suppress("DEPRECATION")
/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Filter
import android.widget.ImageView
import android.widget.AbsListView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.profile.UserProfileSync
import org.ole.planet.myplanet.lite.util.IntentUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import java.util.ArrayList
import java.util.Locale
import kotlin.math.roundToInt

class MyPlanetLite : BaseActivity() {
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
        SecurePreferencesProvider.getServerPreferences(applicationContext)
    }

    private val securePreferences: SharedPreferences by lazy {
        SecurePreferencesProvider.getEncryptedPreferences(applicationContext, SECURE_PREFS_NAME)
    }
    private val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    private val customServerAdapter: JsonAdapter<List<CustomServer>> by lazy {
        val type = Types.newParameterizedType(List::class.java, CustomServer::class.java)
        moshi.adapter(type)
    }
    private val serverConnectivityRepository: ServerConnectivityRepository by lazy {
        ServerConnectivityRepository(connectivityClient, moshi)
    }

    private val signupLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data =
                    result.data ?: run {
                        applyRememberedCredentials()
                        return@registerForActivityResult
                    }
                val autoLogin = data.getBooleanExtra(SignupActivity.EXTRA_AUTO_LOGIN, false)
                if (autoLogin) {
                    val username = data.getStringExtra(SignupActivity.EXTRA_USERNAME).orEmpty()
                    val password =
                        org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
                            .consumeTemporarySignUpPassword(this)
                            .orEmpty()
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
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge(
            statusBarStyle =
                SystemBarStyle.light(
                    ContextCompat.getColor(this, R.color.white),
                    ContextCompat.getColor(this, R.color.white),
                ),
        )
        setContentView(R.layout.activity_main)

        initializeState(savedInstanceState)

        val logoImageView: ImageView = findViewById(R.id.logoImageView)
        val appVersionTextView: TextView = findViewById(R.id.appVersionTextView)
        val loginScroll: ScrollView = findViewById(R.id.loginScroll)

        setupViews(logoImageView, appVersionTextView, loginScroll)
        setupWindowInsets(logoImageView, appVersionTextView, loginScroll)

        World.init(applicationContext)

        configureLogin()
    }

    private fun initializeState(savedInstanceState: Bundle?) {
        ProfileCredentialsStore.setSessionCredentials(null)
        clearStoredSessionIfNotRemembered()
        autoLoginEnabled = intent?.getBooleanExtra(EXTRA_ALLOW_AUTO_LOGIN, false) == true
        deepLinkPostId = intent
            ?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: IntentUtils.extractDeepLinkPostId(intent)

        if (savedInstanceState != null) {
            val contentRoot: View? = findViewById(android.R.id.content)
            contentRoot?.let { root ->
                root.alpha = 0f
                root.doOnPreDraw {
                    root
                        .animate()
                        .alpha(1f)
                        .setDuration(250L)
                        .setInterpolator(FastOutSlowInInterpolator())
                        .start()
                }
            }
        }
    }

    private fun setupViews(
        logoImageView: ImageView,
        appVersionTextView: TextView,
        loginScroll: ScrollView,
    ) {
        val languageSelectorIcon: ImageView = findViewById(R.id.languageSelectorIcon)
        serverInputLayoutView = findViewById(R.id.serverInputLayout)
        serverAutoCompleteView = findViewById(R.id.serverUrlInput)
        loginUsernameInput = findViewById(R.id.usernameInput)
        loginPasswordInput = findViewById(R.id.passwordInput)
        serverStatusIconView = findViewById(R.id.serverStatusIcon)
        val poweredByTextView: TextView = findViewById(R.id.poweredByText)
        signupPromptView = findViewById(R.id.signupPrompt)
        signupButtonView = findViewById(R.id.signupButton)
        privacyPolicyPromptView = findViewById(R.id.privacyPolicyPrompt)

        appVersionTextView.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)

        poweredByTextView.text =
            HtmlCompat.fromHtml(
                getString(R.string.powered_by_plataformas_informaticas),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
            )
        poweredByTextView.movementMethod = LinkMovementMethod.getInstance()

        signupPromptView.text = getString(R.string.login_signup_prompt)
        signupButtonView.setOnClickListener {
            if (!signupButtonView.isEnabled || isLoginInProgress || !isServerReachable) {
                return@setOnClickListener
            }
            val serverBaseUrl = (serverAutoCompleteView.tag as? String).orEmpty()
            val intent =
                Intent(this, SignupActivity::class.java).apply {
                    putExtra(SignupActivity.EXTRA_SERVER_BASE_URL, serverBaseUrl)
                }
            signupLauncher.launch(intent)
        }

        privacyPolicyPromptView.text =
            HtmlCompat.fromHtml(
                getString(R.string.login_privacy_policy_prompt),
                HtmlCompat.FROM_HTML_MODE_LEGACY,
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

        languageSelectorIcon.setOnClickListener {
            LanguagePreferences.showLanguageSelectionDialog(this)
        }
    }

    private fun setupWindowInsets(
        logoImageView: ImageView,
        appVersionTextView: TextView,
        loginScroll: ScrollView,
    ) {
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

    private fun configureLogin() {
        initializeLoginViews()
        setupRememberMeCheckbox()
        setupServerDropdown()
        setupLoginButton()
        maybeRestoreSessionOrAutoLogin()
    }

    private fun initializeLoginViews() {
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

    private fun setupRememberMeCheckbox() {
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

    private fun setupServerDropdown() {
        serverAutoCompleteView.setOnClickListener {
            serverAutoCompleteView.showDropDownWhenSafe()
        }
        serverAutoCompleteView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                serverAutoCompleteView.showDropDownWhenSafe()
            }
        }
        serverAutoCompleteView.setOnItemClickListener { _, _, position, _ ->
            val selected = serverAdapter.getItem(position) ?: return@setOnItemClickListener
            if (selected.isAction) {
                when (selected.actionType) {
                    ServerAction.CONFIGURE -> {
                        val previousConfig = loadServerConfiguration()
                        serverAutoCompleteView.setText(previousConfig.displayName, false)
                        serverAutoCompleteView.tag = previousConfig.baseUrl
                        updateServerFlag(serverInputLayoutView, previousConfig.countryCode)
                        updateServerStatusIcon(previousConfig.baseUrl)
                        serverAutoCompleteView.dismissDropDown()
                        showServerConfigurationDialog(serverAutoCompleteView, serverInputLayoutView)
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

    private fun setupLoginButton() {
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

    private fun ensureSurveyTranslationConsent(onConsentGranted: () -> Unit) {
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

    private fun applyRememberedCredentials(remembered: RememberedCredentials? = loadRememberedCredentials()) {
        rememberedLoginCredentials = remembered
        suppressRememberListener = true
        if (::rememberMeCheckBox.isInitialized) {
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
                baseUrl,
            )
        }
    }

    private data class ServerDialogViews(
        val serverUrlLayout: TextInputLayout,
        val serverUrlInput: MaterialAutoCompleteTextView,
        val serverNameLayout: TextInputLayout,
        val serverNameInput: TextInputEditText,
        val countryLayout: TextInputLayout,
        val countryInput: MaterialAutoCompleteTextView,
    )

    private fun showServerConfigurationDialog(
        serverInput: MaterialAutoCompleteTextView,
        serverLayout: TextInputLayout,
    ) {
        serverLayout.error = null
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_server_configuration, null)
        val views = setupServerConfigurationViews(dialogView)

        val countryList = getFilteredCountries()
        val currentConfig = loadServerConfiguration()
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

    private fun setupServerConfigurationViews(dialogView: View): ServerDialogViews =
        ServerDialogViews(
            serverUrlLayout = dialogView.findViewById(R.id.serverUrlInputLayout),
            serverUrlInput = dialogView.findViewById(R.id.serverUrlInput),
            serverNameLayout = dialogView.findViewById(R.id.serverNameInputLayout),
            serverNameInput = dialogView.findViewById(R.id.serverNameInput),
            countryLayout = dialogView.findViewById(R.id.countryInputLayout),
            countryInput = dialogView.findViewById(R.id.countryInput),
        )

    private fun getFilteredCountries(): List<com.blongho.country_data.Country> {
        val excludedCountryCodes = setOf("CN", "HK", "TW", "IL", "PS")
        return World
            .getAllCountries()
            .filterNot { excludedCountryCodes.contains(it.alpha2.uppercase(Locale.ROOT)) }
            .sortedBy { it.name }
    }

    private fun setupCountryAndServerAdapters(
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

        views.serverUrlInput.setOnClickListener { views.serverUrlInput.showDropDownWhenSafe() }
        views.serverUrlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                views.serverUrlInput.showDropDownWhenSafe()
            }
        }
        views.countryInput.setOnClickListener { views.countryInput.showDropDownWhenSafe() }
        views.countryInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                views.countryInput.showDropDownWhenSafe()
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

    private fun handleServerConfigurationSave(
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
            views.serverUrlLayout.error = getString(R.string.server_configuration_duplicate_error)
        }
    }

    private fun loadServerConfiguration(): ServerConfiguration {
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

    private fun saveServerConfiguration(
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

    private fun updateServerFlag(
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

    private fun refreshServerOptions(
        serverInput: MaterialAutoCompleteTextView,
        serverLayout: TextInputLayout,
    ) {
        val currentConfig = loadServerConfiguration()
        val options = createServerOptions(currentConfig)
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

    private fun createServerOptions(currentConfig: ServerConfiguration): List<ServerOption> {
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

    private fun builtInServerOptions(): List<ServerOption> =
        BUILT_IN_SERVERS.map {
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

    private fun addCustomServer(
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

    private fun loadCustomServers(): MutableList<CustomServer> {
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

    private fun clearCustomServers() {
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

    private fun persistCustomServers(servers: List<CustomServer>) {
        val json = customServerAdapter.toJson(servers)
        serverPreferences
            .edit()
            .putString(KEY_CUSTOM_SERVERS, json)
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
        return normalized
            .newBuilder()
            .build()
            .toString()
            .trimEnd('/')
    }

    private suspend fun handleLoginResult(
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

    private fun recordLoginActivity(
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

    private fun buildLoginActivityUrl(baseUrl: String): String? =
        baseUrl
            .toHttpUrlOrNull()
            ?.newBuilder()
            ?.addPathSegments("db/login_activities")
            ?.build()
            ?.toString()

    private fun buildLoginActivityPayload(username: String): JSONObject? {
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
            }
        }.getOrNull()
    }

    private fun setLoadingState(
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

    private fun saveRememberedCredentials(
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

    private fun clearRememberedCredentials() {
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

    private fun clearStoredSessionIfNotRemembered() {
        val rememberedInSecurePrefs = securePreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)
        val rememberedInLegacyPrefs = serverPreferences.getBoolean(KEY_REMEMBER_CREDENTIALS, false)
        if (rememberedInSecurePrefs || rememberedInLegacyPrefs) {
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

    private fun loadRememberedCredentials(): RememberedCredentials? {
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

    private fun isSurveyTranslationEnabled(): Boolean =
        serverPreferences.getBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, DEFAULT_SURVEY_TRANSLATION_ENABLED)

    private fun setSurveyTranslationEnabled(enabled: Boolean) {
        serverPreferences
            .edit()
            .putBoolean(KEY_SURVEY_TRANSLATIONS_ENABLED, enabled)
            .apply()
    }

    private fun isSurveyTranslationConsentAccepted(): Boolean = serverPreferences.getBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)

    private fun setSurveyTranslationConsentAccepted(accepted: Boolean) {
        serverPreferences
            .edit()
            .putBoolean(KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, accepted)
            .apply()
    }

    override fun onDestroy() {
        serverStatusJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 1
        const val SECURE_PREFS_NAME = "secure_server_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        private const val KEY_SERVER_CODE = "server_code"
        private const val KEY_COUNTRY_CODE = "country_code"
        private const val KEY_SERVER_DISPLAY_NAME = "server_display_name"
        private const val KEY_CUSTOM_SERVERS = "custom_servers"
        const val KEY_REMEMBER_CREDENTIALS = "remember_credentials"
        const val KEY_REMEMBERED_USERNAME = "remembered_username"
        const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        private const val KEY_SURVEY_TRANSLATIONS_ENABLED = "survey_translations_enabled"
        private const val KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED = "survey_translation_consent_accepted"
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        const val EXTRA_ALLOW_AUTO_LOGIN = "extra_allow_auto_login"
        private const val DEFAULT_COUNTRY_CODE = "GT"
        private const val DEFAULT_SERVER_URL_PREFIX = "https://"
        private const val DEFAULT_SURVEY_TRANSLATION_ENABLED = true
        private const val LOGO_SHRUNK_DP = 50f
        private const val APP_VERSION_SHRUNK_BOTTOM_MARGIN_DP = 5f
        private const val LOGIN_SCROLL_SHRUNK_PADDING_TOP_DP = 5f
        private const val LOGIN_TIME_LENGTH = 13
        private val BUILT_IN_SERVERS =
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
    }

    private data class ServerOption(
        val displayName: String,
        val baseUrl: String,
        val countryCode: String,
        val actionType: ServerAction? = null,
    ) {
        val isAction: Boolean
            get() = actionType != null
        val isDivider: Boolean
            get() = actionType == ServerAction.DIVIDER

        override fun toString(): String = displayName

        companion object {
            fun divider(): ServerOption = ServerOption("", "", "", ServerAction.DIVIDER)
        }
    }

    private enum class ServerAction {
        CONFIGURE,
        CLEAR,
        DIVIDER,
    }

    private data class ServerConfiguration(
        val baseUrl: String,
        val countryCode: String,
        val displayName: String,
    )

    private data class BuiltInServer(
        val nameRes: Int,
        val baseUrl: String,
        val countryCode: String,
    )

    private data class CustomServer(
        val displayName: String,
        val baseUrl: String,
        val countryCode: String,
    ) {
        fun toServerOption(): ServerOption = ServerOption(displayName, baseUrl, countryCode)
    }

    private data class RememberedCredentials(
        val username: String,
        val password: String,
    )

    private inner class ServerOptionAdapter(
        context: Context,
    ) : ArrayAdapter<ServerOption>(context, 0, mutableListOf()) {
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

    private fun persistServerMetadata(
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

    private fun shrinkLogo(
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

    private fun restoreLogo(
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
            loginScroll.paddingBottom,
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
            loginScroll.paddingBottom,
        )
        isLoginScrollPaddingShrunk = false
    }
}

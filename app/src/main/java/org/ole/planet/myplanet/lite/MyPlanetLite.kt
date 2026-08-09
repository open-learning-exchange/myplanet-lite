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
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.doOnPreDraw
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.blongho.country_data.World
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.profile.UserProfileSync
import org.ole.planet.myplanet.lite.util.AppNavigator
import org.ole.planet.myplanet.lite.util.IntentUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import kotlin.math.roundToInt

class MyPlanetLite : BaseActivity() {
    internal var originalLogoWidth = 0
    internal var originalLogoHeight = 0
    internal var shrunkLogoSizePx = 0
    internal var isLogoShrunk = false
    internal var originalAppVersionBottomMargin = 0
    internal var shrunkAppVersionBottomMarginPx = 0
    internal var originalLoginScrollPaddingTop = 0
    internal var shrunkLoginScrollPaddingTopPx = 0
    internal var isLoginScrollPaddingShrunk = false

    internal lateinit var serverAdapter: ServerOptionAdapter
    internal lateinit var serverInputLayoutView: TextInputLayout
    internal lateinit var serverAutoCompleteView: MaterialAutoCompleteTextView
    internal lateinit var loginUsernameInput: TextInputEditText
    internal lateinit var loginPasswordInput: TextInputEditText
    internal lateinit var rememberMeCheckBox: MaterialCheckBox
    internal var suppressRememberListener = false
    internal lateinit var serverStatusIconView: ImageView
    internal lateinit var loginButtonView: Button
    internal lateinit var loginProgressView: ProgressBar
    internal lateinit var loginErrorTextView: TextView
    internal lateinit var signupPromptView: TextView
    internal lateinit var signupButtonView: Button
    internal lateinit var privacyPolicyPromptView: TextView
    internal val connectivityClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    internal var serverStatusJob: Job? = null
    internal var currentServerBaseUrl: String = ""
    internal var isServerReachable = false
    internal var isLoginInProgress = false
    internal var rememberedLoginCredentials: RememberedCredentials? = null
    internal var shouldAutoLoginOnLaunch = false
    internal var sessionRestoreAttempted = false
    internal var credentialsAutoLoginAttempted = false
    internal var sessionRestoreInProgress = false
    internal var autoLoginEnabled = false
    internal val userProfileDatabase: UserProfileDatabase by lazy {
        UserProfileDatabase.getInstance(applicationContext)
    }
    internal val userProfileSync: UserProfileSync by lazy {
        UserProfileSync(connectivityClient, userProfileDatabase)
    }
    internal val serverPreferences: SharedPreferences by lazy {
        SecurePreferencesProvider.getServerPreferences(applicationContext)
    }

    internal val securePreferences: SharedPreferences by lazy {
        SecurePreferencesProvider.getEncryptedPreferences(applicationContext, SECURE_PREFS_NAME)
    }
    internal val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    internal val customServerAdapter: JsonAdapter<List<CustomServer>> by lazy {
        val type = Types.newParameterizedType(List::class.java, CustomServer::class.java)
        moshi.adapter(type)
    }
    internal val serverConnectivityRepository: ServerConnectivityRepository by lazy {
        ServerConnectivityRepository(connectivityClient, moshi)
    }

    internal val signupLauncher =
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

    internal var deepLinkPostId: String? = null

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

    internal fun initializeState(savedInstanceState: Bundle?) {
        ProfileCredentialsStore.setSessionCredentials(null)
        clearStoredSessionIfNotRemembered()
        autoLoginEnabled = intent?.getBooleanExtra(EXTRA_ALLOW_AUTO_LOGIN, false) == true
        deepLinkPostId = intent
            ?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID)
            ?.takeIf { it.isNotBlank() }
            ?: AppNavigator.extractPostId(intent)

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

    internal fun setupViews(
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

    internal fun setupWindowInsets(
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

    internal fun launchDashboard() {
        AppNavigator.navigateToDashboard(this, deepLinkPostId, isOfflineMode = false)
        deepLinkPostId = null
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (::serverAdapter.isInitialized && ::serverInputLayoutView.isInitialized && ::serverAutoCompleteView.isInitialized) {
            refreshServerOptions(serverAutoCompleteView, serverInputLayoutView)
        }
    }



    private fun showServerConfigurationDialog(
        serverInput: MaterialAutoCompleteTextView,
        serverLayout: TextInputLayout,
    ) = showServerConfigurationDialogImpl(serverInput, serverLayout)

    private fun addCustomServer(displayName: String, baseUrl: String, countryCode: String): Boolean =
        addCustomServerImpl(displayName, baseUrl, countryCode)

    private fun loadServerConfiguration(): ServerConfiguration = loadServerConfigurationImpl()

    private fun createServerOptions(currentConfig: ServerConfiguration): List<ServerOption> =
        createServerOptionsImpl(currentConfig)

    private fun attemptStoredSessionRestore(baseUrl: String) = attemptStoredSessionRestoreImpl(baseUrl)

    internal data class ServerOption(
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

    internal enum class ServerAction {
        CONFIGURE,
        CLEAR,
        DIVIDER,
    }

    internal data class ServerConfiguration(
        val baseUrl: String,
        val countryCode: String,
        val displayName: String,
    )

    internal data class BuiltInServer(
        val nameRes: Int,
        val baseUrl: String,
        val countryCode: String,
    )

    internal data class CustomServer(
        val displayName: String,
        val baseUrl: String,
        val countryCode: String,
    ) {
        fun toServerOption(): ServerOption = ServerOption(displayName, baseUrl, countryCode)
    }

    internal data class RememberedCredentials(
        val username: String,
        val password: String,
    )

    internal inner class ServerOptionAdapter(context: Context) : ServerOptionAdapterBase(context) {
        override fun submitList(items: List<ServerOption>) = super.submitList(items)
        override fun getItem(position: Int): ServerOption? = super.getItem(position)
    }

    internal fun isRememberCheckBoxInitialized() = ::rememberMeCheckBox.isInitialized
    internal fun isLoginButtonInitialized() = ::loginButtonView.isInitialized
    internal fun isLoginProgressInitialized() = ::loginProgressView.isInitialized
    internal fun isLoginErrorInitialized() = ::loginErrorTextView.isInitialized
    internal fun isServerStatusIconInitialized() = ::serverStatusIconView.isInitialized
    internal fun isSignupButtonInitialized() = ::signupButtonView.isInitialized

    override fun onDestroy() {
        serverStatusJob?.cancel()
        super.onDestroy()
    }


    companion object {
        const val SECURE_PREFS_NAME = "secure_server_prefs"
        const val KEY_REMEMBER_CREDENTIALS = "remember_credentials"
        const val KEY_REMEMBERED_USERNAME = "remembered_username"
        const val KEY_REMEMBERED_PASSWORD = "remembered_password"
        const val EXTRA_ALLOW_AUTO_LOGIN = "extra_allow_auto_login"
    }
}

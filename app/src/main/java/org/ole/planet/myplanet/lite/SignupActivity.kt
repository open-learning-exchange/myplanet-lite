/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.signup.SignupRepository
import org.ole.planet.myplanet.lite.profile.GENDER_FEMALE
import org.ole.planet.myplanet.lite.profile.GENDER_MALE
import org.ole.planet.myplanet.lite.profile.LearningLevelTranslator
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class SignupActivity : BaseActivity() {

    internal var imeInsetBottom: Int = 0
    internal var birthDateSelection: Long? = null

    internal lateinit var scrollView: ScrollView
    internal lateinit var imeSpacer: View
    internal lateinit var backIcon: ShapeableImageView
    internal lateinit var backButton: MaterialButton
    internal lateinit var nextButton: MaterialButton
    internal lateinit var titleView: TextView
    internal lateinit var subtitleView: TextView
    internal lateinit var genderErrorView: TextView
    internal lateinit var autoLoginCheck: MaterialCheckBox

    internal lateinit var usernameLayout: TextInputLayout
    internal lateinit var usernameInput: TextInputEditText
    internal lateinit var firstNameLayout: TextInputLayout
    internal lateinit var firstNameInput: TextInputEditText
    internal lateinit var middleNameInput: TextInputEditText
    internal lateinit var lastNameLayout: TextInputLayout
    internal lateinit var lastNameInput: TextInputEditText
    internal lateinit var birthDateLayout: TextInputLayout
    internal lateinit var birthDateInput: TextInputEditText
    internal lateinit var genderGroup: RadioGroup
    internal lateinit var emailLayout: TextInputLayout
    internal lateinit var emailInput: TextInputEditText
    internal lateinit var phoneLayout: TextInputLayout
    internal lateinit var phoneInput: TextInputEditText
    internal lateinit var passwordLayout: TextInputLayout
    internal lateinit var passwordInput: TextInputEditText
    internal lateinit var confirmPasswordLayout: TextInputLayout
    internal lateinit var confirmPasswordInput: TextInputEditText
    internal lateinit var languageLayout: TextInputLayout
    internal lateinit var languageInput: AutoCompleteTextView
    internal lateinit var levelLayout: TextInputLayout
    internal lateinit var levelInput: AutoCompleteTextView

    internal lateinit var languageOptions: List<SignupLanguageOption>
    internal var selectedLanguageOption: SignupLanguageOption? = null

    internal lateinit var stepViews: Map<SignupStep, View>

    internal val connectivityClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    internal var serverBaseUrl: String = ""
    internal var isServerReachable: Boolean = false
    internal var isCheckingServerAvailability: Boolean = false
    internal var isProcessingStepAction: Boolean = false
    internal var serverCheckJob: Job? = null
    internal var currentConnectivityStep: SignupStep? = null
    internal var serverParentCode: String? = null
    internal var serverCode: String? = null
    internal val serverPreferences by lazy {
        SecurePreferencesProvider.getServerPreferences(applicationContext)
    }
    internal val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    internal val serverConnectivityRepository: ServerConnectivityRepository by lazy {
        ServerConnectivityRepository(connectivityClient, moshi)
    }
    internal val signupRepository: SignupRepository by lazy {
        SignupRepository(connectivityClient)
    }

    internal val steps = SignupStep.entries
    internal var currentStepIndex = 0

    companion object {
        private const val STATE_BIRTH_DATE_SELECTION = "state_birth_date_selection"
        private const val STATE_STEP_INDEX = "state_step_index"
        internal const val BIRTH_DATE_PICKER_TAG = "signup_birth_date_picker"
        const val EXTRA_AUTO_LOGIN = "org.ole.planet.myplanet.lite.signup.AUTO_LOGIN"
        const val EXTRA_USERNAME = "org.ole.planet.myplanet.lite.signup.USERNAME"
        const val EXTRA_SERVER_BASE_URL = "org.ole.planet.myplanet.lite.signup.SERVER_BASE_URL"
        internal const val KEY_SERVER_URL = "server_url"
        internal const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        internal const val KEY_SERVER_CODE = "server_code"
        internal const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        internal const val KEY_DEVICE_UNIQUE_ANDROID_ID = "device_unique_android_id"
        internal const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        internal val USERNAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9]*$")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_signup)

        birthDateSelection = savedInstanceState?.getLong(STATE_BIRTH_DATE_SELECTION)
        currentStepIndex = savedInstanceState?.getInt(STATE_STEP_INDEX) ?: 0

        initializeViews()
        setupWindowInsets()
        setupStepViews()
        setupUsernameFilter()
        setupLanguageOptions()
        setupFocusAndValidationListeners()
        setupClickListeners()
        setupServerConfiguration()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateBack()
            }
        })

        updateStepVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        birthDateSelection?.let { selection ->
            outState.putLong(STATE_BIRTH_DATE_SELECTION, selection)
        }
        outState.putInt(STATE_STEP_INDEX, currentStepIndex)
    }

    override fun onDestroy() {
        serverCheckJob?.cancel()
        super.onDestroy()
    }

    suspend fun checkUsernameAvailability(username: String): UsernameAvailability {
        return signupRepository.checkUsernameAvailability(serverBaseUrl, username)
    }


    internal fun validateCurrentStep(): Boolean {
        return when (steps[currentStepIndex]) {
            SignupStep.USERNAME -> validateUsername()
            SignupStep.NAMES -> validateNames()
            SignupStep.BIRTH_DATE -> validateBirthDate()
            SignupStep.GENDER -> validateGender()
            SignupStep.CONTACT -> validateContact()
            SignupStep.PASSWORD -> validatePasswords()
            SignupStep.LANGUAGE -> validateLanguage()
            SignupStep.LICENSE -> true
        }
    }

    internal suspend fun submitSignupPayload(): SignupSubmissionResult {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        if (username.isEmpty()) {
            return SignupSubmissionResult.FAILED
        }

        val payload = buildSignupPayload(username) ?: return SignupSubmissionResult.FAILED
        return signupRepository.submitSignup(serverBaseUrl, username, payload)
    }

    private fun buildSignupPayload(username: String): JSONObject? {
        val firstName = firstNameInput.text?.toString()?.trim().orEmpty()
        val lastName = lastNameInput.text?.toString()?.trim().orEmpty()
        val middleName = middleNameInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val phoneNumber = phoneInput.text?.toString()?.trim().orEmpty()
        val birthDate = birthDateInput.text?.toString()?.trim().orEmpty()
        val languageLabel = languageInput.text?.toString()?.trim().orEmpty()
        val levelLabel = levelInput.text?.toString()?.trim().orEmpty()
        val levelValue = LearningLevelTranslator.toEnglish(this, levelLabel)?.takeIf { it.isNotEmpty() }
        val androidId = serverPreferences.getString(KEY_DEVICE_ANDROID_ID, null)?.takeIf { it.isNotBlank() }
        val uniqueAndroidId = serverPreferences.getString(KEY_DEVICE_UNIQUE_ANDROID_ID, null)?.takeIf { it.isNotBlank() }
        val customDeviceName = serverPreferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }

        val genderValue = when (genderGroup.checkedRadioButtonId) {
            R.id.signupGenderMale -> GENDER_MALE
            R.id.signupGenderFemale -> GENDER_FEMALE
            else -> null
        } ?: return null

        return JSONObject().apply {
            put("name", username)
            put("firstName", firstName)
            put("lastName", lastName)
            put("middleName", middleName)
            put("password", password)
            put("isUserAdmin", false)
            put("joinDate", System.currentTimeMillis())
            put("email", email)
            putOpt("planetCode", serverCode)
            putOpt("parentCode", serverParentCode)
            put("language", languageLabel)
            put("level", levelValue ?: levelLabel)
            put("phoneNumber", phoneNumber)
            put("birthDate", birthDate)
            put("gender", genderValue)
            put("type", "user")
            put("betaEnabled", false)
            androidId?.let { put("androidId", it) }
            uniqueAndroidId?.let { put("uniqueAndroidId", it) }
            customDeviceName?.let { put("customDeviceName", it) }
            put("roles", JSONArray().apply { put("learner") })
        }
    }

    fun validateUsername(): Boolean {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        return if (username.isNotEmpty() && USERNAME_PATTERN.matches(username)) {
            usernameLayout.error = null
            true
        } else {
            usernameLayout.error = getString(R.string.signup_username_error_invalid)
            false
        }
    }

    fun validateNames(): Boolean {
        val firstName = firstNameInput.text?.toString()?.trim().orEmpty()
        val lastName = lastNameInput.text?.toString()?.trim().orEmpty()

        var valid = true

        if (firstName.isEmpty()) {
            firstNameLayout.error = getString(R.string.signup_first_name_error_required)
            valid = false
        } else {
            firstNameLayout.error = null
        }

        if (lastName.isEmpty()) {
            lastNameLayout.error = getString(R.string.signup_last_name_error_required)
            valid = false
        } else {
            lastNameLayout.error = null
        }

        return valid
    }

    fun validateContact(): Boolean {
        val email = emailInput.text?.toString()?.trim().orEmpty()
        val phone = phoneInput.text?.toString()?.trim().orEmpty()
        var valid = true

        if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = null
        } else {
            emailLayout.error = getString(R.string.signup_email_error_invalid)
            valid = false
        }

        if (Patterns.PHONE.matcher(phone).matches()) {
            phoneLayout.error = null
        } else {
            phoneLayout.error = getString(R.string.signup_phone_error_invalid)
            valid = false
        }

        return valid
    }

    fun validatePasswords(): Boolean {
        val password = passwordInput.text?.toString().orEmpty()
        val passwordsMatch = updatePasswordErrorState(showEmptyError = true)
        return if (password.length < 1) {
            passwordLayout.error = getString(R.string.signup_password_error_length)
            if (confirmPasswordLayout.error == getString(R.string.signup_password_error_mismatch)) {
                confirmPasswordLayout.error = null
            }
            false
        } else {
            if (passwordLayout.error == getString(R.string.signup_password_error_length)) {
                passwordLayout.error = null
            }
            passwordsMatch
        }
    }

}

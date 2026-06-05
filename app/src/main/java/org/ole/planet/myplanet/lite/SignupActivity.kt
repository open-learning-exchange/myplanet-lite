/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.text.InputFilter
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Filter
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.profile.GENDER_FEMALE
import org.ole.planet.myplanet.lite.profile.GENDER_MALE
import org.ole.planet.myplanet.lite.profile.LearningLevelTranslator
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class SignupActivity : BaseActivity() {

    private data class SignupLanguageOption(
        val languageTag: String,
        val labelRes: Int,
        val levelArrayRes: Int
    )

    private enum class SignupStep(
        val titleRes: Int,
        val subtitleRes: Int,
        val nextTextRes: Int = R.string.signup_next_action,
        val showBackButton: Boolean = true
    ) {
        USERNAME(
            R.string.signup_step_username_title,
            R.string.signup_step_username_subtitle,
            showBackButton = false
        ),
        NAMES(
            R.string.signup_step_names_title,
            R.string.signup_step_names_subtitle
        ),
        BIRTH_DATE(
            R.string.signup_step_birth_date_title,
            R.string.signup_step_birth_date_subtitle
        ),
        GENDER(
            R.string.signup_step_gender_title,
            R.string.signup_step_gender_subtitle
        ),
        CONTACT(
            R.string.signup_step_contact_title,
            R.string.signup_step_contact_subtitle
        ),
        PASSWORD(
            R.string.signup_step_password_title,
            R.string.signup_step_password_subtitle
        ),
        LANGUAGE(
            R.string.signup_step_language_title,
            R.string.signup_step_language_subtitle
        ),
        LICENSE(
            R.string.signup_step_license_title,
            R.string.signup_step_license_subtitle,
            nextTextRes = R.string.signup_accept_action
        );
    }

    private var imeInsetBottom: Int = 0
    private var birthDateSelection: Long? = null

    private lateinit var scrollView: ScrollView
    private lateinit var imeSpacer: View
    private lateinit var backIcon: ShapeableImageView
    private lateinit var backButton: MaterialButton
    private lateinit var nextButton: MaterialButton
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView
    private lateinit var genderErrorView: TextView
    private lateinit var autoLoginCheck: MaterialCheckBox

    private lateinit var usernameLayout: TextInputLayout
    private lateinit var usernameInput: TextInputEditText
    private lateinit var firstNameLayout: TextInputLayout
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var middleNameInput: TextInputEditText
    private lateinit var lastNameLayout: TextInputLayout
    private lateinit var lastNameInput: TextInputEditText
    private lateinit var birthDateLayout: TextInputLayout
    private lateinit var birthDateInput: TextInputEditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var emailLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneLayout: TextInputLayout
    private lateinit var phoneInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var languageLayout: TextInputLayout
    private lateinit var languageInput: AutoCompleteTextView
    private lateinit var levelLayout: TextInputLayout
    private lateinit var levelInput: AutoCompleteTextView

    private lateinit var languageOptions: List<SignupLanguageOption>
    private var selectedLanguageOption: SignupLanguageOption? = null

    private lateinit var stepViews: Map<SignupStep, View>

    private val connectivityClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    private var serverBaseUrl: String = ""
    private var isServerReachable: Boolean = false
    private var isCheckingServerAvailability: Boolean = false
    private var isProcessingStepAction: Boolean = false
    private var serverCheckJob: Job? = null
    private var currentConnectivityStep: SignupStep? = null
    private var serverParentCode: String? = null
    private var serverCode: String? = null
    private val serverPreferences by lazy {
        SecurePreferencesProvider.getServerPreferences(applicationContext)
    }
    private val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
    private val serverConnectivityRepository: ServerConnectivityRepository by lazy {
        ServerConnectivityRepository(connectivityClient, moshi)
    }

    private val steps = SignupStep.entries
    private var currentStepIndex = 0

    companion object {
        private const val STATE_BIRTH_DATE_SELECTION = "state_birth_date_selection"
        private const val STATE_STEP_INDEX = "state_step_index"
        private const val BIRTH_DATE_PICKER_TAG = "signup_birth_date_picker"
        const val EXTRA_AUTO_LOGIN = "org.ole.planet.myplanet.lite.signup.AUTO_LOGIN"
        const val EXTRA_USERNAME = "org.ole.planet.myplanet.lite.signup.USERNAME"
        const val EXTRA_SERVER_BASE_URL = "org.ole.planet.myplanet.lite.signup.SERVER_BASE_URL"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        private const val KEY_SERVER_CODE = "server_code"
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_UNIQUE_ANDROID_ID = "device_unique_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        private val USERNAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9]*$")
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

    private fun initializeViews() {
        scrollView = findViewById(R.id.signupScroll)
        imeSpacer = findViewById(R.id.signupImeSpacer)
        backIcon = findViewById(R.id.signupBackIcon)
        backButton = findViewById(R.id.signupBackButton)
        nextButton = findViewById(R.id.signupNextButton)
        titleView = findViewById(R.id.signupTitle)
        subtitleView = findViewById(R.id.signupSubtitle)

        usernameLayout = findViewById(R.id.signupUsernameInputLayout)
        usernameInput = findViewById(R.id.signupUsernameInput)
        firstNameLayout = findViewById(R.id.signupFirstNameInputLayout)
        firstNameInput = findViewById(R.id.signupFirstNameInput)
        middleNameInput = findViewById(R.id.signupMiddleNameInput)
        lastNameLayout = findViewById(R.id.signupLastNameInputLayout)
        lastNameInput = findViewById(R.id.signupLastNameInput)
        birthDateLayout = findViewById(R.id.signupBirthDateInputLayout)
        birthDateInput = findViewById(R.id.signupBirthDateInput)
        genderGroup = findViewById(R.id.signupGenderGroup)
        genderErrorView = findViewById(R.id.signupGenderError)
        emailLayout = findViewById(R.id.signupEmailInputLayout)
        emailInput = findViewById(R.id.signupEmailInput)
        phoneLayout = findViewById(R.id.signupPhoneInputLayout)
        phoneInput = findViewById(R.id.signupPhoneInput)
        passwordLayout = findViewById(R.id.signupPasswordInputLayout)
        passwordInput = findViewById(R.id.signupPasswordInput)
        confirmPasswordLayout = findViewById(R.id.signupConfirmPasswordInputLayout)
        confirmPasswordInput = findViewById(R.id.signupConfirmPasswordInput)
        languageLayout = findViewById(R.id.signupLanguageInputLayout)
        languageInput = findViewById(R.id.signupLanguageInput)
        levelLayout = findViewById(R.id.signupLevelInputLayout)
        levelInput = findViewById(R.id.signupLevelInput)
        autoLoginCheck = findViewById(R.id.signupAutoLoginCheck)

        setupLanguageAndLevelInputs()
    }

    private fun setupLanguageAndLevelInputs() {
        languageInput.keyListener = null
        languageInput.setTextIsSelectable(false)
        languageInput.isLongClickable = false
        languageInput.setOnClickListener {
            if (!languageInput.isPopupShowing) {
                languageInput.showDropDown()
            }
        }

        levelInput.keyListener = null
        levelInput.setTextIsSelectable(false)
        levelInput.isLongClickable = false
        levelInput.setOnClickListener {
            if (!levelInput.isPopupShowing) {
                levelInput.showDropDown()
            }
        }
    }

    private fun setupWindowInsets() {
        val root = findViewById<View>(R.id.signupRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val originalPaddingStart = scrollView.paddingStart
        val originalPaddingTop = scrollView.paddingTop
        val originalPaddingEnd = scrollView.paddingEnd
        val originalPaddingBottom = scrollView.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPadding = originalPaddingBottom + systemInsets.bottom

            imeSpacer.updateLayoutParams<android.widget.LinearLayout.LayoutParams> {
                height = imeInsets.bottom
            }

            imeInsetBottom = imeInsets.bottom

            v.setPaddingRelative(
                originalPaddingStart,
                originalPaddingTop,
                originalPaddingEnd,
                bottomPadding
            )

            currentFocus?.let { focused ->
                ensureVisible(scrollView, focused)
            }
            insets
        }

        ViewCompat.requestApplyInsets(scrollView)
    }

    private fun setupStepViews() {
        val usernameStep = findViewById<View>(R.id.signupStepUsername)
        val namesStep = findViewById<View>(R.id.signupStepNames)
        val birthDateStep = findViewById<View>(R.id.signupStepBirthDate)
        val genderStep = findViewById<View>(R.id.signupStepGender)
        val contactStep = findViewById<View>(R.id.signupStepContact)
        val passwordStep = findViewById<View>(R.id.signupStepPassword)
        val languageStep = findViewById<View>(R.id.signupStepLanguage)
        val licenseStep = findViewById<View>(R.id.signupStepLicense)

        stepViews = mapOf(
            SignupStep.USERNAME to usernameStep,
            SignupStep.NAMES to namesStep,
            SignupStep.BIRTH_DATE to birthDateStep,
            SignupStep.GENDER to genderStep,
            SignupStep.CONTACT to contactStep,
            SignupStep.PASSWORD to passwordStep,
            SignupStep.LANGUAGE to languageStep,
            SignupStep.LICENSE to licenseStep
        )
    }

    private fun setupUsernameFilter() {
        val usernameFilter = InputFilter { source, start, end, dest, dstart, dend ->
            if (start == end) {
                return@InputFilter null
            }
            val replacement = source.subSequence(start, end).toString()
            val prospective = StringBuilder(dest)
            prospective.replace(dstart, dend, replacement)
            val resultText = prospective.toString()
            if (resultText.isEmpty() || USERNAME_PATTERN.matches(resultText)) {
                null
            } else {
                ""
            }
        }
        usernameInput.filters = arrayOf(usernameFilter)
    }

    private fun setupLanguageOptions() {
        languageOptions = listOf(
            SignupLanguageOption(
                languageTag = "en",
                labelRes = R.string.language_name_english,
                levelArrayRes = R.array.signup_level_options_language_en
            ),
            SignupLanguageOption(
                languageTag = "es",
                labelRes = R.string.language_name_spanish,
                levelArrayRes = R.array.signup_level_options_language_es
            ),
            SignupLanguageOption(
                languageTag = "fr",
                labelRes = R.string.language_name_french,
                levelArrayRes = R.array.signup_level_options_language_fr
            ),
            SignupLanguageOption(
                languageTag = "pt",
                labelRes = R.string.language_name_portuguese,
                levelArrayRes = R.array.signup_level_options_language_pt
            ),
            SignupLanguageOption(
                languageTag = "ar",
                labelRes = R.string.language_name_arabic,
                levelArrayRes = R.array.signup_level_options_language_ar
            ),
            SignupLanguageOption(
                languageTag = "so",
                labelRes = R.string.language_name_somali,
                levelArrayRes = R.array.signup_level_options_language_so
            ),
            SignupLanguageOption(
                languageTag = "ne",
                labelRes = R.string.language_name_nepali,
                levelArrayRes = R.array.signup_level_options_language_ne
            ),
            SignupLanguageOption(
                languageTag = "hi",
                labelRes = R.string.language_name_hindi,
                levelArrayRes = R.array.signup_level_options_language_hi
            )
        )

        val languageLabels = languageOptions.map { getString(it.labelRes) }
        val languageAdapter = createNonFilteringAdapter(languageLabels)
        languageInput.setAdapter(languageAdapter)
        languageInput.setOnItemClickListener { _, _, position, _ ->
            val option = languageOptions[position]
            languageLayout.error = null
            applySelectedLanguage(option, resetLevel = true)
        }

        languageInput.doAfterTextChanged { text ->
            languageLayout.error = null
            val label = text?.toString()?.trim().orEmpty()
            val option = languageOptions.firstOrNull { getString(it.labelRes) == label }
            if (option != null && option != selectedLanguageOption) {
                applySelectedLanguage(option, resetLevel = false)
            }
        }

        initializeLanguageSelection()
    }

    private fun TextView.clearErrorOnTextChange(layout: TextInputLayout) {
        doAfterTextChanged {
            layout.error = null
        }
    }

    private fun setupFocusAndValidationListeners() {
        setupGeneralFocusListeners()
        setupTextChangeListeners()
        setupSpecialInputListeners()
    }

    private fun setupGeneralFocusListeners() {
        val focusableInputs = listOf(
            usernameInput,
            firstNameInput,
            middleNameInput,
            lastNameInput,
            birthDateInput,
            emailInput,
            phoneInput,
            passwordInput,
            confirmPasswordInput,
            languageInput,
            levelInput
        )

        focusableInputs.forEach { input ->
            input.setOnFocusChangeListener { v, hasFocus ->
                if (hasFocus) {
                    ensureVisible(scrollView, v)
                    if (v === languageInput && !languageInput.isPopupShowing) {
                        languageInput.showDropDown()
                    } else if (v === levelInput && !levelInput.isPopupShowing) {
                        levelInput.showDropDown()
                    }
                }
            }
        }
    }

    private fun setupTextChangeListeners() {
        levelInput.clearErrorOnTextChange(levelLayout)
        usernameInput.clearErrorOnTextChange(usernameLayout)
        usernameInput.doAfterTextChanged {
            verifyServerAvailability(SignupStep.USERNAME, force = true)
        }

        firstNameInput.clearErrorOnTextChange(firstNameLayout)
        lastNameInput.clearErrorOnTextChange(lastNameLayout)
        emailInput.clearErrorOnTextChange(emailLayout)
        phoneInput.clearErrorOnTextChange(phoneLayout)
    }

    private fun setupSpecialInputListeners() {
        genderGroup.setOnCheckedChangeListener { _, _ ->
            genderErrorView.visibility = View.GONE
            ensureVisible(scrollView, genderGroup)
        }

        birthDateInput.keyListener = null
        birthDateInput.setOnClickListener {
            ensureVisible(scrollView, birthDateInput)
            showBirthDatePicker()
        }
        birthDateInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ensureVisible(scrollView, v)
                showBirthDatePicker()
            }
        }
        birthDateSelection?.let { selection ->
            birthDateInput.setText(formatBirthDate(selection))
        }

        passwordInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ensureVisible(scrollView, v)
            } else {
                updatePasswordErrorState()
            }
        }

        confirmPasswordInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                ensureVisible(scrollView, v)
            } else {
                updatePasswordErrorState()
            }
        }
    }

    private fun setupClickListeners() {
        backIcon.setOnClickListener {
            navigateBack()
        }

        backButton.setOnClickListener {
            navigateBack()
        }

        nextButton.setOnClickListener {
            handleNextButtonClick()
        }
    }

    private fun setupServerConfiguration() {
        serverBaseUrl = intent.getStringExtra(EXTRA_SERVER_BASE_URL)?.trim().orEmpty()
        if (serverBaseUrl.isEmpty()) {
            serverBaseUrl = loadStoredServerBaseUrl()
        }
        serverParentCode = serverPreferences.getString(KEY_SERVER_PARENT_CODE, null)
        serverCode = serverPreferences.getString(KEY_SERVER_CODE, null)
    }

    private fun initializeLanguageSelection() {
        val existingLabel = languageInput.text?.toString()?.trim().orEmpty()
        val matchedOption = languageOptions.firstOrNull { getString(it.labelRes) == existingLabel }

        if (matchedOption != null) {
            applySelectedLanguage(matchedOption, resetLevel = false)
            return
        }

        val defaultOption = findDefaultLanguageOption()
        languageInput.setText(getString(defaultOption.labelRes), false)
        applySelectedLanguage(defaultOption, resetLevel = true)
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


    private fun navigateBack() {
        if (currentStepIndex == 0) {
            finish()
        } else {
            showStep(currentStepIndex - 1)
        }
    }

    private fun moveToNextStep() {
        if (currentStepIndex < steps.lastIndex) {
            showStep(currentStepIndex + 1)
        }
    }

    private fun completeSignup() {
        val autoLogin = autoLoginCheck.isChecked
        val resultIntent = Intent().apply {
            putExtra(EXTRA_AUTO_LOGIN, autoLogin)
            if (autoLogin) {
                val username = usernameInput.text?.toString()?.trim().orEmpty()
                val password = passwordInput.text?.toString().orEmpty()
                putExtra(EXTRA_USERNAME, username)
                org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore.saveTemporarySignUpPassword(this@SignupActivity, password)
            }
        }
        if (autoLogin) {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()
            SecurePreferencesProvider.getEncryptedPreferences(this, MyPlanetLite.SECURE_PREFS_NAME)
                .edit()
                .putBoolean(MyPlanetLite.KEY_REMEMBER_CREDENTIALS, true)
                .putString(MyPlanetLite.KEY_REMEMBERED_USERNAME, username)
                .putString(org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore.getPasswordKey(username), password)
                .apply()
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    private fun findDefaultLanguageOption(): SignupLanguageOption {
        val systemLanguage = Locale.getDefault().language.lowercase(Locale.ROOT)
        return languageOptions.firstOrNull { it.languageTag == systemLanguage } ?: languageOptions.first()
    }

    private fun applySelectedLanguage(option: SignupLanguageOption, resetLevel: Boolean) {
        if (selectedLanguageOption == option && !resetLevel) {
            return
        }

        selectedLanguageOption = option

        val levelValues = getLocalizedLevelValues(option)
        val levelAdapter = createNonFilteringAdapter(levelValues)
        levelInput.setAdapter(levelAdapter)

        val localizedLevel = LearningLevelTranslator
            .toLocalized(this, levelInput.text?.toString(), option.levelArrayRes)
        val shouldClearLevel = resetLevel || localizedLevel.isNullOrBlank()
        if (shouldClearLevel) {
            levelInput.setText("", false)
            levelLayout.error = null
        } else {
            levelInput.setText(localizedLevel, false)
        }
    }

    private fun showStep(index: Int) {
        if (index == currentStepIndex) return
        currentStepIndex = index
        updateStepVisibility()
    }

    private fun getLocalizedLevelValues(option: SignupLanguageOption): List<String> {
        val locale = Locale.forLanguageTag(option.languageTag)
        val baseConfig = resources.configuration
        val localizedConfig = android.content.res.Configuration(baseConfig).apply {
            setLocale(locale)
        }
        val localizedContext = createConfigurationContext(localizedConfig)
        return localizedContext.resources.getStringArray(option.levelArrayRes).toList()
    }

    private fun createNonFilteringAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
            private val filter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults().apply {
                        values = items
                        count = items.size
                    }
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }

            override fun getFilter(): Filter = filter
        }
    }

    private fun updateStepVisibility() {
        val currentStep = steps[currentStepIndex]
        stepViews.forEach { (step, view) ->
            val shouldShow = step == currentStep
            val targetVisibility = if (shouldShow) View.VISIBLE else View.GONE
            if (view.visibility != targetVisibility) {
                view.visibility = targetVisibility
            }
        }

        titleView.setText(currentStep.titleRes)
        subtitleView.setText(currentStep.subtitleRes)
        subtitleView.isClickable = false
        subtitleView.isFocusable = false
        subtitleView.setOnClickListener(null)

        backButton.visibility = if (currentStep.showBackButton) View.VISIBLE else View.GONE
        nextButton.setText(currentStep.nextTextRes)

        when (currentStep) {
            SignupStep.USERNAME, SignupStep.LICENSE -> {
                verifyServerAvailability(currentStep, force = true)
            }
            else -> {
                serverCheckJob?.cancel()
                serverCheckJob = null
                currentConnectivityStep = null
                isCheckingServerAvailability = false
                updateStepActionState(currentStep)
            }
        }

        scrollView.post {
            scrollView.scrollTo(0, 0)
        }
    }

    private fun handleNextButtonClick() {
        val currentStep = steps[currentStepIndex]
        when (currentStep) {
            SignupStep.USERNAME -> submitUsernameStep()
            SignupStep.LICENSE -> submitLicenseStep()
            else -> {
                if (validateCurrentStep()) {
                    moveToNextStep()
                }
            }
        }
    }

    private fun submitUsernameStep() {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        if (!canProceedWithUsernameStep(username)) {
            return
        }

        isProcessingStepAction = true
        updateStepActionState()
        showUsernameAvailabilityChecking(true)

        lifecycleScope.launch {
            val availability = checkUsernameAvailability(username)
            if (!isActive) {
                return@launch
            }

            isProcessingStepAction = false
            showUsernameAvailabilityChecking(false)

            handleUsernameAvailabilityResult(availability)

            updateStepActionState()
        }
    }

    private fun canProceedWithUsernameStep(username: String): Boolean {
        if (isProcessingStepAction) return false
        if (!validateUsername()) return false
        if (isCheckingServerAvailability) {
            Toast.makeText(this, R.string.signup_connection_checking, Toast.LENGTH_SHORT).show()
            return false
        }
        if (!isServerReachable) {
            applyConnectivityState(SignupStep.USERNAME, reachable = false, checking = false)
            verifyServerAvailability(SignupStep.USERNAME, force = true)
            return false
        }
        if (username.isEmpty()) return false
        return true
    }

    private fun handleUsernameAvailabilityResult(availability: UsernameAvailability) {
        when (availability) {
            UsernameAvailability.AVAILABLE -> {
                usernameLayout.error = null
                if (usernameLayout.helperText == getString(R.string.signup_connection_error_input)) {
                    usernameLayout.helperText = null
                }
                moveToNextStep()
            }
            UsernameAvailability.TAKEN -> {
                usernameLayout.error = getString(R.string.signup_username_error_taken)
            }
            UsernameAvailability.UNKNOWN -> {
                usernameLayout.error = null
                usernameLayout.helperText = getString(R.string.signup_connection_error_input)
                isServerReachable = false
                updateStepConnectivityMessage(SignupStep.USERNAME, reachable = false, checking = false)
            }
        }
    }

    private fun submitLicenseStep() {
        if (isProcessingStepAction) {
            return
        }

        lifecycleScope.launch {
            isProcessingStepAction = true
            updateStepActionState()

            if (isCheckingServerAvailability) {
                serverCheckJob?.join()
            }

            val job = verifyServerAvailability(SignupStep.LICENSE, force = true)
            job?.join()

            if (!isServerReachable) {
                isProcessingStepAction = false
                updateStepActionState()
                return@launch
            }

            if (!validateCurrentStep()) {
                isProcessingStepAction = false
                updateStepActionState()
                return@launch
            }

            val submissionResult = submitSignupPayload()

            isProcessingStepAction = false
            updateStepActionState()

            when (submissionResult) {
                SignupSubmissionResult.SUCCESS -> completeSignup()
                SignupSubmissionResult.USERNAME_TAKEN -> {
                    usernameLayout.error = getString(R.string.signup_username_error_taken)
                    showStep(SignupStep.USERNAME.ordinal)
                }
                SignupSubmissionResult.FAILED -> {
                    Toast.makeText(
                        this@SignupActivity,
                        R.string.signup_connection_error_input,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showUsernameAvailabilityChecking(isChecking: Boolean) {
        if (isChecking) {
            usernameLayout.error = null
            usernameLayout.helperText = getString(R.string.signup_username_checking)
        } else if (usernameLayout.helperText == getString(R.string.signup_username_checking)) {
            usernameLayout.helperText = null
        }
    }

    private fun verifyServerAvailability(step: SignupStep, force: Boolean = false): Job? {
        val trimmedBaseUrl = serverBaseUrl.trim()
        if (trimmedBaseUrl.isEmpty()) {
            isServerReachable = false
            applyConnectivityState(step, reachable = false, checking = false)
            return null
        }

        if (!force && currentConnectivityStep == step && isCheckingServerAvailability) {
            return serverCheckJob
        }

        serverCheckJob?.cancel()
        currentConnectivityStep = step

        val job = lifecycleScope.launch {
            applyConnectivityState(step, reachable = false, checking = true)
            val connectivityResult = withContext(Dispatchers.IO) {
                serverConnectivityRepository.checkServerConnectivity(trimmedBaseUrl)
            }
            if (!isActive) {
                return@launch
            }
            if (connectivityResult.reachable) {
                persistServerMetadata(
                    trimmedBaseUrl,
                    connectivityResult.parentCode,
                    connectivityResult.code
                )
            }
            applyConnectivityState(step, reachable = connectivityResult.reachable, checking = false)
        }

        serverCheckJob = job
        return job
    }

    private fun persistServerMetadata(baseUrl: String, parentCode: String?, code: String?) {
        serverParentCode = parentCode
        serverCode = code
        serverPreferences.edit {
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
        }
    }

    private fun applyConnectivityState(step: SignupStep, reachable: Boolean, checking: Boolean) {
        isCheckingServerAvailability = checking
        isServerReachable = if (checking) false else reachable

        if (step == SignupStep.USERNAME) {
            when {
                checking -> {
                    usernameLayout.error = null
                    usernameLayout.helperText = null
                }
                reachable -> {
                    if (usernameLayout.helperText == getString(R.string.signup_connection_checking) ||
                        usernameLayout.helperText == getString(R.string.signup_connection_error_input)
                    ) {
                        usernameLayout.helperText = null
                    }
                }
                else -> {
                    usernameLayout.error = null
                    usernameLayout.helperText = getString(R.string.signup_connection_error_input)
                }
            }
        }

        updateStepConnectivityMessage(step, reachable, checking)
        updateStepActionState()
    }

    private fun updateStepConnectivityMessage(step: SignupStep, reachable: Boolean, checking: Boolean) {
        if (step != steps[currentStepIndex]) {
            return
        }
        if (step != SignupStep.USERNAME && step != SignupStep.LICENSE) {
            return
        }

        when {
            checking -> {
                subtitleView.isClickable = false
                subtitleView.isFocusable = false
                subtitleView.setOnClickListener(null)
            }
            reachable -> {
                subtitleView.setText(step.subtitleRes)
                subtitleView.isClickable = false
                subtitleView.isFocusable = false
                subtitleView.setOnClickListener(null)
            }
            else -> {
                subtitleView.text = getString(R.string.signup_connection_error_retry)
                subtitleView.isClickable = true
                subtitleView.isFocusable = true
                subtitleView.setOnClickListener {
                    verifyServerAvailability(step, force = true)
                }
            }
        }
    }

    private fun updateStepActionState(step: SignupStep = steps[currentStepIndex]) {
        if (step != steps[currentStepIndex]) {
            return
        }
        val shouldEnable = when (step) {
            SignupStep.USERNAME, SignupStep.LICENSE ->
                !isProcessingStepAction && !isCheckingServerAvailability && isServerReachable
            else -> !isProcessingStepAction
        }
        nextButton.isEnabled = shouldEnable
        nextButton.alpha = if (shouldEnable) 1f else 0.5f
    }

    private suspend fun checkUsernameAvailability(username: String): UsernameAvailability {
        val requestUrl = buildUsernameLookupUrl(username) ?: return UsernameAvailability.UNKNOWN
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .build()
                connectivityClient.newCall(request).execute().use { response ->
                    if (response.code == 200) {
                        UsernameAvailability.TAKEN
                    } else {
                        UsernameAvailability.AVAILABLE
                    }
                }
            }.getOrElse {
                UsernameAvailability.UNKNOWN
            }
        }
    }

    private fun buildUsernameLookupUrl(username: String): String? {
        val baseUrl = serverBaseUrl.trim()
        if (baseUrl.isEmpty()) {
            return null
        }
        return baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?.addPathSegments("db/_users/org.couchdb.user:$username")
            ?.build()
            ?.toString()
    }

    private fun loadStoredServerBaseUrl(): String {
        return serverPreferences.getString(KEY_SERVER_URL, "").orEmpty().trim()
    }

    private fun validateCurrentStep(): Boolean {
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

    private suspend fun submitSignupPayload(): SignupSubmissionResult {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        if (username.isEmpty()) {
            return SignupSubmissionResult.FAILED
        }

        val requestUrl = buildUsernameLookupUrl(username) ?: return SignupSubmissionResult.FAILED
        val payload = buildSignupPayload(username) ?: return SignupSubmissionResult.FAILED
        val mediaType = "application/json; charset=utf-8".toMediaType()

        return executeSignupRequest(requestUrl, payload, mediaType)
    }

    @androidx.annotation.VisibleForTesting
    internal suspend fun executeSignupRequest(
        requestUrl: String,
        payload: JSONObject,
        mediaType: okhttp3.MediaType
    ): SignupSubmissionResult {
        return withContext(Dispatchers.IO) {
            try {
                val body = payload.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url(requestUrl)
                    .put(body)
                    .build()
                connectivityClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> SignupSubmissionResult.SUCCESS
                        409 -> SignupSubmissionResult.USERNAME_TAKEN
                        else -> SignupSubmissionResult.FAILED
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(this@SignupActivity, "An unexpected error occurred.", android.widget.Toast.LENGTH_SHORT).show()
                }
                SignupSubmissionResult.FAILED
            }
        }
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

    private enum class UsernameAvailability { AVAILABLE, TAKEN, UNKNOWN }

    @androidx.annotation.VisibleForTesting
    internal enum class SignupSubmissionResult { SUCCESS, USERNAME_TAKEN, FAILED }

    private fun validateUsername(): Boolean {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        return if (username.isNotEmpty() && USERNAME_PATTERN.matches(username)) {
            usernameLayout.error = null
            true
        } else {
            usernameLayout.error = getString(R.string.signup_username_error_invalid)
            false
        }
    }

    private fun validateNames(): Boolean {
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

    private fun validateBirthDate(): Boolean {
        return if (birthDateSelection != null) {
            birthDateLayout.error = null
            true
        } else {
            birthDateLayout.error = getString(R.string.signup_birth_date_error_required)
            false
        }
    }

    private fun validateGender(): Boolean {
        return if (genderGroup.checkedRadioButtonId != -1) {
            genderErrorView.visibility = View.GONE
            true
        } else {
            genderErrorView.text = getString(R.string.signup_gender_error_required)
            genderErrorView.visibility = View.VISIBLE
            false
        }
    }

    private fun validateContact(): Boolean {
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

    private fun validatePasswords(): Boolean {
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

    private fun validateLanguage(): Boolean {
        val language = languageInput.text?.toString()?.trim().orEmpty()
        val level = levelInput.text?.toString()?.trim().orEmpty()
        var valid = true

        if (language.isEmpty()) {
            languageLayout.error = getString(R.string.signup_language_error_required)
            valid = false
        } else {
            languageLayout.error = null
        }

        if (level.isEmpty()) {
            levelLayout.error = getString(R.string.signup_level_error_required)
            valid = false
        } else {
            levelLayout.error = null
        }

        return valid
    }

    private fun updatePasswordErrorState(showEmptyError: Boolean = false): Boolean {
        val password = passwordInput.text?.toString().orEmpty()
        val confirmPassword = confirmPasswordInput.text?.toString().orEmpty()
        val mismatchError = getString(R.string.signup_password_error_mismatch)

        val bothFilled = password.isNotEmpty() && confirmPassword.isNotEmpty()
        return if (bothFilled && password == confirmPassword) {
            if (passwordLayout.error == mismatchError) {
                passwordLayout.error = null
            }
            if (confirmPasswordLayout.error == mismatchError) {
                confirmPasswordLayout.error = null
            }
            true
        } else {
            if (bothFilled || showEmptyError) {
                if (passwordLayout.error == null || passwordLayout.error == mismatchError) {
                    passwordLayout.error = mismatchError
                }
                confirmPasswordLayout.error = mismatchError
            } else {
                if (passwordLayout.error == mismatchError) {
                    passwordLayout.error = null
                }
                if (confirmPasswordLayout.error == mismatchError) {
                    confirmPasswordLayout.error = null
                }
            }
            false
        }
    }

    private fun showBirthDatePicker() {
        if (supportFragmentManager.findFragmentByTag(BIRTH_DATE_PICKER_TAG) != null) {
            return
        }

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.signup_birth_date_picker_title))
            .apply {
                birthDateSelection?.let { setSelection(it) }
            }
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            birthDateSelection = selection
            birthDateInput.setText(formatBirthDate(selection))
            birthDateLayout.error = null
        }

        picker.addOnDismissListener {
            birthDateInput.clearFocus()
        }

        picker.show(supportFragmentManager, BIRTH_DATE_PICKER_TAG)
    }

    private fun formatBirthDate(selection: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(selection))
    }



    private fun ensureVisible(scrollView: ScrollView, view: View) {
        scrollView.post {
            if (!isDescendantOf(scrollView, view)) {
                return@post
            }
            val rect = Rect()
            view.getDrawingRect(rect)
            scrollView.offsetDescendantRectToMyCoords(view, rect)

            val scrollY = scrollView.scrollY
            val scrollViewHeight = scrollView.height
            val visibleBottom = scrollY + scrollViewHeight - imeInsetBottom
            val top = rect.top
            val bottom = rect.bottom

            val scrollDelta = when {
                bottom > visibleBottom -> bottom - visibleBottom
                top < scrollY -> top - scrollY
                else -> 0
            }

            if (scrollDelta != 0) {
                scrollView.smoothScrollBy(0, scrollDelta)
            }
        }
    }

    private fun isDescendantOf(parent: View, child: View): Boolean {
        var current: View? = child
        while (current != null && current != parent) {
            val currentParent = current.parent
            current = currentParent as? View
        }
        return current == parent
    }
}

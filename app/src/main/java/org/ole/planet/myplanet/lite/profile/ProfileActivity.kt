/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite.profile

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.ole.planet.myplanet.lite.BaseActivity
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.model.LanguageOption
import org.ole.planet.myplanet.lite.util.nullIfBlank
import java.util.Locale

class ProfileActivity : BaseActivity() {
    internal val selectAvatarLauncher: ActivityResultLauncher<PickVisualMediaRequest> =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { processAvatarSelection(it) }
        }

    internal val cropAvatarLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            when (result.resultCode) {
                RESULT_OK -> {
                    val croppedUri = result.data?.let { UCrop.getOutput(it) }
                    if (croppedUri != null) {
                        handleCroppedAvatar(croppedUri)
                    } else {
                        Toast
                            .makeText(
                                this,
                                R.string.profile_avatar_picker_error,
                                Toast.LENGTH_SHORT,
                            ).show()
                    }
                }

                UCrop.RESULT_ERROR -> {
                    val error = result.data?.let { UCrop.getError(it) }
                    Toast
                        .makeText(
                            this,
                            R.string.profile_avatar_picker_error,
                            Toast.LENGTH_SHORT,
                        ).show()
                }
            }
        }

    internal var selectedBirthDateIso: String? = null
    internal var pendingAvatarUpload: ByteArray? = null

    internal lateinit var avatarCircleView: ImageView
    internal lateinit var avatarSquareView: ImageView
    private lateinit var profileScrollView: View
    private lateinit var loadingContainer: View
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var middleNameInput: TextInputEditText
    private lateinit var lastNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var languageInput: MaterialAutoCompleteTextView
    private lateinit var levelInput: MaterialAutoCompleteTextView
    private lateinit var genderGroup: RadioGroup

    internal val userProfileDatabase: UserProfileDatabase by lazy {
        UserProfileDatabase.getInstance(applicationContext)
    }

    internal val httpClient: OkHttpClient by lazy { OkHttpClient() }

    internal val userProfileSync: UserProfileSync by lazy {
        UserProfileSync(httpClient, userProfileDatabase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

        val root: View = findViewById(R.id.profileRoot)
        val toolbar: MaterialToolbar = findViewById(R.id.profileToolbar)
        val scrollView: View = findViewById(R.id.profileScroll)
        profileScrollView = scrollView
        loadingContainer = findViewById(R.id.profileLoadingContainer)
        showLoading(true)

        setupWindowInsets(root, toolbar, scrollView)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        bindViews()
        setupListeners()

        val languageOptions = getLanguageOptions()
        setupLanguageDropdown(languageOptions)

        loadProfileData(languageOptions)
    }

    private fun setupWindowInsets(
        root: View,
        toolbar: MaterialToolbar,
        scrollView: View,
    ) {
        val toolbarPadding = Padding(toolbar.paddingLeft, toolbar.paddingTop, toolbar.paddingRight)
        val scrollPadding = Padding(scrollView.paddingLeft, scrollView.paddingTop, scrollView.paddingRight, scrollView.paddingBottom)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(
                left = toolbarPadding.left + systemBars.left,
                top = toolbarPadding.top + systemBars.top,
                right = toolbarPadding.right + systemBars.right,
            )
            scrollView.updatePadding(
                left = scrollPadding.left + systemBars.left,
                right = scrollPadding.right + systemBars.right,
                bottom = scrollPadding.bottom + systemBars.bottom,
            )
            insets
        }
    }

    private fun bindViews() {
        firstNameInput = findViewById(R.id.profileFirstNameInput)
        middleNameInput = findViewById(R.id.profileMiddleNameInput)
        lastNameInput = findViewById(R.id.profileLastNameInput)
        emailInput = findViewById(R.id.profileEmailInput)
        phoneInput = findViewById(R.id.profilePhoneInput)
        genderGroup = findViewById(R.id.profileGenderGroup)
        languageInput = findViewById(R.id.profileLanguageInput)
        levelInput = findViewById(R.id.profileLevelInput)
        avatarCircleView = findViewById(R.id.profileAvatarPreviewCircle)
        avatarSquareView = findViewById(R.id.profileAvatarPreviewSquare)
    }

    private fun setupListeners() {
        val changeAvatarButton: View = findViewById(R.id.profileChangeAvatarButton)
        val saveButton: MaterialButton = findViewById(R.id.profileSaveButton)
        val birthDateInput: TextInputEditText = findViewById(R.id.profileBirthDateInput)

        val avatarClickListener =
            View.OnClickListener {
                launchAvatarPicker()
            }
        changeAvatarButton.setOnClickListener(avatarClickListener)
        avatarCircleView.setOnClickListener(avatarClickListener)
        avatarSquareView.setOnClickListener(avatarClickListener)

        saveButton.setOnClickListener {
            lifecycleScope.launch {
                val formValues = collectFormValues()
                saveButton.isEnabled = false
                showLoading(true)
                val success =
                    try {
                        submitProfileUpdates(formValues)
                    } finally {
                        showLoading(false)
                        saveButton.isEnabled = true
                    }
                if (success) {
                    selectedBirthDateIso = formValues.birthDateIso
                }
                val toastMessage =
                    if (success) {
                        R.string.profile_save_success_toast
                    } else {
                        R.string.profile_save_failed_toast
                    }
                Toast.makeText(this@ProfileActivity, toastMessage, Toast.LENGTH_LONG).show()
            }
        }

        birthDateInput.setOnClickListener {
            showBirthDatePicker(birthDateInput)
        }
        birthDateInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showBirthDatePicker(birthDateInput)
            }
        }
    }

    private fun getLanguageOptions(): List<LanguageOption> =
        listOf(
            LanguageOption("en", R.string.language_name_english, R.array.signup_level_options_language_en),
            LanguageOption("es", R.string.language_name_spanish, R.array.signup_level_options_language_es),
            LanguageOption("fr", R.string.language_name_french, R.array.signup_level_options_language_fr),
            LanguageOption("pt", R.string.language_name_portuguese, R.array.signup_level_options_language_pt),
            LanguageOption("ar", R.string.language_name_arabic, R.array.signup_level_options_language_ar),
            LanguageOption("so", R.string.language_name_somali, R.array.signup_level_options_language_so),
            LanguageOption("ne", R.string.language_name_nepali, R.array.signup_level_options_language_ne),
            LanguageOption("hi", R.string.language_name_hindi, R.array.signup_level_options_language_hi),
        )

    private fun setupLanguageDropdown(languageOptions: List<LanguageOption>) {
        val languageLabels = languageOptions.map { getString(it.labelRes) }
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, languageLabels)
        languageInput.setAdapter(languageAdapter)

        val currentLang = Locale.getDefault().language.lowercase(Locale.ROOT)
        val initialLevelRes =
            languageOptions.find { it.languageTag == currentLang }?.levelArrayRes
                ?: R.array.signup_level_options_language_en
        val defaultLevelOptions = resources.getStringArray(initialLevelRes).toMutableList()
        val levelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, defaultLevelOptions)
        levelInput.setAdapter(levelAdapter)

        languageInput.setOnItemClickListener { _, _, position, _ ->
            applyLanguage(languageOptions[position], levelInput.text?.toString(), levelAdapter)
        }
    }

    private fun applyLanguage(
        option: LanguageOption,
        existingLevel: String?,
        levelAdapter: ArrayAdapter<String>,
    ) {
        val label = getString(option.labelRes)
        if (languageInput.text?.toString() != label) {
            languageInput.setText(label, false)
        }
        val newLevels = resources.getStringArray(option.levelArrayRes)
        levelAdapter.clear()
        levelAdapter.addAll(newLevels.asList())
        levelAdapter.notifyDataSetChanged()

        val localizedLevel =
            LearningLevelTranslator.toLocalized(
                this@ProfileActivity,
                existingLevel,
                option.levelArrayRes,
            )
        if (localizedLevel.isNullOrBlank()) {
            levelInput.setText("", false)
        } else {
            levelInput.setText(localizedLevel, false)
        }
    }

    private fun loadProfileData(languageOptions: List<LanguageOption>) {
        val usernameView: TextView = findViewById(R.id.profileUsernameValue)
        val birthDateInput: TextInputEditText = findViewById(R.id.profileBirthDateInput)

        lifecycleScope.launch {
            showLoading(true)
            val refreshed = refreshProfileFromServer()
            val profile =
                withContext(Dispatchers.IO) {
                    userProfileDatabase.getProfile()
                }

            usernameView.text = profile?.username?.let {
                getString(R.string.dashboard_profile_username_format, it)
            } ?: getString(R.string.dashboard_profile_username_placeholder)

            firstNameInput.setText(profile?.firstName.orEmpty())
            middleNameInput.setText(profile?.middleName.orEmpty())
            lastNameInput.setText(profile?.lastName.orEmpty())
            emailInput.setText(profile?.email.orEmpty())
            phoneInput.setText(profile?.phoneNumber.orEmpty())
            selectedBirthDateIso = normalizeBirthDateIso(profile?.birthDate)
            birthDateInput.setText(formatBirthDate(selectedBirthDateIso))

            applyAvatarBitmap(decodeAvatarBytes(profile?.avatarImage))

            val normalizedGender = profile?.gender?.trim()?.lowercase(Locale.ROOT)
            when {
                normalizedGender.isNullOrEmpty() -> {
                    genderGroup.clearCheck()
                }

                normalizedGender == GENDER_FEMALE ||
                    normalizedGender == getString(R.string.signup_gender_option_female).lowercase(Locale.ROOT) -> {
                    genderGroup.check(R.id.profileGenderFemale)
                }

                normalizedGender == GENDER_MALE ||
                    normalizedGender == getString(R.string.signup_gender_option_male).lowercase(Locale.ROOT) -> {
                    genderGroup.check(R.id.profileGenderMale)
                }

                else -> {
                    genderGroup.clearCheck()
                }
            }

            val languageValue = profile?.language.orEmpty()
            val normalizedLanguage = languageValue.trim().lowercase(Locale.ROOT)
            val matchedLanguage =
                languageOptions.firstOrNull { option ->
                    normalizedLanguage == option.languageTag.lowercase(Locale.ROOT) ||
                        normalizedLanguage == getString(option.labelRes).trim().lowercase(Locale.ROOT)
                }

            if (matchedLanguage != null) {
                @Suppress("UNCHECKED_CAST")
                val currentLevelAdapter = levelInput.adapter as? ArrayAdapter<String>
                if (currentLevelAdapter != null) {
                    applyLanguage(matchedLanguage, profile?.level, currentLevelAdapter)
                }
            } else {
                languageInput.setText(languageValue, false)
                val englishLevel = LearningLevelTranslator.toEnglish(this@ProfileActivity, profile?.level)
                levelInput.setText(englishLevel.orEmpty(), false)
            }

            showLoading(false)

            if (!refreshed) {
                Toast
                    .makeText(
                        this@ProfileActivity,
                        R.string.profile_refresh_failed_toast,
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingContainer.isVisible = show
        profileScrollView.isVisible = !show
    }

    private fun collectFormValues(): ProfileFormValues {
        val genderValue =
            when (genderGroup.checkedRadioButtonId) {
                R.id.profileGenderMale -> GENDER_MALE
                R.id.profileGenderFemale -> GENDER_FEMALE
                else -> null
            }

        val birthDateIso = normalizeBirthDateIso(selectedBirthDateIso)

        return ProfileFormValues(
            firstName =
                firstNameInput.text
                    ?.toString()
                    ?.trim()
                    .nullIfBlank(),
            middleName =
                middleNameInput.text
                    ?.toString()
                    ?.trim()
                    .orEmpty(),
            lastName =
                lastNameInput.text
                    ?.toString()
                    ?.trim()
                    .nullIfBlank(),
            email =
                emailInput.text
                    ?.toString()
                    ?.trim()
                    .nullIfBlank(),
            phoneNumber =
                phoneInput.text
                    ?.toString()
                    ?.trim()
                    .nullIfBlank(),
            birthDateIso = birthDateIso,
            birthYear = extractBirthYearFromIso(birthDateIso),
            age = calculateAgeFromIso(birthDateIso),
            gender = genderValue,
            language =
                languageInput.text
                    ?.toString()
                    ?.trim()
                    .nullIfBlank(),
            level =
                LearningLevelTranslator
                    .toEnglish(this, levelInput.text?.toString()?.trim())
                    .nullIfBlank(),
        )
    }

    private suspend fun refreshProfileFromServer(): Boolean = refreshProfileFromServerImpl()

    internal suspend fun executeProfileUpdateRequest(
        normalizedBase: String,
        username: String,
        nonNullCookie: String,
        document: JSONObject,
        avatarUploadBytes: ByteArray?,
        avatarBytesToPersist: ByteArray?,
    ): Boolean = executeProfileUpdateRequestImpl(
        normalizedBase,
        username,
        nonNullCookie,
        document,
        avatarUploadBytes,
        avatarBytesToPersist,
    )

    private suspend fun fetchRemoteProfileDocument(
        serverBaseUrl: String,
        username: String,
        sessionCookie: String,
    ): JSONObject? = fetchRemoteProfileDocumentImpl(serverBaseUrl, username, sessionCookie)

    private suspend fun submitProfileUpdates(formValues: ProfileFormValues): Boolean = submitProfileUpdatesImpl(formValues)

    private fun formatBirthDate(raw: String?): String = formatBirthDateImpl(raw)

    private fun showBirthDatePicker(targetView: TextInputEditText) = showBirthDatePickerImpl(targetView)

    private fun normalizeBirthDateIso(raw: String?): String? = normalizeBirthDateIsoImpl(raw)

    private fun extractBirthYearFromIso(iso: String?): String? = extractBirthYearFromIsoImpl(iso)

    private fun calculateAgeFromIso(iso: String?): String? = calculateAgeFromIsoImpl(iso)

    private fun launchAvatarPicker() = launchAvatarPickerImpl()

    private fun processAvatarSelection(uri: Uri) = processAvatarSelectionImpl(uri)

    private fun handleCroppedAvatar(uri: Uri) = handleCroppedAvatarImpl(uri)

    private fun applyAvatarBitmap(
        bitmap: Bitmap?,
        markForUpload: Boolean = false,
    ) = applyAvatarBitmapImpl(bitmap, markForUpload)

    private fun decodeAvatarBytes(bytes: ByteArray?): Bitmap? = decodeAvatarBytesImpl(bytes)

    private data class Padding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int = 0,
    )
}

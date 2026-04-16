/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite.profile

import org.ole.planet.myplanet.lite.BaseActivity
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.yalantis.ucrop.UCrop
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ole.planet.myplanet.lite.BuildConfig
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.model.LanguageOption
import org.ole.planet.myplanet.lite.util.nullIfBlank

class ProfileActivity : BaseActivity() {

    private val selectAvatarLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processAvatarSelection(it) }
    }

    private val cropAvatarLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        when (result.resultCode) {
            RESULT_OK -> {
                val croppedUri = result.data?.let { UCrop.getOutput(it) }
                if (croppedUri != null) {
                    handleCroppedAvatar(croppedUri)
                } else {
                    Toast.makeText(
                        this,
                        R.string.profile_avatar_picker_error,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            UCrop.RESULT_ERROR -> {
                val error = result.data?.let { UCrop.getError(it) }
                Toast.makeText(
                    this,
                    R.string.profile_avatar_picker_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private var selectedBirthDateIso: String? = null
    private var pendingAvatarUpload: ByteArray? = null

    private lateinit var avatarCircleView: ImageView
    private lateinit var avatarSquareView: ImageView
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

    private val userProfileDatabase: UserProfileDatabase by lazy {
        UserProfileDatabase.getInstance(applicationContext)
    }

    private val httpClient: OkHttpClient by lazy { OkHttpClient() }

    private val userProfileSync: UserProfileSync by lazy {
        UserProfileSync(httpClient, userProfileDatabase)
    }

    private data class ProfileFormValues(
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val email: String?,
        val phoneNumber: String?,
        val birthDateIso: String?,
        val birthYear: String?,
        val age: String?,
        val gender: String?,
        val language: String?,
        val level: String?
    )

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

    private fun setupWindowInsets(root: View, toolbar: MaterialToolbar, scrollView: View) {
        val toolbarPadding = Padding(toolbar.paddingLeft, toolbar.paddingTop, toolbar.paddingRight)
        val scrollPadding = Padding(scrollView.paddingLeft, scrollView.paddingTop, scrollView.paddingRight, scrollView.paddingBottom)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(
                left = toolbarPadding.left + systemBars.left,
                top = toolbarPadding.top + systemBars.top,
                right = toolbarPadding.right + systemBars.right
            )
            scrollView.updatePadding(
                left = scrollPadding.left + systemBars.left,
                right = scrollPadding.right + systemBars.right,
                bottom = scrollPadding.bottom + systemBars.bottom
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

        val avatarClickListener = View.OnClickListener {
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
                val success = try {
                    submitProfileUpdates(formValues)
                } finally {
                    showLoading(false)
                    saveButton.isEnabled = true
                }
                if (success) {
                    selectedBirthDateIso = formValues.birthDateIso
                }
                val toastMessage = if (success) {
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

    private fun getLanguageOptions(): List<LanguageOption> {
        return listOf(
            LanguageOption("en", R.string.language_name_english, R.array.signup_level_options_language_en),
            LanguageOption("es", R.string.language_name_spanish, R.array.signup_level_options_language_es),
            LanguageOption("fr", R.string.language_name_french, R.array.signup_level_options_language_fr),
            LanguageOption("pt", R.string.language_name_portuguese, R.array.signup_level_options_language_pt),
            LanguageOption("ar", R.string.language_name_arabic, R.array.signup_level_options_language_ar),
            LanguageOption("so", R.string.language_name_somali, R.array.signup_level_options_language_so),
            LanguageOption("ne", R.string.language_name_nepali, R.array.signup_level_options_language_ne),
            LanguageOption("hi", R.string.language_name_hindi, R.array.signup_level_options_language_hi)
        )
    }

    private fun setupLanguageDropdown(languageOptions: List<LanguageOption>) {
        val languageLabels = languageOptions.map { getString(it.labelRes) }
        val languageAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, languageLabels)
        languageInput.setAdapter(languageAdapter)

        val currentLang = Locale.getDefault().language.lowercase(Locale.ROOT)
        val initialLevelRes = languageOptions.find { it.languageTag == currentLang }?.levelArrayRes
            ?: R.array.signup_level_options_language_en
        val defaultLevelOptions = resources.getStringArray(initialLevelRes).toMutableList()
        val levelAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, defaultLevelOptions)
        levelInput.setAdapter(levelAdapter)

        languageInput.setOnItemClickListener { _, _, position, _ ->
            applyLanguage(languageOptions[position], levelInput.text?.toString(), levelAdapter)
        }
    }

    private fun applyLanguage(option: LanguageOption, existingLevel: String?, levelAdapter: ArrayAdapter<String>) {
        val label = getString(option.labelRes)
        if (languageInput.text?.toString() != label) {
            languageInput.setText(label, false)
        }
        val newLevels = resources.getStringArray(option.levelArrayRes)
        levelAdapter.clear()
        levelAdapter.addAll(newLevels.asList())
        levelAdapter.notifyDataSetChanged()

        val localizedLevel = LearningLevelTranslator.toLocalized(
            this@ProfileActivity,
            existingLevel,
            option.levelArrayRes
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
            val profile = withContext(Dispatchers.IO) {
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
            selectedBirthDateIso = profile?.birthDate
            birthDateInput.setText(formatBirthDate(selectedBirthDateIso))

            applyAvatarBitmap(decodeAvatarBytes(profile?.avatarImage))

            val normalizedGender = profile?.gender?.trim()?.lowercase(Locale.ROOT)
            when {
                normalizedGender.isNullOrEmpty() -> genderGroup.clearCheck()
                normalizedGender == GENDER_FEMALE ||
                    normalizedGender == getString(R.string.signup_gender_option_female).lowercase(Locale.ROOT) -> {
                    genderGroup.check(R.id.profileGenderFemale)
                }
                normalizedGender == GENDER_MALE ||
                    normalizedGender == getString(R.string.signup_gender_option_male).lowercase(Locale.ROOT) -> {
                    genderGroup.check(R.id.profileGenderMale)
                }
                else -> genderGroup.clearCheck()
            }

            val languageValue = profile?.language.orEmpty()
            val normalizedLanguage = languageValue.trim().lowercase(Locale.ROOT)
            val matchedLanguage = languageOptions.firstOrNull { option ->
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
                Toast.makeText(
                    this@ProfileActivity,
                    R.string.profile_refresh_failed_toast,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun showLoading(show: Boolean) {
        loadingContainer.isVisible = show
        profileScrollView.isVisible = !show
    }

    private fun collectFormValues(): ProfileFormValues {
        val genderValue = when (genderGroup.checkedRadioButtonId) {
            R.id.profileGenderMale -> GENDER_MALE
            R.id.profileGenderFemale -> GENDER_FEMALE
            else -> null
        }

        val birthDateIso = selectedBirthDateIso

        return ProfileFormValues(
            firstName = firstNameInput.text?.toString()?.trim().nullIfBlank(),
            middleName = middleNameInput.text?.toString()?.trim().orEmpty(),
            lastName = lastNameInput.text?.toString()?.trim().nullIfBlank(),
            email = emailInput.text?.toString()?.trim().nullIfBlank(),
            phoneNumber = phoneInput.text?.toString()?.trim().nullIfBlank(),
            birthDateIso = birthDateIso,
            birthYear = extractBirthYearFromIso(birthDateIso),
            age = calculateAgeFromIso(birthDateIso),
            gender = genderValue,
            language = languageInput.text?.toString()?.trim().nullIfBlank(),
            level = LearningLevelTranslator
                .toEnglish(this, levelInput.text?.toString()?.trim())
                .nullIfBlank()
        )
    }

    private suspend fun refreshProfileFromServer(): Boolean {
        val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
        val sanitizedBase = (storedBaseUrl ?: BuildConfig.PLANET_BASE_URL).trim()
        if (sanitizedBase.isEmpty()) {
            return false
        }
        val normalizedBase = sanitizedBase.trimEnd('/')

        val authService = AuthDependencies.provideAuthService(this, sanitizedBase)
        val storedCredentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
        var username = storedCredentials?.username
        if (username.isNullOrBlank()) {
            username = withContext(Dispatchers.IO) {
                userProfileDatabase.getProfile()?.username
            }
        }

        var sessionCookie = authService.getStoredToken()

        if (storedCredentials != null) {
            when (val loginResult = authService.login(storedCredentials.username, storedCredentials.password)) {
                is AuthResult.Success -> {
                    sessionCookie = loginResult.response.sessionCookie ?: sessionCookie
                    if (username.isNullOrBlank()) {
                        username = loginResult.response.name ?: storedCredentials.username
                    }
                }
                is AuthResult.Error, is AuthResult.Failure -> Unit
            }
        }

        if (username.isNullOrBlank() || sessionCookie.isNullOrBlank()) {
            return false
        }

        return userProfileSync.refreshProfile(normalizedBase, username.orEmpty(), sessionCookie)
    }

    private suspend fun submitProfileUpdates(formValues: ProfileFormValues): Boolean {
        val storedProfile = withContext(Dispatchers.IO) { userProfileDatabase.getProfile() }
        val storedBaseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
        val sanitizedBase = (storedBaseUrl ?: BuildConfig.PLANET_BASE_URL).trim()
        if (sanitizedBase.isEmpty()) {
            return false
        }
        val normalizedBase = sanitizedBase.trimEnd('/')
        var username = storedProfile?.username
        val storedCredentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
        if (username.isNullOrBlank()) {
            username = storedCredentials?.username
        }
        if (username.isNullOrBlank()) {
            return false
        }

        val authService = AuthDependencies.provideAuthService(this, sanitizedBase)
        var sessionCookie = authService.getStoredToken()
        if (storedCredentials != null) {
            when (val loginResult = authService.login(storedCredentials.username, storedCredentials.password)) {
                is AuthResult.Success -> {
                    sessionCookie = loginResult.response.sessionCookie ?: sessionCookie
                }

                is AuthResult.Error, is AuthResult.Failure -> {
                    if (sessionCookie.isNullOrBlank()) {
                        return false
                    }
                }
            }
        }

        val nonNullCookie = sessionCookie.nullIfBlank() ?: return false
        val avatarUploadBytes = pendingAvatarUpload
        val document = buildUpdatedProfileDocument(
            normalizedBase,
            username,
            nonNullCookie,
            formValues,
            storedProfile,
            avatarUploadBytes
        ) ?: return false

        val avatarBytesToPersist = avatarUploadBytes ?: storedProfile?.avatarImage

        val success = withContext(Dispatchers.IO) {
            try {
                val requestBody = document.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/_users/org.couchdb.user:$username")
                    .put(requestBody)
                    .addHeader("Cookie", nonNullCookie)
                    .addHeader("Content-Type", "application/json")

                httpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext false
                    }
                    val responseBody = response.body.string()
                    val newRevision = responseBody.nullIfBlank()
                        ?.let { JSONObject(it) }
                        ?.optString("rev")
                        .nullIfBlank()
                    newRevision?.let { document.put("_rev", it) }
                    if (avatarUploadBytes != null) {
                        replaceAvatarAttachmentWithStub(document, avatarUploadBytes, newRevision)
                    }
                    saveUpdatedProfileLocally(document, avatarBytesToPersist, username)
                    true
                }
            } catch (error: Exception) {
                false
            }
        }

        if (success && avatarUploadBytes != null) {
            pendingAvatarUpload = null
            AvatarUpdateNotifier.notifyAvatarUpdated(username)
        }

        return success
    }

    private suspend fun buildUpdatedProfileDocument(
        serverBaseUrl: String,
        username: String,
        sessionCookie: String,
        formValues: ProfileFormValues,
        storedProfile: UserProfile?,
        avatarUploadBytes: ByteArray?
    ): JSONObject? {
        val baseDocument = storedProfile?.rawDocument?.nullIfBlank()?.let { JSONObject(it) }
            ?: fetchRemoteProfileDocument(serverBaseUrl, username, sessionCookie)
            ?: return null

        val latestRevision = storedProfile?.revision ?: baseDocument.optString("_rev").nullIfBlank()
        latestRevision?.let { baseDocument.put("_rev", it) }
        val derivedKey = storedProfile?.derivedKey ?: baseDocument.optString("derived_key").nullIfBlank()
        derivedKey?.let { baseDocument.put("derived_key", it) }
        baseDocument.put("_id", baseDocument.optString("_id").nullIfBlank() ?: "org.couchdb.user:$username")
        baseDocument.put("name", username)
        baseDocument.put("type", baseDocument.optString("type").nullIfBlank() ?: "user")

        applyFormValuesToDocument(baseDocument, formValues)

        if (avatarUploadBytes != null) {
            val attachments = baseDocument.optJSONObject("_attachments") ?: JSONObject()
            val avatarAttachment = JSONObject().apply {
                put("content_type", AVATAR_CONTENT_TYPE)
                put("data", Base64.encodeToString(avatarUploadBytes, Base64.NO_WRAP))
            }
            attachments.put(AVATAR_ATTACHMENT_KEY, avatarAttachment)
            baseDocument.put("_attachments", attachments)
        }

        return baseDocument
    }

    private suspend fun fetchRemoteProfileDocument(
        serverBaseUrl: String,
        username: String,
        sessionCookie: String
    ): JSONObject? {
        val profileUrl = "$serverBaseUrl/db/_users/org.couchdb.user:$username"
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(profileUrl)
                    .get()
                    .addHeader("Cookie", sessionCookie)
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext null
                    }
                    val body = response.body.string()
                    if (body.isNullOrBlank()) {
                        return@withContext null
                    }
                    JSONObject(body)
                }
            } catch (error: Exception) {
                null
            }
        }
    }

    private fun applyFormValuesToDocument(document: JSONObject, values: ProfileFormValues) {
        document.putOrRemove("firstName", values.firstName)
        document.put("middleName", values.middleName.orEmpty())
        document.putOrRemove("lastName", values.lastName)
        document.putOrRemove("email", values.email)
        document.putOrRemove("phoneNumber", values.phoneNumber)
        document.putOrRemove("gender", values.gender)
        document.putOrRemove("language", values.language)
        document.putOrRemove("level", values.level)

        if (values.birthDateIso != null) {
            document.put("birthDate", values.birthDateIso)
        } else {
            document.remove("birthDate")
        }

        if (values.birthYear != null) {
            document.put("birthYear", values.birthYear)
        } else {
            document.remove("birthYear")
        }

        if (values.age != null) {
            document.put("age", values.age)
        } else {
            document.remove("age")
        }
    }

    private suspend fun saveUpdatedProfileLocally(
        document: JSONObject,
        avatarBytes: ByteArray?,
        usernameFallback: String
    ) {
        val resolvedUsername = document.optString("name").nullIfBlank() ?: usernameFallback
        val updatedProfile = UserProfile(
            username = resolvedUsername,
            firstName = document.optString("firstName").nullIfBlank(),
            middleName = document.optString("middleName").nullIfBlank(),
            lastName = document.optString("lastName").nullIfBlank(),
            email = document.optString("email").nullIfBlank(),
            language = document.optString("language").nullIfBlank(),
            phoneNumber = document.optString("phoneNumber").nullIfBlank(),
            birthDate = document.optString("birthDate").nullIfBlank(),
            gender = document.optString("gender").nullIfBlank(),
            level = document.optString("level").nullIfBlank(),
            avatarImage = avatarBytes,
            revision = document.optString("_rev").nullIfBlank(),
            derivedKey = document.optString("derived_key").nullIfBlank(),
            rawDocument = document.toString(),
            isUserAdmin = document.optBoolean("isUserAdmin", false)
        )

        withContext(Dispatchers.IO) {
            userProfileDatabase.saveProfile(updatedProfile)
        }
    }

    private fun replaceAvatarAttachmentWithStub(
        document: JSONObject,
        avatarBytes: ByteArray,
        revision: String?
    ) {
        val attachments = document.optJSONObject("_attachments") ?: JSONObject()
        val stubInfo = JSONObject().apply {
            put("content_type", AVATAR_CONTENT_TYPE)
            put("length", avatarBytes.size)
            put("digest", avatarBytes.toMd5DigestHeader())
            put("stub", true)
            revision?.substringBefore('-')?.toIntOrNull()?.let { put("revpos", it) }
        }
        attachments.put(AVATAR_ATTACHMENT_KEY, stubInfo)
        document.put("_attachments", attachments)
    }

    private fun ByteArray.toMd5DigestHeader(): String {
        val digest = MessageDigest.getInstance("MD5").digest(this)
        val encoded = Base64.encodeToString(digest, Base64.NO_WRAP)
        return "md5-$encoded"
    }

    private fun formatBirthDate(raw: String?): String {
        if (raw.isNullOrBlank()) {
            return ""
        }
        val targetPattern = "MMM d, yyyy"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val instant = runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()

            if (instant != null) {
                val zonedDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
                val formatter = DateTimeFormatter.ofPattern(targetPattern, Locale.getDefault())
                zonedDate.format(formatter)
            } else {
                raw
            }
        } else {
            val inputPatterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd"
            )
            val outputFormat = SimpleDateFormat(targetPattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            inputPatterns.firstNotNullOfOrNull { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(raw)
                }.getOrNull()?.let { date ->
                    outputFormat.format(date)
                }
            } ?: raw
        }
    }

    private fun showBirthDatePicker(targetView: TextInputEditText) {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.profile_birth_date_picker_title)
            .setSelection(parseBirthDateToMillis(selectedBirthDateIso) ?: MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val iso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Instant.ofEpochMilli(selection).atZone(ZoneOffset.UTC).toInstant().toString()
            } else {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                format.format(Date(selection))
            }
            selectedBirthDateIso = iso
            targetView.setText(formatBirthDate(iso))
        }

        picker.show(supportFragmentManager, "birthDatePicker")
    }

    private fun parseBirthDateToMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) {
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
                    .getOrNull()
        } else {
            val patterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd"
            )
            patterns.firstNotNullOfOrNull { pattern ->
                runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(raw)?.time
                }.getOrNull()
            }
        }
    }

    private fun extractBirthYearFromIso(iso: String?): String? {
        if (iso.isNullOrBlank()) {
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                Instant.parse(iso).atZone(ZoneOffset.UTC).year.toString()
            }.getOrNull()
        } else {
            runCatching {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = format.parse(iso) ?: return@runCatching null
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.time = date
                calendar.get(Calendar.YEAR).toString()
            }.getOrNull()
        }
    }

    private fun calculateAgeFromIso(iso: String?): String? {
        if (iso.isNullOrBlank()) {
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val birthDate = Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
                val now = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
                Period.between(birthDate, now).years.takeIf { it >= 0 }?.toString()
            }.getOrNull()
        } else {
            runCatching {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = format.parse(iso) ?: return@runCatching null
                val birthCalendar = Calendar.getInstance().apply { time = date }
                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                    age -= 1
                }
                age.takeIf { it >= 0 }?.toString()
            }.getOrNull()
        }
    }

    private fun launchAvatarPicker() {
        selectAvatarLauncher.launch("image/*")
    }

    private fun processAvatarSelection(uri: Uri) {
        val destinationFile = runCatching { File.createTempFile("avatar_crop", ".jpg", cacheDir) }.getOrNull()
        if (destinationFile == null) {
            Toast.makeText(this, R.string.profile_avatar_picker_error, Toast.LENGTH_SHORT).show()
            return
        }
        val destinationUri = Uri.fromFile(destinationFile)
        val options = UCrop.Options().apply {
            setToolbarTitle(getString(R.string.profile_avatar_crop_title))
            setHideBottomControls(true)
            setFreeStyleCropEnabled(false)
            val white = resources.getColor(R.color.white, theme)
            val blue = resources.getColor(R.color.blueOle, theme)
            setToolbarColor(white)
            setStatusBarColor(white)
            setToolbarWidgetColor(blue)
            setActiveControlsWidgetColor(blue)
        }
        val targetSize = resources.getDimensionPixelSize(R.dimen.profile_avatar_max_image_size).takeIf { it > 0 } ?: 512
        val intent = UCrop.of(uri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(targetSize, targetSize)
            .withOptions(options)
            .getIntent(this)
        val canHandleCrop =
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
        if (!canHandleCrop) {
            destinationFile.delete()
            Toast.makeText(this, R.string.profile_avatar_picker_error, Toast.LENGTH_SHORT).show()
            return
        }
        cropAvatarLauncher.launch(intent)
    }

    private fun handleCroppedAvatar(uri: Uri) {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                loadScaledAvatar(uri)
            }
            if (bitmap != null) {
                applyAvatarBitmap(bitmap, markForUpload = true)
            } else {
                Toast.makeText(
                    this@ProfileActivity,
                    R.string.profile_avatar_picker_error,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun loadScaledAvatar(uri: Uri): Bitmap? {
        return try {
            val resolver = contentResolver
            val targetSize = resources.getDimensionPixelSize(R.dimen.profile_avatar_max_image_size).takeIf { it > 0 }
                ?: 512
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            val decoded = resolver.openInputStream(uri)?.use { input ->
                val bufferedInput = BufferedInputStream(input)
                bufferedInput.mark(8 * 1024 * 1024) // 8MB mark limit for decode bounds
                BitmapFactory.decodeStream(bufferedInput, null, boundsOptions)
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(boundsOptions, targetSize, targetSize)
                }
                try {
                    bufferedInput.reset()
                    BitmapFactory.decodeStream(bufferedInput, null, decodeOptions)
                } catch (e: IOException) {
                    // Fallback to reopening the stream if reset fails
                    resolver.openInputStream(uri)?.use { fallbackInput ->
                        BitmapFactory.decodeStream(fallbackInput, null, decodeOptions)
                    }
                }
            } ?: return null
            val square = cropToSquare(decoded)
            if (square !== decoded) {
                decoded.recycle()
            }
            if (square.width > targetSize) {
                Bitmap.createScaledBitmap(square, targetSize, targetSize, true).also { scaled ->
                    if (scaled !== square) {
                        square.recycle()
                    }
                }
            } else {
                square
            }
        } catch (ex: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        val height = options.outHeight
        val width = options.outWidth
        if (height > reqHeight || width > reqWidth) {
            var halfHeight = height / 2
            var halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun cropToSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val xOffset = (bitmap.width - size) / 2
        val yOffset = (bitmap.height - size) / 2
        return Bitmap.createBitmap(bitmap, xOffset, yOffset, size, size)
    }

    private fun applyAvatarBitmap(bitmap: Bitmap?, markForUpload: Boolean = false) {
        if (bitmap != null) {
            if (markForUpload) {
                pendingAvatarUpload = compressAvatar(bitmap)
            }
            avatarCircleView.setImageBitmap(bitmap)
            avatarSquareView.setImageBitmap(bitmap)
        } else {
            if (markForUpload) {
                pendingAvatarUpload = null
            }
            avatarCircleView.setImageDrawable(null)
            avatarSquareView.setImageDrawable(null)
        }
    }

    private fun decodeAvatarBytes(bytes: ByteArray?): Bitmap? {
        if (bytes == null || bytes.isEmpty()) {
            return null
        }
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (ex: Exception) {
            null
        }
    }

    private fun compressAvatar(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }



    private fun JSONObject.putOrRemove(key: String, value: String?) {
        if (value.isNullOrBlank()) {
            remove(key)
        } else {
            put(key, value)
        }
    }


    private companion object {
        private const val AVATAR_ATTACHMENT_KEY = "img"
        private const val AVATAR_CONTENT_TYPE = "image/jpeg"
    }

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int = 0)
}

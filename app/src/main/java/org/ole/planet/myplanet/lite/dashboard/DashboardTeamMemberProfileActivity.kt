/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite.dashboard

import org.ole.planet.myplanet.lite.BaseActivity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import java.util.Locale
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.profile.GenderTranslator
import org.ole.planet.myplanet.lite.profile.LearningLevelTranslator
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.DateUtils

class DashboardTeamMemberProfileActivity : BaseActivity() {

    private lateinit var avatarView: ImageView
    private lateinit var nameView: TextView
    private lateinit var usernameView: TextView
    private lateinit var roleView: TextView
    private lateinit var identityNameView: TextView
    private lateinit var identityUsernameView: TextView
    private lateinit var identityGenderView: TextView
    private lateinit var identityBirthDateView: TextView
    private lateinit var contactEmailView: TextView
    private lateinit var contactPhoneView: TextView
    private lateinit var contactEmailActionView: View
    private lateinit var contactPhoneActionView: View
    private lateinit var preferencesLanguageView: TextView
    private lateinit var preferencesLevelView: TextView
    private lateinit var loadingView: View
    private lateinit var contentView: View
    private lateinit var errorView: TextView

    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var credentials: StoredCredentials? = null
    private var avatarLoader: DashboardAvatarLoader? = null

    private val repository = DashboardTeamsRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        setContentView(R.layout.activity_dashboard_team_member_profile)

        val toolbar: MaterialToolbar = findViewById(R.id.teamMemberProfileToolbar)
        toolbar.setTitle(R.string.dashboard_team_member_profile_title)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        avatarView = findViewById(R.id.teamMemberProfileAvatar)
        nameView = findViewById(R.id.teamMemberProfileName)
        usernameView = findViewById(R.id.teamMemberProfileUsername)
        roleView = findViewById(R.id.teamMemberProfileRole)
        identityNameView = findViewById(R.id.teamMemberProfileIdentityName)
        identityUsernameView = findViewById(R.id.teamMemberProfileIdentityUsername)
        identityGenderView = findViewById(R.id.teamMemberProfileIdentityGender)
        identityBirthDateView = findViewById(R.id.teamMemberProfileIdentityBirthDate)
        contactEmailView = findViewById(R.id.teamMemberProfileContactEmail)
        contactPhoneView = findViewById(R.id.teamMemberProfileContactPhone)
        contactEmailActionView = findViewById(R.id.teamMemberProfileContactEmailAction)
        contactPhoneActionView = findViewById(R.id.teamMemberProfileContactPhoneAction)
        preferencesLanguageView = findViewById(R.id.teamMemberProfilePreferencesLanguage)
        preferencesLevelView = findViewById(R.id.teamMemberProfilePreferencesLevel)
        loadingView = findViewById(R.id.teamMemberProfileLoading)
        contentView = findViewById(R.id.teamMemberProfileContent)
        errorView = findViewById(R.id.teamMemberProfileError)

        val username = intent.getStringExtra(EXTRA_USERNAME)
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
        val isLeader = intent.getBooleanExtra(EXTRA_IS_LEADER, false)

        if (username.isNullOrBlank()) {
            finish()
            return
        }

        val resolvedName = displayName?.takeIf { it.isNotBlank() } ?: username
        nameView.text = resolvedName
        usernameView.text = getString(R.string.dashboard_team_member_profile_username_format, username)
        roleView.text = getString(
            if (isLeader) R.string.dashboard_team_members_leader_role else R.string.dashboard_team_members_member_role
        )
        identityUsernameView.text = usernameView.text

        lifecycleScope.launch {
            initializeSession()
            loadProfile(username, resolvedName)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        avatarLoader?.destroy()
        avatarLoader = null
    }

    private suspend fun initializeSession() {
        if (!baseUrl.isNullOrBlank() && credentials != null) {
            return
        }
        val appContext = applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(appContext)
        credentials = ProfileCredentialsStore.getStoredCredentials(appContext)
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(appContext, base)
            sessionCookie = authService.getStoredToken()
            if (avatarLoader == null) {
                avatarLoader = DashboardAvatarLoader(base, sessionCookie, credentials, lifecycleScope)
            }
        }
    }

    private suspend fun loadProfile(username: String, fallbackName: String) {
        val base = baseUrl
        if (base.isNullOrBlank()) {
            showError(getString(R.string.dashboard_team_members_missing_server))
            return
        }

        showLoading(true)
        val result = repository.fetchTeamMemberProfileDetails(base, credentials, sessionCookie, username)
        result.onSuccess { profile ->
            showLoading(false)
            contentView.isVisible = true
            bindProfile(profile, fallbackName, username)
        }.onFailure {
            showError(getString(R.string.dashboard_team_member_profile_error))
        }
    }

    private fun bindProfile(profile: DashboardTeamsRepository.TeamMemberProfileDetails, fallbackName: String, username: String) {
        val fullName = profile.fullName ?: fallbackName
        nameView.text = fullName
        identityNameView.text = fullName

        val avatarTarget = avatarView
        avatarTarget.setImageResource(R.drawable.ic_person_placeholder_24)
        val shouldAttemptAvatar = profile.hasAvatar || profile.username.isNotBlank()
        avatarLoader?.bind(avatarTarget, username, shouldAttemptAvatar)

        val usernameLabel = getString(R.string.dashboard_team_member_profile_username_format, profile.username)
        usernameView.text = usernameLabel
        identityUsernameView.text = usernameLabel

        bindContactLink(
            value = profile.email,
            valueView = contactEmailView,
            actionView = contactEmailActionView
        ) { email ->
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:" + Uri.encode(email))
            }
            startActivity(intent)
        }

        bindContactLink(
            value = profile.phoneNumber,
            valueView = contactPhoneView,
            actionView = contactPhoneActionView
        ) { phone ->
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:" + Uri.encode(phone))
            }
            startActivity(intent)
        }

        preferencesLanguageView.text = profile.language ?: getString(R.string.dashboard_team_member_profile_not_available)
        preferencesLevelView.text = resolveLevel(profile.language, profile.level)
        identityGenderView.text = resolveGender(profile.gender)
        identityBirthDateView.text = formatBirthDate(profile.birthDate)
    }

    private fun resolveLevel(language: String?, level: String?): String {
        val unavailable = getString(R.string.dashboard_team_member_profile_not_available)
        val englishLevel = LearningLevelTranslator.toEnglish(this, level) ?: return unavailable
        val arrayRes = levelArrayResForLanguage(language)
        if (arrayRes != null) {
            val localized = LearningLevelTranslator.toLocalized(this, englishLevel, arrayRes)
            if (!localized.isNullOrBlank()) {
                return localized
            }
        }
        return englishLevel
    }

    private fun resolveGender(gender: String?): String {
        val unavailable = getString(R.string.dashboard_team_member_profile_not_available)
        val englishGender = GenderTranslator.toEnglish(this, gender) ?: return unavailable
        val arrayRes = genderArrayResForLanguage(currentAppLanguageTag())
        if (arrayRes != null) {
            val localized = GenderTranslator.toLocalized(this, englishGender, arrayRes)
            if (!localized.isNullOrBlank()) {
                return localized
            }
        }
        return englishGender
    }

    private fun levelArrayResForLanguage(language: String?): Int? {
        val normalized = language?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when (normalized) {
            "english", getString(R.string.language_name_english).lowercase(Locale.ROOT), "en" ->
                R.array.signup_level_options_language_en

            "spanish", "español", getString(R.string.language_name_spanish).lowercase(Locale.ROOT), "es" ->
                R.array.signup_level_options_language_es

            "french", "français", getString(R.string.language_name_french).lowercase(Locale.ROOT), "fr" ->
                R.array.signup_level_options_language_fr

            "portuguese", "português", getString(R.string.language_name_portuguese).lowercase(Locale.ROOT), "pt" ->
                R.array.signup_level_options_language_pt

            "arabic", "العربية", getString(R.string.language_name_arabic).lowercase(Locale.ROOT), "ar" ->
                R.array.signup_level_options_language_ar

            "somali", "soomaali", getString(R.string.language_name_somali).lowercase(Locale.ROOT), "so" ->
                R.array.signup_level_options_language_so

            "nepali", "नेपाली", getString(R.string.language_name_nepali).lowercase(Locale.ROOT), "ne" ->
                R.array.signup_level_options_language_ne

            "hindi", "हिन्दी", getString(R.string.language_name_hindi).lowercase(Locale.ROOT), "hi" ->
                R.array.signup_level_options_language_hi

            else -> null
        }
    }

    private fun genderArrayResForLanguage(languageTag: String?): Int? {
        val normalized = languageTag?.trim()?.lowercase(Locale.ROOT) ?: return null
        return when (normalized) {
            "english", getString(R.string.language_name_english).lowercase(Locale.ROOT), "en" ->
                R.array.signup_gender_options_language_en

            "spanish", "español", getString(R.string.language_name_spanish).lowercase(Locale.ROOT), "es" ->
                R.array.signup_gender_options_language_es

            "french", "français", getString(R.string.language_name_french).lowercase(Locale.ROOT), "fr" ->
                R.array.signup_gender_options_language_fr

            "portuguese", "português", getString(R.string.language_name_portuguese).lowercase(Locale.ROOT), "pt" ->
                R.array.signup_gender_options_language_pt

            "arabic", "العربية", getString(R.string.language_name_arabic).lowercase(Locale.ROOT), "ar" ->
                R.array.signup_gender_options_language_ar

            "somali", "soomaali", getString(R.string.language_name_somali).lowercase(Locale.ROOT), "so" ->
                R.array.signup_gender_options_language_so

            "nepali", "नेपाली", getString(R.string.language_name_nepali).lowercase(Locale.ROOT), "ne" ->
                R.array.signup_gender_options_language_ne

            "hindi", "हिन्दी", getString(R.string.language_name_hindi).lowercase(Locale.ROOT), "hi" ->
                R.array.signup_gender_options_language_hi

            else -> null
        }
    }

    private fun currentAppLanguageTag(): String? {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = appLocales[0] ?: Locale.getDefault()
        return locale.language
    }

    private fun formatBirthDate(value: String?): String {
        return DateUtils.formatBirthDate(
            value = value,
            fallback = getString(R.string.dashboard_team_member_profile_not_available)
        )
    }

    private fun showLoading(loading: Boolean) {
        loadingView.isVisible = loading
        errorView.isVisible = false
    }

    private fun showError(message: String) {
        showLoading(false)
        contentView.isVisible = false
        errorView.text = message
        errorView.isVisible = true
    }

    private fun bindContactLink(
        value: String?,
        valueView: TextView,
        actionView: View,
        launchIntent: (String) -> Unit
    ) {
        val resolvedValue = value?.takeIf { it.isNotBlank() }
        val displayValue = resolvedValue ?: getString(R.string.dashboard_team_member_profile_not_available)

        valueView.text = displayValue
        val canLaunch = resolvedValue != null
        actionView.isVisible = canLaunch
        if (canLaunch) {
            actionView.setOnClickListener { launchIntent(resolvedValue) }
        } else {
            actionView.setOnClickListener(null)
        }
    }

    companion object {
        private const val EXTRA_USERNAME = "extra_username"
        private const val EXTRA_DISPLAY_NAME = "extra_display_name"
        private const val EXTRA_IS_LEADER = "extra_is_leader"

        fun buildIntent(context: Context, username: String, displayName: String, isLeader: Boolean): Intent {
            return Intent(context, DashboardTeamMemberProfileActivity::class.java).apply {
                putExtra(EXTRA_USERNAME, username)
                putExtra(EXTRA_DISPLAY_NAME, displayName)
                putExtra(EXTRA_IS_LEADER, isLeader)
            }
        }
    }


}

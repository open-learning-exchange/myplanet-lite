package org.ole.planet.myplanet.lite

import android.content.Intent
import android.view.LayoutInflater
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.checkbox.MaterialCheckBox
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal class SurveyTranslationSettingsController(
    private val activity: AppCompatActivity,
    private val menuItem: MenuItem,
) {
    private val preferences =
        SecurePreferencesProvider.getServerPreferences(activity.applicationContext)
    private val toggle: SwitchCompat? = menuItem.actionView?.findViewById(R.id.menuToggle)
    private var isUpdatingToggle = false

    fun bind() {
        updateVisualState(DashboardActivity.isSurveyTranslationActive(activity))
        toggle?.setOnCheckedChangeListener { _, enabled ->
            if (isUpdatingToggle) return@setOnCheckedChangeListener
            if (enabled) {
                updateVisualState(false)
                showConsentDialog()
            } else {
                applyPreference(false, showToast = true)
            }
        }
    }

    fun handleMenuSelection() {
        if (toggle?.isChecked == true || menuItem.isChecked) {
            applyPreference(false, showToast = true)
        } else {
            showConsentDialog()
        }
    }

    private fun applyPreference(enabled: Boolean, showToast: Boolean) {
        preferences.edit()
            .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED, enabled)
            .apply()
        updateVisualState(enabled)
        if (showToast) {
            Toast.makeText(
                activity,
                if (enabled) R.string.dashboard_settings_survey_translation_enabled
                else R.string.dashboard_settings_survey_translation_disabled,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun updateVisualState(enabled: Boolean) {
        isUpdatingToggle = true
        toggle?.isChecked = enabled
        menuItem.isChecked = enabled
        isUpdatingToggle = false
    }

    private fun showConsentDialog() {
        if (!preferences.contains(DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED)) {
            preferences.edit()
                .putBoolean(
                    DashboardActivity.KEY_SURVEY_TRANSLATIONS_ENABLED,
                    DashboardActivity.DEFAULT_SURVEY_TRANSLATION_ENABLED,
                ).apply()
        }
        val dialogView = LayoutInflater.from(activity)
            .inflate(R.layout.dialog_survey_translation_consent, null, false)
        val consentCheckBox =
            dialogView.findViewById<MaterialCheckBox>(R.id.surveyTranslationConsentCheckBox)
        val policyLink = dialogView.findViewById<TextView>(R.id.surveyTranslationPolicyLink)
        consentCheckBox.isChecked = true

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.login_survey_translation_consent_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dashboard_survey_translation_consent_accept) { alertDialog, _ ->
                preferences.edit()
                    .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, true)
                    .apply()
                applyPreference(consentCheckBox.isChecked, showToast = true)
                alertDialog.dismiss()
            }
            .setNegativeButton(R.string.dashboard_survey_translation_consent_cancel) { alertDialog, _ ->
                preferences.edit()
                    .putBoolean(DashboardActivity.KEY_SURVEY_TRANSLATION_CONSENT_ACCEPTED, false)
                    .apply()
                applyPreference(false, showToast = false)
                alertDialog.dismiss()
            }
            .create()
        policyLink.setOnClickListener {
            activity.startActivity(Intent(activity, PrivacyPolicyActivity::class.java))
        }
        dialog.show()
    }
}

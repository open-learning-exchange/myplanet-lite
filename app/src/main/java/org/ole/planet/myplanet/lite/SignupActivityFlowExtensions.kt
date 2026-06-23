/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-06-10
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

internal fun SignupActivity.navigateBack() {
    if (currentStepIndex == 0) {
        finish()
    } else {
        showStep(currentStepIndex - 1)
    }
}

internal fun SignupActivity.moveToNextStep() {
    if (currentStepIndex < steps.lastIndex) {
        showStep(currentStepIndex + 1)
    }
}

internal fun SignupActivity.completeSignup() {
    val autoLogin = autoLoginCheck.isChecked
    val resultIntent = Intent().apply {
            putExtra(SignupActivity.EXTRA_AUTO_LOGIN, autoLogin)
        if (autoLogin) {
            val username = usernameInput.text?.toString()?.trim().orEmpty()
            val password = passwordInput.text?.toString().orEmpty()
            putExtra(SignupActivity.EXTRA_USERNAME, username)
            org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore.saveTemporarySignUpPassword(this@completeSignup, password)
        }
    }
    if (autoLogin) {
        val username = usernameInput.text?.toString()?.trim().orEmpty()
        val password = passwordInput.text?.toString().orEmpty()
        SecurePreferencesProvider.getEncryptedPreferences(this, MyPlanetLite.SECURE_PREFS_NAME)
            .edit {
                putBoolean(MyPlanetLite.KEY_REMEMBER_CREDENTIALS, true)
                putString(MyPlanetLite.KEY_REMEMBERED_USERNAME, username)
                putString(org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore.getPasswordKey(username), password)
            }
    }
    setResult(android.app.Activity.RESULT_OK, resultIntent)
    finish()
}

internal fun SignupActivity.showStep(index: Int) {
    if (index == currentStepIndex) return
    currentStepIndex = index
    updateStepVisibility()
}

internal fun SignupActivity.updateStepVisibility() {
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

internal fun SignupActivity.handleNextButtonClick() {
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

internal fun SignupActivity.submitUsernameStep() {
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

internal fun SignupActivity.canProceedWithUsernameStep(username: String): Boolean {
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

internal fun SignupActivity.handleUsernameAvailabilityResult(availability: UsernameAvailability) {
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

internal fun SignupActivity.submitLicenseStep() {
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
                    this@submitLicenseStep,
                    R.string.signup_connection_error_input,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

internal fun SignupActivity.showUsernameAvailabilityChecking(isChecking: Boolean) {
    if (isChecking) {
        usernameLayout.error = null
        usernameLayout.helperText = getString(R.string.signup_username_checking)
    } else if (usernameLayout.helperText == getString(R.string.signup_username_checking)) {
        usernameLayout.helperText = null
    }
}

internal fun SignupActivity.validateBirthDate(): Boolean {
    return if (birthDateSelection != null) {
        birthDateLayout.error = null
        true
    } else {
        birthDateLayout.error = getString(R.string.signup_birth_date_error_required)
        false
    }
}

internal fun SignupActivity.validateGender(): Boolean {
    return if (genderGroup.checkedRadioButtonId != -1) {
        genderErrorView.visibility = View.GONE
        true
    } else {
        genderErrorView.text = getString(R.string.signup_gender_error_required)
        genderErrorView.visibility = View.VISIBLE
        false
    }
}

internal fun SignupActivity.validateLanguage(): Boolean {
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

internal fun SignupActivity.updatePasswordErrorState(showEmptyError: Boolean = false): Boolean {
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

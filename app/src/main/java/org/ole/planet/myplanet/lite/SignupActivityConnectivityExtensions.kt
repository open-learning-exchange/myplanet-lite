/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-06-10
 */

package org.ole.planet.myplanet.lite

import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun SignupActivity.setupServerConfiguration() {
    serverBaseUrl = intent.getStringExtra(SignupActivity.EXTRA_SERVER_BASE_URL)?.trim().orEmpty()
    if (serverBaseUrl.isEmpty()) {
        serverBaseUrl = loadStoredServerBaseUrl()
    }
    serverParentCode = serverPreferences.getString(SignupActivity.KEY_SERVER_PARENT_CODE, null)
    serverCode = serverPreferences.getString(SignupActivity.KEY_SERVER_CODE, null)
}

internal fun SignupActivity.verifyServerAvailability(
    step: SignupStep,
    force: Boolean = false,
): Job? {
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

    val job =
        lifecycleScope.launch {
            applyConnectivityState(step, reachable = false, checking = true)
            val connectivityResult =
                withContext(Dispatchers.IO) {
                    serverConnectivityRepository.checkServerConnectivity(trimmedBaseUrl)
                }
            if (!isActive) {
                return@launch
            }
            if (connectivityResult.reachable) {
                persistServerMetadata(
                    trimmedBaseUrl,
                    connectivityResult.parentCode,
                    connectivityResult.code,
                )
            }
            applyConnectivityState(step, reachable = connectivityResult.reachable, checking = false)
        }

    serverCheckJob = job
    return job
}

internal fun SignupActivity.persistServerMetadata(
    baseUrl: String,
    parentCode: String?,
    code: String?,
) {
    serverParentCode = parentCode
    serverCode = code
    serverPreferences.edit {
        putString(SignupActivity.KEY_SERVER_URL, baseUrl)
        if (parentCode != null) {
            putString(SignupActivity.KEY_SERVER_PARENT_CODE, parentCode)
        } else {
            remove(SignupActivity.KEY_SERVER_PARENT_CODE)
        }
        if (code != null) {
            putString(SignupActivity.KEY_SERVER_CODE, code)
        } else {
            remove(SignupActivity.KEY_SERVER_CODE)
        }
    }
}

internal fun SignupActivity.applyConnectivityState(
    step: SignupStep,
    reachable: Boolean,
    checking: Boolean,
) {
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

internal fun SignupActivity.updateStepConnectivityMessage(
    step: SignupStep,
    reachable: Boolean,
    checking: Boolean,
) {
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

internal fun SignupActivity.updateStepActionState(step: SignupStep = steps[currentStepIndex]) {
    if (step != steps[currentStepIndex]) {
        return
    }
    val shouldEnable =
        when (step) {
            SignupStep.USERNAME, SignupStep.LICENSE -> {
                !isProcessingStepAction && !isCheckingServerAvailability && isServerReachable
            }

            else -> {
                !isProcessingStepAction
            }
        }
    nextButton.isEnabled = shouldEnable
    nextButton.alpha = if (shouldEnable) 1f else 0.5f
}

internal fun SignupActivity.loadStoredServerBaseUrl(): String =
    serverPreferences.getString(SignupActivity.KEY_SERVER_URL, "").orEmpty().trim()

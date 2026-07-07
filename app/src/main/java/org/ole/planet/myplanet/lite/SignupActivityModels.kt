/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-06-10
 */

package org.ole.planet.myplanet.lite

internal data class SignupLanguageOption(
    val languageTag: String,
    val labelRes: Int,
    val levelArrayRes: Int,
)

internal enum class SignupStep(
    val titleRes: Int,
    val subtitleRes: Int,
    val nextTextRes: Int = R.string.signup_next_action,
    val showBackButton: Boolean = true,
) {
    USERNAME(
        R.string.signup_step_username_title,
        R.string.signup_step_username_subtitle,
        showBackButton = false,
    ),
    NAMES(
        R.string.signup_step_names_title,
        R.string.signup_step_names_subtitle,
    ),
    BIRTH_DATE(
        R.string.signup_step_birth_date_title,
        R.string.signup_step_birth_date_subtitle,
    ),
    GENDER(
        R.string.signup_step_gender_title,
        R.string.signup_step_gender_subtitle,
    ),
    CONTACT(
        R.string.signup_step_contact_title,
        R.string.signup_step_contact_subtitle,
    ),
    PASSWORD(
        R.string.signup_step_password_title,
        R.string.signup_step_password_subtitle,
    ),
    LANGUAGE(
        R.string.signup_step_language_title,
        R.string.signup_step_language_subtitle,
    ),
    LICENSE(
        R.string.signup_step_license_title,
        R.string.signup_step_license_subtitle,
        nextTextRes = R.string.signup_accept_action,
    ),
}

enum class UsernameAvailability { AVAILABLE, TAKEN, UNKNOWN }

internal enum class SignupSubmissionResult { SUCCESS, USERNAME_TAKEN, FAILED }

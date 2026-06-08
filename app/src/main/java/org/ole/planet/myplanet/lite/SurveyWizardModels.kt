/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Json
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyStatusStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionAnswer
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionTeam
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.profile.GENDER_FEMALE
import org.ole.planet.myplanet.lite.profile.GENDER_MALE
import org.ole.planet.myplanet.lite.profile.GENDER_OTHER
import org.ole.planet.myplanet.lite.profile.LearningLevelTranslator
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager.TranslatedQuestion
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import androidx.core.graphics.toColorInt

internal const val OTHER_CHOICE_TAG = "other_choice"
internal const val BIRTH_DATE_PICKER_TAG = "survey_birth_date_picker"
internal const val DEFAULT_RATING_SCALE_MAX = 9
internal const val DEFAULT_EXAM_PASSING_PERCENTAGE = 100
internal const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
internal const val ANONYMOUS_RESPONDENT_NAME = "Anonymous"

internal sealed class WizardStep : java.io.Serializable {
    object Basics : WizardStep() {
        private fun readResolve(): Any = Basics
    }

    object Names : WizardStep() {
        private fun readResolve(): Any = Names
    }

    object BirthDate : WizardStep() {
        private fun readResolve(): Any = BirthDate
    }

    object Contact : WizardStep() {
        private fun readResolve(): Any = Contact
    }

    object LanguageLevel : WizardStep() {
        private fun readResolve(): Any = LanguageLevel
    }

    data class Question(val question: SurveyQuestion, val questionIndex: Int) : WizardStep()
}

internal data class SurveyRespondent(
    var gender: String? = null,
    var birthYear: Int? = null,
    var age: Int? = null,
    var additionalInfo: Boolean = false,
    var firstName: String? = null,
    var middleName: String? = null,
    var lastName: String? = null,
    var birthDate: String? = null,
    var email: String? = null,
    var phoneNumber: String? = null,
    var language: String? = null,
    var level: String? = null,
)

internal sealed class SurveyAnswer : java.io.Serializable {
    data class Text(val value: String) : SurveyAnswer()
    data class SingleChoice(val choice: SelectedOption?) : SurveyAnswer()
    data class MultipleChoice(val choices: List<SelectedOption>) : SurveyAnswer()
    data class Rating(val score: Int) : SurveyAnswer()
}

internal data class SelectedOption(
    val id: String?,
    val text: String,
    val isOther: Boolean = false,
) : java.io.Serializable {
    fun toSubmissionValue(): SubmissionOptionValue {
        return SubmissionOptionValue(
            id = id,
            text = text,
            isOther = isOther,
        )
    }
}

internal data class SubmissionOptionValue(
    @param:Json(name = "id") val id: String?,
    @param:Json(name = "text") val text: String?,
    @param:Json(name = "isOther") val isOther: Boolean = false,
)

internal data class SurveySubmissionParams(
    val survey: SurveyDocument,
    val existingSubmission: DashboardSurveySubmissionsRepository.SubmissionLookup?,
    val username: String?,
    val fullName: String,
    val parentId: String,
    val answersPayload: List<SubmissionAnswer>,
    val totalGrade: Int,
    val profile: UserProfile?
)

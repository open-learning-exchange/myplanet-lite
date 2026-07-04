package org.ole.planet.myplanet.lite

import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.profile.GENDER_FEMALE
import org.ole.planet.myplanet.lite.profile.GENDER_MALE
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SurveyWizardRespondentExtensionsTest {

    @Test
    fun testUpdateRespondentBirthYear_withValidYear() {
        val fragment = SurveyWizardFragment()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        fragment.updateRespondentBirthYear("2000")

        assertEquals(2000, fragment.respondent.birthYear)
        assertEquals(currentYear - 2000, fragment.respondent.age)
    }

    @Test
    fun testUpdateRespondentBirthYear_withFutureYear() {
        val fragment = SurveyWizardFragment()
        val futureYear = Calendar.getInstance().get(Calendar.YEAR) + 1

        fragment.updateRespondentBirthYear(futureYear.toString())

        assertEquals(null, fragment.respondent.birthYear)
        assertEquals(null, fragment.respondent.age)
    }

    @Test
    fun testUpdateRespondentBirthYear_withNullOrBlank() {
        val fragment = SurveyWizardFragment()
        fragment.respondent.birthYear = 2000
        fragment.respondent.age = 20

        fragment.updateRespondentBirthYear(null)

        assertEquals(null, fragment.respondent.birthYear)
        assertEquals(null, fragment.respondent.age)

        fragment.respondent.birthYear = 2000
        fragment.respondent.age = 20

        fragment.updateRespondentBirthYear("   ")

        assertEquals(null, fragment.respondent.birthYear)
        assertEquals(null, fragment.respondent.age)
    }

    @Test
    fun testParseBirthDateIso() {
        val fragment = SurveyWizardFragment()

        // 2000-01-01 -> 946684800000
        val timestamp = fragment.parseBirthDateIso("2000-01-01")
        assertEquals(946684800000L, timestamp)

        assertEquals(null, fragment.parseBirthDateIso(null))
        assertEquals(null, fragment.parseBirthDateIso(""))
        assertEquals(null, fragment.parseBirthDateIso("invalid-date"))
    }

    @Test
    fun testFormatBirthDateIso() {
        val fragment = SurveyWizardFragment()

        val dateString = fragment.formatBirthDateIso(946684800000L)
        assertEquals("2000-01-01", dateString)
    }

    @Test
    fun testFormatBirthDateDisplay() {
        val fragment = SurveyWizardFragment()

        assertEquals("2000-01-01", fragment.formatBirthDateDisplay("2000-01-01"))
        assertEquals("invalid-date", fragment.formatBirthDateDisplay("invalid-date"))
    }

    @Test
    fun testLevelArrayForLanguage() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        assertEquals(org.ole.planet.myplanet.lite.R.array.signup_level_options_language_en, fragment.levelArrayForLanguage("Unknown Language"))
        assertEquals(org.ole.planet.myplanet.lite.R.array.signup_level_options_language_en, fragment.levelArrayForLanguage(null))
        assertEquals(org.ole.planet.myplanet.lite.R.array.signup_level_options_language_en, fragment.levelArrayForLanguage("English"))
    }

    @Test
    fun testCreateBirthYearLayout() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()
        fragment.respondent.birthYear = 1990

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val (layout, input) = fragment.createBirthYearLayout(activity)

        assertEquals(activity.getString(org.ole.planet.myplanet.lite.R.string.dashboard_survey_wizard_birth_year_label), layout.hint)
        assertEquals("1990", input.text.toString())
    }

    @Test
    fun testCreateAdditionalCheckBox() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()
        fragment.respondent.additionalInfo = true

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val checkbox = fragment.createAdditionalCheckBox(activity)

        assertEquals(activity.getString(org.ole.planet.myplanet.lite.R.string.dashboard_survey_wizard_additional_info_label), checkbox.text.toString())
        assertTrue(checkbox.isChecked)
    }

    @Test
    fun testCreateDropdownLayout() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val hintText = "Select Language"
        val (layout, input) = fragment.createDropdownLayout(activity, hintText)

        assertEquals(hintText, layout.hint)
        assertEquals(android.text.InputType.TYPE_NULL, input.inputType)
    }

    @Test
    fun testCreateGenderGroup() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = org.robolectric.Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()
        fragment.respondent.gender = GENDER_FEMALE

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val (label, group) = fragment.createGenderGroup(activity)

        assertEquals(activity.getString(org.ole.planet.myplanet.lite.R.string.dashboard_survey_wizard_gender_label), label.text.toString())
        assertEquals(2, group.childCount)

        val maleButton = group.getChildAt(0) as RadioButton
        val femaleButton = group.getChildAt(1) as RadioButton

        assertEquals(GENDER_MALE, maleButton.tag)
        assertEquals(GENDER_FEMALE, femaleButton.tag)

        assertFalse(maleButton.isChecked)
        assertTrue(femaleButton.isChecked)
    }
}

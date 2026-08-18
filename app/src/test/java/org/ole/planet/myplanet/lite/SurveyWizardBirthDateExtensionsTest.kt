package org.ole.planet.myplanet.lite

import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SurveyWizardBirthDateExtensionsTest {

    @Test
    fun testParseBirthDateIso() {
        val fragment = SurveyWizardFragment()

        // 2000-01-01 in UTC
        val timestamp = fragment.parseBirthDateIso("2000-01-01")
        assertEquals(946684800000L, timestamp)

        assertNull(fragment.parseBirthDateIso(null))
        assertNull(fragment.parseBirthDateIso(""))
        assertNull(fragment.parseBirthDateIso("invalid-date"))
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
    fun testInitialBirthDatePickerSelection() {
        val fragment = SurveyWizardFragment()

        fragment.birthDateSelection = 946684800000L
        assertEquals(946684800000L, fragment.initialBirthDatePickerSelection())

        fragment.birthDateSelection = null
        fragment.respondent.birthYear = 1990

        val selection = fragment.initialBirthDatePickerSelection()
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = selection!!
        }

        assertEquals(1990, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH))
        assertEquals(1, calendar.get(Calendar.DAY_OF_MONTH))

        fragment.respondent.birthYear = null
        assertNull(fragment.initialBirthDatePickerSelection())
    }

    @Test
    fun testCreateBirthDateLayout() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()
        fragment.respondent.birthDate = "2000-01-01"

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val (layout, input) = fragment.createBirthDateLayout(activity)

        assertEquals(activity.getString(R.string.dashboard_survey_wizard_birth_date_label), layout.hint)
        assertEquals("2000-01-01", input.text.toString())
        assertTrue(input.isClickable)
        assertFalse(input.isFocusable)
    }

    @Test
    fun testRenderBirthDateStep() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val (view, collector) = fragment.renderBirthDateStep()

        assertNotNull(view)

        fragment.birthDateSelection = 946684800000L

        val collected = collector.invoke()
        assertTrue(collected)
        assertEquals("2000-01-01", fragment.respondent.birthDate)
    }

    @Test
    fun testShowBirthDatePicker() {
        com.google.mlkit.common.sdkinternal.MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val controller = Robolectric.buildActivity(androidx.fragment.app.FragmentActivity::class.java)
        val activity = controller.create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)

        val fragment = SurveyWizardFragment()

        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "survey")
            .commitNow()

        val input = TextInputEditText(activity)
        fragment.showBirthDatePicker(input)

        // Execute pending transactions to ensure the dialog fragment is fully added
        fragment.childFragmentManager.executePendingTransactions()

        val picker = fragment.childFragmentManager.findFragmentByTag(org.ole.planet.myplanet.lite.BIRTH_DATE_PICKER_TAG)
        assertNotNull(picker)
    }
}

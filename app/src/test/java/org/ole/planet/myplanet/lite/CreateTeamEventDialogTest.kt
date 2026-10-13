package org.ole.planet.myplanet.lite

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CreateTeamEventDialogTest {
    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("event_form_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SecurePreferencesProvider.injectedPreferences = prefs
        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team-1", "Team One")
    }

    @After fun teardown() = SecurePreferencesProvider.resetForTesting()

    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup) {
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
    } else emptyList()

    @Test fun `calendar opens form with selected date validates content and restores draft`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { calendar ->
                calendar.selectDate(LocalDate.now().plusDays(7))
                calendar.requireView().findViewById<View>(R.id.calendarCreate).performClick()
                calendar.childFragmentManager.executePendingTransactions()
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                assertEquals(LocalDate.now().plusDays(7).toString(), form.requireArguments().getString("startDate"))
                val dialog = form.requireDialog() as AlertDialog
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                assertTrue(descendants(dialog.window!!.decorView).filterIsInstance<TextView>().any {
                    it.text.toString() == form.getString(R.string.calendar_required_fields) && it.visibility == View.VISIBLE
                })
                val title = descendants(dialog.window!!.decorView).filterIsInstance<TextInputLayout>().single {
                    it.hint.toString() == form.getString(R.string.dashboard_resources_pdf_field_title)
                }.editText as EditText
                title.setText("Draft meeting")
            }
            scenario.recreate()
            scenario.onFragment { calendar ->
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                val title = descendants(form.requireDialog().window!!.decorView).filterIsInstance<TextInputLayout>().single {
                    it.hint.toString() == form.getString(R.string.dashboard_resources_pdf_field_title)
                }.editText!!
                assertEquals("Draft meeting", title.text.toString())
            }
        }
    }
    @Test fun `date picker accepts one day or ordered ranges and retains pending selection`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { calendar ->
                calendar.requireView().findViewById<View>(R.id.calendarCreate).performClick()
                calendar.childFragmentManager.executePendingTransactions()
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                form.requireDialog().window!!.decorView.findViewWithTag<View>("eventDates").performClick()
                form.childFragmentManager.executePendingTransactions()
                val picker = form.childFragmentManager.findFragmentByTag("event_dates") as TeamEventDatePickerFragment
                picker.select(LocalDate.now().plusDays(7))
                assertEquals(LocalDate.now().plusDays(7), picker.start)
                assertNull(picker.end)
            }
            scenario.recreate()
            scenario.onFragment { calendar ->
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                val picker = form.childFragmentManager.findFragmentByTag("event_dates") as TeamEventDatePickerFragment
                picker.select(LocalDate.now().plusDays(2))
                assertEquals(LocalDate.now().plusDays(2), picker.start)
                assertEquals(LocalDate.now().plusDays(7), picker.end)
                // A third tap starts a fresh single-day selection.
                picker.select(LocalDate.now().plusDays(21))
                assertNull(picker.end)
                (picker.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                form.childFragmentManager.executePendingTransactions()
                val dates = form.requireDialog().window!!.decorView.findViewWithTag<TextView>("eventDates")
                assertFalse(dates.text.toString().contains(" – "))
                // Confirmed single selection is passed back to the form, including across months.
                dates.performClick()
                form.childFragmentManager.executePendingTransactions()
                val reopened = form.childFragmentManager.findFragmentByTag("event_dates") as TeamEventDatePickerFragment
                assertEquals(LocalDate.now().plusDays(21), reopened.start)
                assertNull(reopened.end)
                reopened.select(LocalDate.now().plusDays(23))
                reopened.select(LocalDate.now().plusDays(27))
                (reopened.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                form.childFragmentManager.executePendingTransactions()
                assertTrue(dates.text.toString().contains(" – "))
            }
        }
    }

    @Test fun `clock picker offers durations restores state and calculates next day end`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { calendar ->
                calendar.selectDate(LocalDate.now().plusDays(7))
                calendar.requireView().findViewById<View>(R.id.calendarCreate).performClick()
                calendar.childFragmentManager.executePendingTransactions()
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                val root = form.requireDialog().window!!.decorView
                descendants(root).filterIsInstance<android.widget.CheckBox>().single {
                    it.text.toString() == form.getString(R.string.calendar_all_day)
                }.isChecked = false
                val start = descendants(root).filterIsInstance<TextInputLayout>().single {
                    it.hint.toString() == form.getString(R.string.calendar_start_time)
                }.editText!!
                assertFalse(start.showSoftInputOnFocus)
                start.performClick()
                form.childFragmentManager.executePendingTransactions()
                val picker = form.childFragmentManager.findFragmentByTag("event_time") as TeamEventTimePickerFragment
                val clock = picker.requireDialog().findViewById<android.widget.TimePicker>(R.id.eventTimeClock)
                assertFalse(clock.is24HourView)
                clock.hour = 23; clock.minute = 45
                val options = picker.requireDialog().findViewById<com.google.android.material.chip.ChipGroup>(R.id.eventDurationOptions)
                assertEquals(listOf(5, 15, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300),
                    (0 until options.childCount).map { options.getChildAt(it).tag })
                (options.findViewWithTag<View>(90) as com.google.android.material.chip.Chip).isChecked = true
            }
            scenario.recreate()
            scenario.onFragment { calendar ->
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                val picker = form.childFragmentManager.findFragmentByTag("event_time") as TeamEventTimePickerFragment
                val clock = picker.requireDialog().findViewById<android.widget.TimePicker>(R.id.eventTimeClock)
                assertEquals(23, clock.hour); assertEquals(45, clock.minute)
                val options = picker.requireDialog().findViewById<com.google.android.material.chip.ChipGroup>(R.id.eventDurationOptions)
                assertTrue((options.findViewWithTag<View>(90) as com.google.android.material.chip.Chip).isChecked)
                (picker.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
                form.childFragmentManager.executePendingTransactions()
                fun time(label: Int) = descendants(form.requireDialog().window!!.decorView).filterIsInstance<TextInputLayout>()
                    .single { it.hint.toString() == form.getString(label) }.editText!!
                assertEquals("23:45", time(R.string.calendar_start_time).text.toString())
                assertEquals("01:15", time(R.string.calendar_end_time).text.toString())
                form.requireDialog().window!!.decorView.findViewWithTag<View>("eventDates").performClick()
                form.childFragmentManager.executePendingTransactions()
                val dates = form.childFragmentManager.findFragmentByTag("event_dates") as TeamEventDatePickerFragment
                assertEquals(LocalDate.now().plusDays(7), dates.start)
                assertEquals(LocalDate.now().plusDays(8), dates.end)
            }
        }
    }

    @Test fun `new event date picker disables past dates and ignores past taps`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { calendar ->
                val today = LocalDate.now()
                calendar.selectDate(today.minusDays(2))
                calendar.requireView().findViewById<View>(R.id.calendarCreate).performClick()
                calendar.childFragmentManager.executePendingTransactions()
                val form = calendar.childFragmentManager.findFragmentByTag("create_team_event") as CreateTeamEventDialogFragment
                form.requireDialog().window!!.decorView.findViewWithTag<View>("eventDates").performClick()
                form.childFragmentManager.executePendingTransactions()
                val picker = form.childFragmentManager.findFragmentByTag("event_dates") as TeamEventDatePickerFragment
                assertEquals(today, picker.start)
                picker.select(today.minusDays(1))
                assertEquals(today, picker.start)
                descendants(picker.requireDialog().window!!.decorView).filter { view ->
                    val date = (view.tag as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    date != null && date < today
                }.forEach { assertFalse(it.isEnabled) }
                picker.select(today.plusDays(1))
                assertEquals(today.plusDays(1), picker.start)
            }
        }
    }

}

package org.ole.planet.myplanet.lite

import android.content.Context
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TeamCalendarFragmentTest {
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("calendar_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SecurePreferencesProvider.injectedPreferences = prefs
        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team-1", "Team One")
    }

    @After
    fun teardown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun `calendar month grid handles leap year and locale week starts`() {
        val month = YearMonth.of(2024, 2)
        val mondayDays = calendarMonthDays(month, DayOfWeek.MONDAY)
        assertEquals(42, mondayDays.size)
        assertEquals(LocalDate.of(2024, 1, 29), mondayDays.first())
        assertTrue(mondayDays.contains(LocalDate.of(2024, 2, 29)))
        assertEquals(LocalDate.of(2024, 1, 28), calendarMonthDays(month, DayOfWeek.SUNDAY).first())
    }

    @Test
    fun `selection filters agenda events and month navigation clamps short months`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.selectDate(LocalDate.of(2024, 1, 31))
                fragment.moveMonth(1)
                assertEquals(LocalDate.of(2024, 2, 29), fragment.selectedDate)
                assertEquals(42, fragment.requireView().findViewById<GridLayout>(R.id.calendarDays).childCount)
                fragment.showEvents("team-1", listOf(
                    TeamCalendarEvent("one", fragment.selectedDate, "Meeting", "09:00"),
                    TeamCalendarEvent("two", fragment.selectedDate.plusDays(1), "Tomorrow"),
                ))
                val agenda = fragment.requireView().findViewById<LinearLayout>(R.id.calendarEvents)
                assertEquals(1, agenda.childCount)
                assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.calendarEmpty).visibility)
                fragment.selectDate(LocalDate.of(2024, 3, 2))
                assertEquals(0, agenda.childCount)
                assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.calendarEmpty).visibility)
                fragment.requireView().findViewById<View>(R.id.calendarToday).performClick()
                assertEquals(LocalDate.now(), fragment.selectedDate)
            }
        }
    }

    @Test
    fun `selected date survives recreation and events from another team are ignored`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            val date = LocalDate.of(2027, 12, 15)
            scenario.onFragment { it.selectDate(date) }
            scenario.recreate()
            scenario.onFragment { fragment ->
                assertEquals(date, fragment.selectedDate)
                assertEquals(YearMonth.of(2027, 12), fragment.visibleMonth)
                fragment.showEvents("another-team", listOf(TeamCalendarEvent("event", date, "Private event")))
                assertEquals(0, fragment.requireView().findViewById<LinearLayout>(R.id.calendarEvents).childCount)
            }
        }
    }
    @Test
    fun `event descriptions render voice markdown with bold text and links`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.showEvents("team-1", listOf(TeamCalendarEvent(
                    "event", fragment.selectedDate, "Meeting", description =
                        "# Agenda\n\n**Important**\n\n- First item\n\n[Join](https://example.org/meeting)",
                    meetupLink = "https://planet.gt/spa/teams/view/team-1",
                )))
                val agenda = fragment.requireView().findViewById<LinearLayout>(R.id.calendarEvents)
                val card = agenda.getChildAt(0) as LinearLayout
                val body = card.getChildAt(2) as android.widget.TextView
                val rendered = body.text as android.text.Spanned
                assertTrue(rendered.toString().contains("Agenda"))
                assertTrue(rendered.toString().contains("First item"))
                assertFalse(rendered.toString().contains("**Important**"))
                assertTrue(rendered.getSpans(0, rendered.length, io.noties.markwon.core.spans.StrongEmphasisSpan::class.java)
                    .isNotEmpty())
                assertTrue(rendered.getSpans(0, rendered.length, android.text.style.URLSpan::class.java)
                    .any { it.url == "https://example.org/meeting" })
                val link = card.getChildAt(3) as android.widget.TextView
                val linkText = link.text as android.text.Spanned
                assertEquals("https://planet.gt/spa/teams/view/team-1", linkText.toString())
                assertEquals(linkText.toString(), linkText.getSpans(0, linkText.length,
                    android.text.style.URLSpan::class.java).single().url)
                assertTrue(link.movementMethod is android.text.method.LinkMovementMethod)
            }
        }
    }

    @Test
    fun `Google descriptions render HTML links and keep plain text and locations intact`() {
        launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            scenario.onFragment { fragment ->
                fragment.showEvents("team-1", listOf(TeamCalendarEvent(
                    "google:1", fragment.selectedDate, "Google meeting", description =
                        "<p><b>Agenda</b> &amp; <i>details</i></p><ul><li>First item</li></ul>" +
                        "<a href=\"https://example.org/meeting\">Join</a>",
                    sourceCalendar = "Personal", location = "Room <b>A</b>",
                )))
                val agenda = fragment.requireView().findViewById<LinearLayout>(R.id.calendarEvents)
                val card = agenda.getChildAt(0) as LinearLayout
                val body = card.getChildAt(2) as android.widget.TextView
                val rendered = body.text as android.text.Spanned
                assertTrue(rendered.toString().contains("Agenda & details"))
                assertTrue(rendered.toString().contains("First item"))
                assertFalse(rendered.toString().contains("<p>"))
                assertTrue(rendered.toString().endsWith("Room <b>A</b>"))
                assertTrue(rendered.getSpans(0, rendered.length, android.text.style.StyleSpan::class.java)
                    .any { it.style == android.graphics.Typeface.BOLD })
                assertTrue(rendered.getSpans(0, rendered.length, android.text.style.URLSpan::class.java)
                    .any { it.url == "https://example.org/meeting" })
                assertTrue(body.movementMethod is android.text.method.LinkMovementMethod)
                assertEquals("First line\nSecond line: 2 < 3 & 5 > 4\nOnline",
                    googleCalendarDescription("First line\nSecond line: 2 < 3 & 5 > 4", "Online").toString())
            }
        }
    }

    @Test fun `edit action is only shown for events created by current user`() {
        val credentials = org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
        credentials.setSessionCredentials(org.ole.planet.myplanet.lite.profile.StoredCredentials("ana", "password"))
        try {
            launchFragmentInContainer<TeamCalendarFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
                scenario.onFragment { fragment ->
                    val date = fragment.selectedDate
                    val own = org.ole.planet.myplanet.lite.dashboard.TeamMeetup("own", "1-rev", "Own", "Agenda",
                        date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), null,
                        "", "", "none", 10, emptySet(), "", "", createdBy = "ana")
                    val other = own.copy(id = "other", title = "Other", createdBy = "juan")
                    fragment.showEvents("team-1", (own.occurrences(date, date) + other.occurrences(date, date)))
                    val agenda = fragment.requireView().findViewById<LinearLayout>(R.id.calendarEvents)
                    fun editCount(index: Int): Int {
                        val card = agenda.getChildAt(index) as LinearLayout
                        return (0 until card.childCount).map { card.getChildAt(it) }
                            .filterIsInstance<com.google.android.material.button.MaterialButton>()
                            .count { it.text.toString() == fragment.getString(R.string.calendar_edit_event) }
                    }
                    assertEquals(1, editCount(0))
                    assertEquals(0, editCount(1))
                }
            }
        } finally { credentials.setSessionCredentials(null) }
    }

}

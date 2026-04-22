package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class DashboardTeamsFragmentTest {

    private lateinit var mockPrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        mockPrefs = FakeSharedPreferences()
        SecurePreferencesProvider.injectedPreferences = mockPrefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `no team selected shows empty view`() {
        val scenario = launchFragmentInContainer<DashboardTeamsFragment>(themeResId = R.style.Theme_MyPlanetLite)
        scenario.onFragment { fragment ->
            val emptyView = fragment.view?.findViewById<TextView>(R.id.teamsEmptyView)
            val teamContentContainer = fragment.view?.findViewById<FrameLayout>(R.id.teamContentContainer)

            assertNotNull(emptyView)
            assertTrue(emptyView!!.isVisible)
            assertEquals(fragment.getString(R.string.dashboard_teams_select_team_hint), emptyView.text.toString())

            assertNotNull(teamContentContainer)
            assertFalse(teamContentContainer!!.isVisible)
        }
    }

    @Test
    fun `team selected shows content container and loads voices by default`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team1", "Team 1")

        val scenario = launchFragmentInContainer<DashboardTeamsFragment>(themeResId = R.style.Theme_MyPlanetLite)
        scenario.onFragment { fragment ->
            val emptyView = fragment.view?.findViewById<TextView>(R.id.teamsEmptyView)
            val teamContentContainer = fragment.view?.findViewById<FrameLayout>(R.id.teamContentContainer)

            assertNotNull(emptyView)
            assertFalse(emptyView!!.isVisible)

            assertNotNull(teamContentContainer)
            assertTrue(teamContentContainer!!.isVisible)

            val childFragment = fragment.childFragmentManager.findFragmentById(R.id.teamContentContainer)
            assertNotNull(childFragment)
            assertTrue(childFragment is DashboardVoicesFragment)

            val voicesButton = fragment.view?.findViewById<ImageButton>(R.id.teamVoicesButton)
            val surveysButton = fragment.view?.findViewById<ImageButton>(R.id.teamSurveysButton)

            assertNotNull(voicesButton)
            assertTrue(voicesButton!!.isSelected)
            // Using boolean check for alpha > 0.9 to avoid float assertion issues in some kotlin versions, or just use typical assertEquals
            assertEquals(1f, voicesButton.alpha)

            assertNotNull(surveysButton)
            assertFalse(surveysButton!!.isSelected)
            assertEquals(0.5f, surveysButton.alpha)
        }
    }

    @Test
    fun `clicking surveys button loads surveys fragment`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team1", "Team 1")

        val scenario = launchFragmentInContainer<DashboardTeamsFragment>(themeResId = R.style.Theme_MyPlanetLite)
        scenario.onFragment { fragment ->
            val surveysButton = fragment.view?.findViewById<ImageButton>(R.id.teamSurveysButton)
            surveysButton?.performClick()

            fragment.childFragmentManager.executePendingTransactions()
            val childFragment = fragment.childFragmentManager.findFragmentById(R.id.teamContentContainer)
            assertNotNull(childFragment)
            assertTrue(childFragment is DashboardTeamSurveysFragment)

            val voicesButton = fragment.view?.findViewById<ImageButton>(R.id.teamVoicesButton)

            assertNotNull(surveysButton)
            assertTrue(surveysButton!!.isSelected)
            assertEquals(1f, surveysButton.alpha)

            assertNotNull(voicesButton)
            assertFalse(voicesButton!!.isSelected)
            assertEquals(0.5f, voicesButton.alpha)
        }
    }

    @Test
    fun `onResume reloads content if team selection changed`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val scenario = launchFragmentInContainer<DashboardTeamsFragment>(themeResId = R.style.Theme_MyPlanetLite)
        scenario.onFragment { fragment ->
            val emptyView = fragment.view?.findViewById<TextView>(R.id.teamsEmptyView)
            assertTrue(emptyView!!.isVisible)
        }

        DashboardTeamSelectionPreferences.setSelectedTeam(context, "team1", "Team 1")

        // Simulating the fragment going back to resumed state (like navigating back)
        scenario.moveToState(Lifecycle.State.STARTED)
        scenario.moveToState(Lifecycle.State.RESUMED)

        scenario.onFragment { fragment ->
            val emptyView = fragment.view?.findViewById<TextView>(R.id.teamsEmptyView)
            val teamContentContainer = fragment.view?.findViewById<FrameLayout>(R.id.teamContentContainer)

            assertFalse(emptyView!!.isVisible)
            assertTrue(teamContentContainer!!.isVisible)

            val childFragment = fragment.childFragmentManager.findFragmentById(R.id.teamContentContainer)
            assertTrue(childFragment is DashboardVoicesFragment)
        }
    }

    // --- Fakes ---
    class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = data
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { temp[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { temp[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { toRemove.add(key); return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) data.clear()
            for (key in toRemove) data.remove(key)
            data.putAll(temp)
        }
    }
}
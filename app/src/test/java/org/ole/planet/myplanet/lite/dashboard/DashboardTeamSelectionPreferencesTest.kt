/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-23
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DashboardTeamSelectionPreferencesTest {

    private lateinit var mockContext: FakeContext
    private lateinit var mockPrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        mockPrefs = FakeSharedPreferences()
        mockContext = FakeContext(mockPrefs)
    }

    @Test
    fun `getSelectedTeamId returns null when nothing is saved`() {
        assertNull(DashboardTeamSelectionPreferences.getSelectedTeamId(mockContext))
    }

    @Test
    fun `getSelectedTeamId returns id when saved`() {
        mockPrefs.edit().putString("selected_team_id", "team-123").apply()
        assertEquals("team-123", DashboardTeamSelectionPreferences.getSelectedTeamId(mockContext))
    }

    @Test
    fun `getSelectedTeamId returns null when saved id is blank`() {
        mockPrefs.edit().putString("selected_team_id", "   ").apply()
        assertNull(DashboardTeamSelectionPreferences.getSelectedTeamId(mockContext))
    }

    @Test
    fun `getSelectedTeamName returns null when nothing is saved`() {
        assertNull(DashboardTeamSelectionPreferences.getSelectedTeamName(mockContext))
    }

    @Test
    fun `getSelectedTeamName returns name when saved`() {
        mockPrefs.edit().putString("selected_team_name", "Team Alpha").apply()
        assertEquals("Team Alpha", DashboardTeamSelectionPreferences.getSelectedTeamName(mockContext))
    }

    @Test
    fun `getSelectedTeamName returns null when saved name is blank`() {
        mockPrefs.edit().putString("selected_team_name", "   ").apply()
        assertNull(DashboardTeamSelectionPreferences.getSelectedTeamName(mockContext))
    }

    @Test
    fun `setSelectedTeam saves id and name correctly`() {
        DashboardTeamSelectionPreferences.setSelectedTeam(mockContext, "team-123", "Team Alpha")
        assertEquals("team-123", mockPrefs.getString("selected_team_id", null))
        assertEquals("Team Alpha", mockPrefs.getString("selected_team_name", null))
    }

    @Test
    fun `setSelectedTeam clears both when id is null`() {
        mockPrefs.edit()
            .putString("selected_team_id", "team-123")
            .putString("selected_team_name", "Team Alpha")
            .apply()

        DashboardTeamSelectionPreferences.setSelectedTeam(mockContext, null, "Some Name")

        assertNull(mockPrefs.getString("selected_team_id", null))
        assertNull(mockPrefs.getString("selected_team_name", null))
    }

    @Test
    fun `setSelectedTeam clears both when id is blank`() {
        mockPrefs.edit()
            .putString("selected_team_id", "team-123")
            .putString("selected_team_name", "Team Alpha")
            .apply()

        DashboardTeamSelectionPreferences.setSelectedTeam(mockContext, "   ", "Some Name")

        assertNull(mockPrefs.getString("selected_team_id", null))
        assertNull(mockPrefs.getString("selected_team_name", null))
    }

    @Test
    fun `setSelectedTeam saves id but clears name when name is null`() {
        mockPrefs.edit()
            .putString("selected_team_id", "team-123")
            .putString("selected_team_name", "Team Alpha")
            .apply()

        DashboardTeamSelectionPreferences.setSelectedTeam(mockContext, "team-456", null)

        assertEquals("team-456", mockPrefs.getString("selected_team_id", null))
        assertNull(mockPrefs.getString("selected_team_name", null))
    }

    @Test
    fun `setSelectedTeam saves id but clears name when name is blank`() {
        mockPrefs.edit()
            .putString("selected_team_id", "team-123")
            .putString("selected_team_name", "Team Alpha")
            .apply()

        DashboardTeamSelectionPreferences.setSelectedTeam(mockContext, "team-456", "   ")

        assertEquals("team-456", mockPrefs.getString("selected_team_id", null))
        assertNull(mockPrefs.getString("selected_team_name", null))
    }

    // --- Fakes ---

    class FakeContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

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

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            temp[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            temp[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            toRemove.add(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clear = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clear) data.clear()
            for (key in toRemove) data.remove(key)
            data.putAll(temp)
        }
    }
}
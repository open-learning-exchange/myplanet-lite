package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class DashboardServerPreferencesTest {

    private lateinit var mockContext: FakeContext
    private lateinit var mockPrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        mockPrefs = FakeSharedPreferences()
        mockContext = FakeContext(mockPrefs)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `getServerBaseUrl returns null when nothing is saved`() {
        assertNull(DashboardServerPreferences.getServerBaseUrl(mockContext))
    }

    @Test
    fun `getServerBaseUrl returns url when saved`() {
        mockPrefs.edit().putString(DashboardServerPreferences.KEY_SERVER_URL, "https://example.com").apply()
        assertEquals("https://example.com", DashboardServerPreferences.getServerBaseUrl(mockContext))
    }

    @Test
    fun `getServerBaseUrl returns null when saved url is blank`() {
        mockPrefs.edit().putString(DashboardServerPreferences.KEY_SERVER_URL, "   ").apply()
        assertNull(DashboardServerPreferences.getServerBaseUrl(mockContext))
    }

    @Test
    fun `getServerCode returns null when nothing is saved`() {
        assertNull(DashboardServerPreferences.getServerCode(mockContext))
    }

    @Test
    fun `getServerCode returns code when saved`() {
        mockPrefs.edit().putString(DashboardServerPreferences.KEY_SERVER_CODE, "123").apply()
        assertEquals("123", DashboardServerPreferences.getServerCode(mockContext))
    }

    @Test
    fun `getServerCode returns null when saved code is blank`() {
        mockPrefs.edit().putString(DashboardServerPreferences.KEY_SERVER_CODE, "   ").apply()
        assertNull(DashboardServerPreferences.getServerCode(mockContext))
    }

    @Test
    fun `getServerParentCode returns null when nothing is saved`() {
        assertNull(DashboardServerPreferences.getServerParentCode(mockContext))
    }

    @Test
    fun `getServerParentCode returns parent code when saved`() {
        mockPrefs.edit().putString(DashboardServerPreferences.KEY_SERVER_PARENT_CODE, "456").apply()
        assertEquals("456", DashboardServerPreferences.getServerParentCode(mockContext))
    }

    @Test
    fun `getServerParentCode returns null when saved parent code is blank`() {
        mockPrefs.edit().putString(DashboardServerPreferences.KEY_SERVER_PARENT_CODE, "   ").apply()
        assertNull(DashboardServerPreferences.getServerParentCode(mockContext))
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

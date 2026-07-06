package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

@RunWith(AndroidJUnit4::class)
class DashboardServerCatalogTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        SecurePreferencesProvider.injectedPreferences = prefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun `normalizeServerUrl correctly normalizes URLs`() {
        assertEquals("http://myplanet.org", DashboardServerCatalog.normalizeServerUrl("myplanet.org"))
        assertEquals("https://myplanet.org", DashboardServerCatalog.normalizeServerUrl("https://myplanet.org/"))
        assertEquals("http://myplanet.org", DashboardServerCatalog.normalizeServerUrl("  myplanet.org/  "))
        assertEquals("", DashboardServerCatalog.normalizeServerUrl(""))
        assertEquals("", DashboardServerCatalog.normalizeServerUrl("   "))
        assertEquals("", DashboardServerCatalog.normalizeServerUrl("http://"))
    }

    @Test
    fun `baseUrlKey correctly formats keys`() {
        assertEquals("http://myplanet.org", DashboardServerCatalog.baseUrlKey("http://myplanet.org/"))
        assertEquals("https://myplanet.org", DashboardServerCatalog.baseUrlKey("HTTPS://MYPLANET.ORG"))
        assertEquals("", DashboardServerCatalog.baseUrlKey(""))
    }

    @Test
    fun `displayNameFromBaseUrl correctly extracts display names`() {
        assertEquals("myplanet.org", DashboardServerCatalog.displayNameFromBaseUrl("https://myplanet.org"))
        assertEquals("myplanet.org", DashboardServerCatalog.displayNameFromBaseUrl("http://myplanet.org/foo/bar"))
        assertEquals("invalid-url", DashboardServerCatalog.displayNameFromBaseUrl("invalid-url"))
    }

    @Test
    fun `addObservedServer adds new server and returns true`() {
        val added = DashboardServerCatalog.addObservedServer(context, "myplanet.org", "My Planet", "US")
        assertTrue(added)

        val prefs = SecurePreferencesProvider.injectedPreferences!!
        val jsonString = prefs.getString("custom_servers", null)
        val array = JSONArray(jsonString)
        assertEquals(1, array.length())
        val obj = array.getJSONObject(0)
        assertEquals("My Planet", obj.getString("displayName"))
        assertEquals("http://myplanet.org", obj.getString("baseUrl"))
        assertEquals("US", obj.getString("countryCode"))
    }

    @Test
    fun `addObservedServer returns false for empty URL`() {
        val added = DashboardServerCatalog.addObservedServer(context, "")
        assertFalse(added)
    }

    @Test
    fun `addObservedServer prevents duplicates`() {
        DashboardServerCatalog.addObservedServer(context, "myplanet.org", "My Planet", "US")
        val addedAgain = DashboardServerCatalog.addObservedServer(context, "http://myplanet.org/", "My Planet 2", "CA")
        assertFalse(addedAgain)

        val prefs = SecurePreferencesProvider.injectedPreferences!!
        val jsonString = prefs.getString("custom_servers", null)
        val array = JSONArray(jsonString)
        assertEquals(1, array.length())
    }

    @Test
    fun `addObservedServer appends to existing list`() {
        DashboardServerCatalog.addObservedServer(context, "myplanet.org", "My Planet", "US")
        DashboardServerCatalog.addObservedServer(context, "another.org")

        val prefs = SecurePreferencesProvider.injectedPreferences!!
        val jsonString = prefs.getString("custom_servers", null)
        val array = JSONArray(jsonString)
        assertEquals(2, array.length())
        assertEquals("http://another.org", array.getJSONObject(1).getString("baseUrl"))
        assertEquals("another.org", array.getJSONObject(1).getString("displayName")) // Defaulted to host
        assertEquals("GT", array.getJSONObject(1).getString("countryCode")) // Defaulted to GT
    }

    @Test
    fun `addObservedServer loads correctly from existing prefs`() {
        // Prepare some existing valid and invalid prefs
        val prefs = SecurePreferencesProvider.injectedPreferences!!
        val array = JSONArray()
        array.put(JSONObject().put("baseUrl", "http://existing.org").put("countryCode", "uk").put("displayName", "Existing"))
        array.put(JSONObject().put("baseUrl", "  ").put("countryCode", "uk")) // Invalid - empty url
        array.put(JSONObject().put("baseUrl", "http://invalid.org").put("countryCode", "  ")) // Invalid - empty country
        array.put(JSONObject().put("baseUrl", "http://no-display.org").put("countryCode", "ca")) // Valid - will use baseUrl as displayName
        array.put(JSONObject().put("baseUrl", "http://empty-display.org").put("countryCode", "mx").put("displayName", "   ")) // Valid - empty display -> baseUrl
        prefs.edit().putString("custom_servers", array.toString()).commit()

        val added = DashboardServerCatalog.addObservedServer(context, "new.org")
        assertTrue(added)

        val newJsonString = prefs.getString("custom_servers", null)
        val newArray = JSONArray(newJsonString)

        // We expect the 3 valid existing ones + 1 new one = 4
        assertEquals(4, newArray.length())

        assertEquals("Existing", newArray.getJSONObject(0).getString("displayName"))
        assertEquals("http://existing.org", newArray.getJSONObject(0).getString("baseUrl"))
        assertEquals("UK", newArray.getJSONObject(0).getString("countryCode"))

        assertEquals("http://no-display.org", newArray.getJSONObject(1).getString("displayName"))
        assertEquals("http://no-display.org", newArray.getJSONObject(1).getString("baseUrl"))
        assertEquals("CA", newArray.getJSONObject(1).getString("countryCode"))

        assertEquals("http://empty-display.org", newArray.getJSONObject(2).getString("displayName"))
        assertEquals("http://empty-display.org", newArray.getJSONObject(2).getString("baseUrl"))
        assertEquals("MX", newArray.getJSONObject(2).getString("countryCode"))

        assertEquals("new.org", newArray.getJSONObject(3).getString("displayName"))
        assertEquals("http://new.org", newArray.getJSONObject(3).getString("baseUrl"))
        assertEquals("GT", newArray.getJSONObject(3).getString("countryCode"))
    }

    @Test
    fun `addObservedServer fails gracefully with malformed json`() {
        val prefs = SecurePreferencesProvider.injectedPreferences!!
        prefs.edit().putString("custom_servers", "malformed_json_here").commit()

        val added = DashboardServerCatalog.addObservedServer(context, "myplanet.org")
        assertTrue(added) // Should ignore malformed JSON and just add the new one

        val newJsonString = prefs.getString("custom_servers", null)
        val newArray = JSONArray(newJsonString)
        assertEquals(1, newArray.length())
    }

    @Test
    fun `addObservedServer returns false if key is empty`() {
        // normalizeServerUrl("http://") returns ""
        val added = DashboardServerCatalog.addObservedServer(context, "http://")
        assertFalse(added)
    }
}

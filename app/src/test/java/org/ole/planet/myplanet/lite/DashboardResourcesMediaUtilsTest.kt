package org.ole.planet.myplanet.lite

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun applyWebCompatibleResourceDefaults_addsMissingFields() {
        val payload = JSONObject()

        DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(payload)

        assertTrue(payload.has("author"))
        assertEquals("", payload.getString("author"))

        assertTrue(payload.has("year"))
        assertEquals("", payload.getString("year"))

        assertTrue(payload.has("publisher"))
        assertEquals("", payload.getString("publisher"))

        assertTrue(payload.has("linkToLicense"))
        assertEquals("", payload.getString("linkToLicense"))

        assertTrue(payload.has("openWith"))
        assertEquals("", payload.getString("openWith"))

        assertTrue(payload.has("resourceFor"))
        val resourceForArray = payload.getJSONArray("resourceFor")
        assertEquals(0, resourceForArray.length())

        assertTrue(payload.has("medium"))
        assertEquals("", payload.getString("medium"))

        assertTrue(payload.has("resourceType"))
        assertEquals("", payload.getString("resourceType"))
    }

    @Test
    fun applyWebCompatibleResourceDefaults_preservesExistingFields() {
        val payload = JSONObject().apply {
            put("author", "Test Author")
            put("year", "2023")
            put("publisher", "Test Publisher")
            put("linkToLicense", "http://license.com")
            put("openWith", "PDF Viewer")
            put("resourceFor", JSONArray().apply { put("Test Group") })
            put("medium", "digital")
            put("resourceType", "book")
        }

        DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(payload)

        assertEquals("Test Author", payload.getString("author"))
        assertEquals("2023", payload.getString("year"))
        assertEquals("Test Publisher", payload.getString("publisher"))
        assertEquals("http://license.com", payload.getString("linkToLicense"))
        assertEquals("PDF Viewer", payload.getString("openWith"))

        val resourceForArray = payload.getJSONArray("resourceFor")
        assertEquals(1, resourceForArray.length())
        assertEquals("Test Group", resourceForArray.getString(0))

        assertEquals("digital", payload.getString("medium"))
        assertEquals("book", payload.getString("resourceType"))
    }
}

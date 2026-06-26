package org.ole.planet.myplanet.lite

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SurveyWizardSubmissionExtensionsTest {

    @Test
    fun optIntOrNull_nullJsonObject_returnsNull() {
        val json: JSONObject? = null
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_missingKey_returnsNull() {
        val json = JSONObject()
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_nullValue_returnsNull() {
        val json = JSONObject().apply { put("key", JSONObject.NULL) }
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_validNumber_returnsInt() {
        val json = JSONObject().apply { put("key", 42) }
        assertEquals(42, json.optIntOrNull("key"))

        val jsonDouble = JSONObject().apply { put("key", 42.5) }
        assertEquals(42, jsonDouble.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_validString_returnsInt() {
        val json = JSONObject().apply { put("key", "42") }
        assertEquals(42, json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_invalidString_returnsNull() {
        val json = JSONObject().apply { put("key", "not a number") }
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_booleanValue_returnsNull() {
        val json = JSONObject().apply { put("key", true) }
        assertNull(json.optIntOrNull("key"))
    }
}

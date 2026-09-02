package org.ole.planet.myplanet.lite.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class PlanetAppIdentityTest {

    @Test
    fun `test app id matches the value planet reports expect`() {
        assertEquals("app", PlanetAppIdentity.FIELD_NAME)
        assertEquals("myplanet-lite", PlanetAppIdentity.APP_ID)
    }

    @Test
    fun `test putPlanetAppId stamps the app field onto a payload`() {
        val payload = JSONObject().put("type", "login")

        val result = payload.putPlanetAppId()

        assertEquals("myplanet-lite", payload.getString("app"))
        assertEquals("login", payload.getString("type"))
        assertEquals(payload, result)
    }

    @Test
    fun `test putPlanetAppId overwrites a stale app value`() {
        val payload = JSONObject().put("app", "myplanet")

        payload.putPlanetAppId()

        assertEquals("myplanet-lite", payload.getString("app"))
    }
}

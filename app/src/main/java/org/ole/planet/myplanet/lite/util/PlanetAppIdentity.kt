package org.ole.planet.myplanet.lite.util

import org.json.JSONObject

object PlanetAppIdentity {
    const val FIELD_NAME = "app"
    const val APP_ID = "myplanet-lite"
}

fun JSONObject.putPlanetAppId(): JSONObject =
    put(PlanetAppIdentity.FIELD_NAME, PlanetAppIdentity.APP_ID)

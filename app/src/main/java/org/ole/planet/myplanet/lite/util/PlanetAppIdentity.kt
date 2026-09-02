/*
 * Author: mutugiii
 * Email: mutugimutuma@gmail.com
 * Creation date: 2026-09-02
 */

package org.ole.planet.myplanet.lite.util

import org.json.JSONObject

/*
 * Identifies the app that authored a document on Planet.
 *
 * Planet's reports split activity by an "app" field whose value is one of
 * "planet", "myplanet" or "myplanet-lite". When the field is absent Planet falls
 * back to treating any document carrying an "androidId" as myPlanet, so every
 * activity document this app writes must carry this value explicitly — a
 * document written without it can never be re-attributed.
 */
object PlanetAppIdentity {
    /** JSON key Planet reads the authoring app from. */
    const val FIELD_NAME = "app"

    /** Value Planet expects for documents written by myPlanet Lite. */
    const val APP_ID = "myplanet-lite"
}

/*
 * Stamps [PlanetAppIdentity.APP_ID] onto an activity payload built with JSONObject.
 */
fun JSONObject.putPlanetAppId(): JSONObject = put(PlanetAppIdentity.FIELD_NAME, PlanetAppIdentity.APP_ID)

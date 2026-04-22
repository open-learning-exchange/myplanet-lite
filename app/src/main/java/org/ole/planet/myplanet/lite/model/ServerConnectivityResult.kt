package org.ole.planet.myplanet.lite.model

data class ServerConnectivityResult(
    val reachable: Boolean,
    val parentCode: String? = null,
    val code: String? = null
)

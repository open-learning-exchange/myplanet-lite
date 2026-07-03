package org.ole.planet.myplanet.lite.dashboard

data class EnrichedJoinRequest(
    val id: String,
    val username: String,
    val fullName: String,
    val hasAvatar: Boolean,
    val request: JoinRequestDocument,
)

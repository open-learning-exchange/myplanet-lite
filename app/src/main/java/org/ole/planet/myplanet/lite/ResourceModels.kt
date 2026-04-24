package org.ole.planet.myplanet.lite

internal data class ResourceUi(
    val id: String,
    val filename: String,
    val name: String,
    val type: String,
    val date: String,
    val createdDate: Long?,
    val isDownloaded: Boolean,
    val isDownloadable: Boolean,
    val isTeamResource: Boolean
)

internal enum class ResourceSortBy {
    NAME,
    DATE
}

internal fun ResourceUi.uniqueKey(): String = storedResourceKey(id, filename, isTeamResource)

internal fun ResourceUi.resourceIdentityKey(): String = id.takeIf { it.isNotBlank() } ?: uniqueKey()

internal fun storedResourceKey(id: String, filename: String, isTeamResource: Boolean): String {
    val bucket = if (isTeamResource) "team" else "community"
    return "$bucket::$id::$filename"
}

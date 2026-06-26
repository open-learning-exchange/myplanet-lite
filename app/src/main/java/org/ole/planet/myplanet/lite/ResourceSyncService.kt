package org.ole.planet.myplanet.lite

import java.util.Locale
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository
import org.ole.planet.myplanet.lite.util.DateUtils

internal data class MainResourcesFetchResult(
    val page: List<ResourceUi>
)

internal data class TeamResourcesFetchParams(
    val baseUrl: String,
    val sessionCookie: String?,
    val username: String?,
    val password: String?,
    val teamId: String,
    val searchQuery: String,
    val mediaTypeFilter: String?,
    val isSortDescending: Boolean,
    val limit: Int,
    val downloadedResources: List<ResourceUi>
)

internal class ResourceSyncService(
    private val repository: DashboardResourcesRepository,
    private val downloadService: ResourceDownloadService
) {
    suspend fun fetchCommunityResources(
        baseUrl: String,
        sessionCookie: String?,
        searchQuery: String,
        mediaTypeFilter: String?,
        isSortDescending: Boolean,
        skip: Int,
        limit: Int,
        existingKeys: Set<String>
    ): MainResourcesFetchResult {
        val result = repository.fetchCommunityResources(
            baseUrl = baseUrl,
            sessionCookie = sessionCookie,
            searchQuery = searchQuery,
            mediaTypeFilter = mediaTypeFilter,
            sortBy = "title",
            sortDescending = isSortDescending,
            skip = skip,
            limit = limit
        )
        val page = result.getOrDefault(emptyList())
        val mutableKeys = existingKeys.toMutableSet()
        val items = page.map { resource ->
            val id = resource.id?.trim().orEmpty()
            val filename = resource.filename?.trim().orEmpty()
            ResourceUi(
                id = id,
                filename = filename,
                name = resource.title?.takeIf { it.isNotBlank() }
                    ?: resource.filename?.takeIf { it.isNotBlank() }
                    ?: "-",
                type = resource.mediaType?.uppercase(Locale.ROOT) ?: "PDF",
                date = DateUtils.toDisplayDate(resource.createdDate),
                createdDate = resource.createdDate,
                isDownloaded = downloadService.findLocalResourceFile(id, filename, isTeamResource = false)?.exists() == true,
                isDownloadable = ResourceSearchEngine.parseIsDownloadable(resource.isDownloadable),
                isTeamResource = false
            )
        }.filter { item ->
            val key = item.resourceIdentityKey()
            if (mutableKeys.contains(key)) false else {
                mutableKeys.add(key)
                true
            }
        }
        return MainResourcesFetchResult(items)
    }

    suspend fun fetchTeamResources(
        params: TeamResourcesFetchParams
    ): List<ResourceUi> {
        val result = repository.fetchTeamResources(
            baseUrl = params.baseUrl,
            sessionCookie = params.sessionCookie,
            username = params.username,
            password = params.password,
            teamId = params.teamId,
            searchQuery = params.searchQuery,
            mediaTypeFilter = params.mediaTypeFilter,
            sortBy = "title",
            sortDescending = params.isSortDescending,
            limit = params.limit
        )
        val page = result.getOrDefault(emptyList())
        val allRemoteItems = page.map { resource ->
            val id = resource.id?.trim().orEmpty()
            val filename = resource.filename?.trim().orEmpty()
            ResourceUi(
                id = id,
                filename = filename,
                name = resource.title?.takeIf { it.isNotBlank() }
                    ?: resource.filename?.takeIf { it.isNotBlank() }
                    ?: "-",
                type = resource.mediaType?.uppercase(Locale.ROOT) ?: "PDF",
                date = DateUtils.toDisplayDate(resource.createdDate),
                createdDate = resource.createdDate,
                isDownloaded = downloadService.findLocalResourceFile(id, filename, isTeamResource = true)?.exists() == true,
                isDownloadable = ResourceSearchEngine.parseIsDownloadable(resource.isDownloadable),
                isTeamResource = true
            )
        }
        val remoteKeys = allRemoteItems.map { it.resourceIdentityKey() }.toSet()
        val downloaded = params.downloadedResources.filter { remoteKeys.contains(it.resourceIdentityKey()) }
        val existingKeys = downloaded.map { it.resourceIdentityKey() }.toMutableSet()
        val remoteItems = allRemoteItems.filter { item ->
            val key = item.resourceIdentityKey()
            if (existingKeys.contains(key)) false else {
                existingKeys.add(key)
                true
            }
        }
        return downloaded + remoteItems
    }

    suspend fun downloadResource(
        baseUrl: String,
        sessionCookie: String?,
        item: ResourceUi,
        onProgress: ((Int?) -> Unit)? = null
    ): Boolean {
        val bytesResult = repository.downloadResourceBytes(
            baseUrl = baseUrl,
            sessionCookie = sessionCookie,
            resourceId = item.id,
            filename = item.filename,
            onProgress = onProgress
        )
        return bytesResult.fold(
            onSuccess = { bytes ->
                val localFile = downloadService.saveDownloadedResourceFile(item, bytes)
                if (localFile != null) {
                    downloadService.upsertDownloadedResource(item.copy(isDownloaded = true))
                    true
                } else {
                    false
                }
            },
            onFailure = { false }
        )
    }
}

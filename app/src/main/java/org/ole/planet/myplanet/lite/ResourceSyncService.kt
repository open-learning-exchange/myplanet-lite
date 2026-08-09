package org.ole.planet.myplanet.lite

import java.util.Locale
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository
import org.ole.planet.myplanet.lite.util.DateUtils

internal data class MainResourcesFetchResult(
    val page: List<ResourceUi>
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
            mapToUiModel(resource, isTeamResource = false)
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
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        teamId: String,
        searchQuery: String,
        mediaTypeFilter: String?,
        isSortDescending: Boolean,
        limit: Int,
        downloadedResources: List<ResourceUi>
    ): List<ResourceUi> {
        val result = repository.fetchTeamResources(
            DashboardResourcesRepository.TeamResourcesRequest(
                baseUrl = baseUrl,
                sessionCookie = sessionCookie,
                username = username,
                password = password,
                teamId = teamId,
                searchQuery = searchQuery,
                mediaTypeFilter = mediaTypeFilter,
                sortBy = "title",
                sortDescending = isSortDescending,
                limit = limit
            )
        )
        val page = result.getOrDefault(emptyList())
        val allRemoteItems = page.map { resource ->
            mapToUiModel(resource, isTeamResource = true)
        }
        val remoteKeys = allRemoteItems.map { it.resourceIdentityKey() }.toSet()
        val downloaded = downloadedResources.filter { remoteKeys.contains(it.resourceIdentityKey()) }
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

    private fun mapToUiModel(
        resource: DashboardResourcesRepository.ResourceDocument,
        isTeamResource: Boolean
    ): ResourceUi {
        val id = resource.id?.trim().orEmpty()
        val filename = resource.filename?.trim().orEmpty()
        return ResourceUi(
            id = id,
            filename = filename,
            name = resource.title?.takeIf { it.isNotBlank() }
                ?: resource.filename?.takeIf { it.isNotBlank() }
                ?: "-",
            type = resource.mediaType?.uppercase(Locale.ROOT) ?: "PDF",
            date = DateUtils.toDisplayDate(resource.createdDate),
            createdDate = resource.createdDate,
            isDownloaded = downloadService.findLocalResourceFile(id, filename, isTeamResource = isTeamResource)?.exists() == true,
            isDownloadable = ResourceSearchEngine.parseIsDownloadable(resource.isDownloadable),
            isTeamResource = isTeamResource
        )
    }
}

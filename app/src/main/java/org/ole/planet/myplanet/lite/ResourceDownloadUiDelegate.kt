package org.ole.planet.myplanet.lite

internal object ResourceDownloadUiDelegate {
    suspend fun downloadResource(
        baseUrl: String,
        sessionCookie: String?,
        item: ResourceUi,
        resourceSyncService: ResourceSyncService
    ): Boolean {
        return resourceSyncService.downloadResource(
            baseUrl = baseUrl,
            sessionCookie = sessionCookie,
            item = item
        )
    }

    fun deleteDownloadedResource(
        downloadService: ResourceDownloadService,
        item: ResourceUi
    ) {
        downloadService.findLocalResourceFile(item.id, item.filename, item.isTeamResource)?.delete()
        downloadService.removeDownloadedResource(item)
    }

    fun markResourceDownloadState(
        mainResourcesItems: MutableList<ResourceUi>,
        teamResourcesItems: MutableList<ResourceUi>,
        item: ResourceUi,
        downloaded: Boolean,
        selectedSortBy: ResourceSortBy,
        isSortDescending: Boolean
    ) {
        updateDownloadStateInList(mainResourcesItems, item, downloaded, selectedSortBy, isSortDescending)
        updateDownloadStateInList(teamResourcesItems, item, downloaded, selectedSortBy, isSortDescending)
    }

    fun updateDownloadStateInList(
        items: MutableList<ResourceUi>,
        target: ResourceUi,
        downloaded: Boolean,
        selectedSortBy: ResourceSortBy,
        isSortDescending: Boolean
    ) {
        val index = items.indexOfFirst { it.uniqueKey() == target.uniqueKey() }
        if (index >= 0) {
            val existing = items[index]
            items[index] = existing.copy(isDownloaded = downloaded)
        } else if (downloaded) {
            items.add(0, target.copy(isDownloaded = true))
        }
        ResourceSearchEngine.sortResources(items, selectedSortBy, isSortDescending)
    }

    fun loadDownloadedResourcesFiltered(
        downloadService: ResourceDownloadService,
        isTeamResourcesTab: Boolean,
        searchQuery: String,
        selectedMediaType: String?
    ): List<ResourceUi> {
        return ResourceSearchEngine.filterByTabQueryAndType(
            resources = downloadService.loadDownloadedResources(),
            isTeamResourcesTab = isTeamResourcesTab,
            query = searchQuery,
            selectedMediaType = selectedMediaType
        )
    }
}

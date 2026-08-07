package org.ole.planet.myplanet.lite

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials

internal fun DashboardResourcesPageFragment.refreshContent(forceRefresh: Boolean = false) {
        val content = contentView ?: return
        val empty = emptyView ?: return
        val list = resourcesList ?: return
        val offlineMode = (activity as? DashboardActivity)?.isOfflineModeActive() == true
        addResourceFab?.isVisible = !offlineMode && (!isTeamResourcesTab || hasSelectedTeam())

        if (isTeamResourcesTab) {
            refreshTeamResources(content, empty, list, offlineMode, forceRefresh)
        } else {
            refreshMainResources(content, empty, list, offlineMode, forceRefresh)
        }
    }

private fun DashboardResourcesPageFragment.refreshTeamResources(
    content: View,
    empty: TextView,
    list: RecyclerView,
    offlineMode: Boolean,
    forceRefresh: Boolean
) {
    if (offlineMode) {
        content.isVisible = true
        empty.isVisible = false
        showDownloadedResourcesOnly(list)
        swipeRefreshLayout?.isRefreshing = false
        return
    }
    if (!hasSelectedTeam()) {
        content.isVisible = false
        empty.text = getString(R.string.dashboard_teams_select_team_hint)
        empty.isVisible = true
        swipeRefreshLayout?.isRefreshing = false
        return
    }

    content.isVisible = true
    empty.isVisible = false
    if (forceRefresh || !hasLoadedTeamResources) {
        loadTeamResources()
        return
    }
    val currentAdapter = list.adapter as? DashboardResourcesPageFragment.ResourceExplorerAdapter
    if (currentAdapter == null) {
        list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(teamResourcesItems.toList(), resourceDownloadProgress, ::openResource, ::onSecondaryAction)
    } else {
        currentAdapter.replaceResources(teamResourcesItems)
    }
    swipeRefreshLayout?.isRefreshing = false
}

private fun DashboardResourcesPageFragment.refreshMainResources(
    content: View,
    empty: TextView,
    list: RecyclerView,
    offlineMode: Boolean,
    forceRefresh: Boolean
) {
    content.isVisible = true
    empty.isVisible = false
    if (offlineMode) {
        showDownloadedResourcesOnly(list)
        swipeRefreshLayout?.isRefreshing = false
        return
    }
    if (forceRefresh || !hasLoadedMainResources) {
        resetMainResourcesAndLoad()
        return
    }
    val currentAdapter = list.adapter as? DashboardResourcesPageFragment.ResourceExplorerAdapter
    if (currentAdapter == null) {
        list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), resourceDownloadProgress, ::openResource, ::onSecondaryAction)
    } else {
        currentAdapter.replaceResources(mainResourcesItems)
    }
    swipeRefreshLayout?.isRefreshing = false
}

internal fun DashboardResourcesPageFragment.showDownloadedResourcesOnly(list: RecyclerView) {
        val downloadedResources = loadDownloadedResourcesFiltered().toMutableList()
        ResourceSearchEngine.sortResources(downloadedResources, selectedSortBy, isSortDescending)
        if (isTeamResourcesTab) {
            teamResourcesItems.clear()
            teamResourcesItems.addAll(downloadedResources)
        } else {
            mainResourcesItems.clear()
            mainResourcesItems.addAll(downloadedResources)
        }
        list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(downloadedResources, resourceDownloadProgress, ::openResource, ::onSecondaryAction)
    }

internal fun DashboardResourcesPageFragment.resetMainResourcesAndLoad() {
        mainResourcesSkip = 0
        hasMoreMainResources = true
        isLoadingMainResources = false
        hasLoadedMainResources = false
        mainResourcesItems.clear()
        mainResourcesItems.addAll(loadDownloadedResourcesFiltered())
        ResourceSearchEngine.sortResources(mainResourcesItems, selectedSortBy, isSortDescending)
        resourcesList?.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), resourceDownloadProgress, ::openResource, ::onSecondaryAction)
        loadMoreMainResources()
    }

internal fun DashboardResourcesPageFragment.loadMoreMainResources() {
        if (isLoadingMainResources || !hasMoreMainResources) {
            return
        }
        val context = context ?: return
        val list = resourcesList ?: return
        val resolvedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        if (resolvedBaseUrl.isNullOrBlank()) {
            list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), resourceDownloadProgress, ::openResource, ::onSecondaryAction)
            swipeRefreshLayout?.isRefreshing = false
            return
        }
        baseUrl = resolvedBaseUrl

        isLoadingMainResources = true
        lifecycleScope.launch {
            sessionCookie = runCatching {
                AuthDependencies.provideAuthService(requireContext().applicationContext, resolvedBaseUrl)
                    .getStoredToken()
            }.getOrNull()

            val fetchResult = resourceSyncService.fetchCommunityResources(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                searchQuery = searchQuery,
                mediaTypeFilter = selectedMediaType,
                isSortDescending = isSortDescending,
                skip = mainResourcesSkip,
                limit = DashboardResourcesPageFragment.MAIN_RESOURCES_PAGE_SIZE,
                existingKeys = mainResourcesItems.map { it.resourceIdentityKey() }.toSet()
            )
            val items = fetchResult.page
            isLoadingMainResources = false
            if (isAdded) {
                processMainResourcesResult(list, items)
            }
        }
    }

private fun DashboardResourcesPageFragment.processMainResourcesResult(
    list: RecyclerView,
    items: List<ResourceUi>
) {
    mainResourcesItems.addAll(items)
    ResourceSearchEngine.sortResources(mainResourcesItems, selectedSortBy, isSortDescending)
    val currentAdapter = list.adapter as? DashboardResourcesPageFragment.ResourceExplorerAdapter
    if (currentAdapter == null || mainResourcesSkip == 0) {
        list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            mainResourcesItems.toList(),
            resourceDownloadProgress,
            ::openResource,
            ::onSecondaryAction
        )
    } else {
        currentAdapter.replaceResources(mainResourcesItems)
    }
    mainResourcesSkip += items.size
    hasMoreMainResources = items.size >= DashboardResourcesPageFragment.MAIN_RESOURCES_PAGE_SIZE
    hasLoadedMainResources = true
    swipeRefreshLayout?.isRefreshing = false
}

internal fun DashboardResourcesPageFragment.loadTeamResources() {
        if (isLoadingTeamResources) {
            return
        }
        val context = context ?: return
        val list = resourcesList ?: return
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(context).orEmpty()
        val resolvedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        if (teamId.isBlank() || resolvedBaseUrl.isNullOrBlank()) {
            teamResourcesItems.clear()
            list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(emptyList<ResourceUi>(), resourceDownloadProgress, ::openResource, ::onSecondaryAction)
            swipeRefreshLayout?.isRefreshing = false
            return
        }
        baseUrl = resolvedBaseUrl
        isLoadingTeamResources = true
        fetchAndDisplayTeamResources(resolvedBaseUrl, teamId, credentials, list)
    }

    private fun DashboardResourcesPageFragment.fetchAndDisplayTeamResources(
        resolvedBaseUrl: String,
        teamId: String,
        credentials: StoredCredentials?,
        list: RecyclerView
    ) {
        lifecycleScope.launch {
            sessionCookie = runCatching {
                AuthDependencies.provideAuthService(requireContext().applicationContext, resolvedBaseUrl)
                    .getStoredToken()
            }.getOrNull()

            val mergedResources = resourceSyncService.fetchTeamResources(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                username = credentials?.username,
                password = credentials?.password,
                teamId = teamId,
                searchQuery = searchQuery,
                mediaTypeFilter = selectedMediaType,
                isSortDescending = isSortDescending,
                limit = DashboardResourcesPageFragment.MAIN_RESOURCES_PAGE_SIZE,
                downloadedResources = loadDownloadedResourcesFiltered()
            )
            isLoadingTeamResources = false
            if (isAdded) {
                processTeamResourcesResult(list, mergedResources)
            }
        }
    }

private fun DashboardResourcesPageFragment.processTeamResourcesResult(
    list: RecyclerView,
    mergedResources: List<ResourceUi>
) {
    teamResourcesItems.clear()
    teamResourcesItems.addAll(mergedResources)
    ResourceSearchEngine.sortResources(teamResourcesItems, selectedSortBy, isSortDescending)
    hasLoadedTeamResources = true
    list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
        teamResourcesItems.toList(),
        resourceDownloadProgress,
        ::openResource,
        ::onSecondaryAction
    )
    swipeRefreshLayout?.isRefreshing = false
}

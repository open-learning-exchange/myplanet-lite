package org.ole.planet.myplanet.lite

import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal fun DashboardResourcesPageFragment.refreshContent(forceRefresh: Boolean = false) {
        val content = contentView ?: return
        val empty = emptyView ?: return
        val list = resourcesList ?: return
        val offlineMode = (activity as? DashboardActivity)?.isOfflineModeActive() == true
        addResourceFab?.isVisible = !offlineMode && (!isTeamResourcesTab || hasSelectedTeam())

        if (isTeamResourcesTab) {
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
                list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(teamResourcesItems.toList(), ::openResource, ::onSecondaryAction)
            } else {
                currentAdapter.replaceResources(teamResourcesItems)
            }
            swipeRefreshLayout?.isRefreshing = false
            return
        }

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
            list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
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
        list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(downloadedResources, ::openResource, ::onSecondaryAction)
    }

internal fun DashboardResourcesPageFragment.resetMainResourcesAndLoad() {
        mainResourcesSkip = 0
        hasMoreMainResources = true
        isLoadingMainResources = false
        hasLoadedMainResources = false
        mainResourcesItems.clear()
        mainResourcesItems.addAll(loadDownloadedResourcesFiltered())
        ResourceSearchEngine.sortResources(mainResourcesItems, selectedSortBy, isSortDescending)
        resourcesList?.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
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
            list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
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
                existingKeys = mainResourcesItems.map { it.uniqueKey() }.toSet()
            )
            val items = fetchResult.page
            isLoadingMainResources = false
            if (isAdded) {
                mainResourcesItems.addAll(items)
                ResourceSearchEngine.sortResources(mainResourcesItems, selectedSortBy, isSortDescending)
                val currentAdapter = list.adapter as? DashboardResourcesPageFragment.ResourceExplorerAdapter
                if (currentAdapter == null || mainResourcesSkip == 0) {
                    list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
                } else {
                    currentAdapter.replaceResources(mainResourcesItems)
                }
                mainResourcesSkip += items.size
                hasMoreMainResources = items.size >= DashboardResourcesPageFragment.MAIN_RESOURCES_PAGE_SIZE
                hasLoadedMainResources = true
                swipeRefreshLayout?.isRefreshing = false
            }
        }
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
            list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(emptyList<ResourceUi>(), ::openResource, ::onSecondaryAction)
            swipeRefreshLayout?.isRefreshing = false
            return
        }
        baseUrl = resolvedBaseUrl
        isLoadingTeamResources = true
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
                teamResourcesItems.clear()
                teamResourcesItems.addAll(mergedResources)
                ResourceSearchEngine.sortResources(teamResourcesItems, selectedSortBy, isSortDescending)
                hasLoadedTeamResources = true
                list.adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(teamResourcesItems.toList(), ::openResource, ::onSecondaryAction)
                swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

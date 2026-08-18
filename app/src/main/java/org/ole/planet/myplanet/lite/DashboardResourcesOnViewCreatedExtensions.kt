package org.ole.planet.myplanet.lite

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

internal fun DashboardResourcesPageFragment.setupResourcesView(view: View) {
    val searchInput: TextInputEditText = view.findViewById(R.id.resourcesSearchInput)
    val typeSpinner: Spinner = view.findViewById(R.id.resourcesTypeSpinner)
    val sortBySpinner: Spinner = view.findViewById(R.id.resourcesSortBySpinner)
    val sortOrderToggle: ImageButton = view.findViewById(R.id.resourcesSortOrderToggle)
    val fab = view.findViewById<FloatingActionButton>(R.id.addResourceFab)
    val list: RecyclerView = view.findViewById(R.id.resourcesList)
    val content: LinearLayout = view.findViewById(R.id.resourcesExplorerContent)
    val empty: TextView = view.findViewById(R.id.resourcesEmptyView)
    val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.resourcesSwipeRefresh)

    addResourceFab = fab
    contentView = content
    emptyView = empty
    resourcesList = list
    swipeRefreshLayout = swipeRefresh
    uploadLoadingView = view.findViewById(R.id.resourcesUploadLoading)

    setupSearchInput(searchInput)
    setupTypeSpinner(typeSpinner)
    setupSortBySpinner(sortBySpinner)
    setupSortOrderToggle(sortOrderToggle)
    setupFab(fab)
    setupList(list)
    setupSwipeRefresh(swipeRefresh)

    refreshContent()
}

private fun DashboardResourcesPageFragment.setupSearchInput(searchInput: TextInputEditText) {
    searchInput.hint = getString(R.string.dashboard_resources_filter_name_hint)
    searchInput.setOnEditorActionListener { _, actionId, event ->
        val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
        val isEnterKey =
            event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
        if (isSearchAction || isEnterKey) {
            val query = searchInput.text?.toString().orEmpty().trim()
            if (query != searchQuery) {
                searchQuery = query
                hasLoadedMainResources = false
                hasLoadedTeamResources = false
            }
            refreshContent(forceRefresh = true)
            true
        } else {
            false
        }
    }
}

private fun DashboardResourcesPageFragment.setupTypeSpinner(typeSpinner: Spinner) {
    typeSpinner.adapter = ArrayAdapter(
        requireContext(),
        android.R.layout.simple_spinner_dropdown_item,
        listOf(
            getString(R.string.dashboard_resources_filter_type_all),
            getString(R.string.dashboard_resources_filter_type_pdf),
            getString(R.string.dashboard_resources_filter_type_video),
            getString(R.string.dashboard_resources_filter_type_image),
            getString(R.string.course_wizard_attachment_audio)
        )
    )
    typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val nextType = when (position) {
                1 -> "pdf"
                2 -> "video"
                3 -> "image"
                4 -> "audio"
                else -> null
            }
            if (nextType != selectedMediaType) {
                selectedMediaType = nextType
                hasLoadedMainResources = false
                hasLoadedTeamResources = false
                refreshContent(forceRefresh = true)
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}

private fun DashboardResourcesPageFragment.setupSortBySpinner(sortBySpinner: Spinner) {
    sortBySpinner.adapter = ArrayAdapter(
        requireContext(),
        android.R.layout.simple_spinner_dropdown_item,
        listOf(
            getString(R.string.dashboard_resources_sort_by_name),
            getString(R.string.dashboard_resources_sort_by_date)
        )
    )
    sortBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val nextSortBy = if (position == 1) ResourceSortBy.DATE else ResourceSortBy.NAME
            if (nextSortBy != selectedSortBy) {
                selectedSortBy = nextSortBy
                hasLoadedMainResources = false
                hasLoadedTeamResources = false
                refreshContent(forceRefresh = true)
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
}

private fun DashboardResourcesPageFragment.updateSortOrderIcon(sortOrderToggle: ImageButton) {
    val icon = if (isSortDescending) {
        R.drawable.ic_sort_descending_24
    } else {
        R.drawable.ic_sort_ascending_24
    }
    sortOrderToggle.setImageResource(icon)
}

private fun DashboardResourcesPageFragment.setupSortOrderToggle(sortOrderToggle: ImageButton) {
    updateSortOrderIcon(sortOrderToggle)
    sortOrderToggle.setOnClickListener {
        isSortDescending = !isSortDescending
        updateSortOrderIcon(sortOrderToggle)
        hasLoadedMainResources = false
        hasLoadedTeamResources = false
        refreshContent(forceRefresh = true)
    }
}

private fun DashboardResourcesPageFragment.setupFab(fab: FloatingActionButton) {
    fab.setOnClickListener {
        showAddResourceMenu(fab)
    }
}

private fun DashboardResourcesPageFragment.setupList(list: RecyclerView) {
    list.layoutManager = LinearLayoutManager(requireContext())
    list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            super.onScrolled(recyclerView, dx, dy)
            if (dy <= 0 || isTeamResourcesTab || !hasMoreMainResources || isLoadingMainResources) {
                return
            }
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
            val totalItemCount = layoutManager.itemCount
            val lastVisible = layoutManager.findLastVisibleItemPosition()
            if (totalItemCount > 0 && lastVisible >= totalItemCount - 1) {
                loadMoreMainResources()
            }
        }
    })
}

private fun DashboardResourcesPageFragment.setupSwipeRefresh(swipeRefresh: SwipeRefreshLayout) {
    swipeRefresh.setOnRefreshListener {
        refreshContent(forceRefresh = true)
    }
}

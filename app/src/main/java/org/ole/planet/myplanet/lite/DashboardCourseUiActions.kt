package org.ole.planet.myplanet.lite

import android.view.View

fun DashboardCoursePageFragment.showEmptyState(message: String) {
    emptyView.text = message
    emptyView.visibility = View.VISIBLE
    recyclerView.visibility = View.GONE
}

fun DashboardCoursePageFragment.hideEmptyState() {
    emptyView.visibility = View.GONE
    recyclerView.visibility = View.VISIBLE
}

fun DashboardCoursePageFragment.showLoadingOverlay(show: Boolean) {
    val parentOverlay = parentFragment?.view?.findViewById<View>(R.id.dashboardCoursesGlobalLoadingOverlay)
    parentOverlay?.visibility = if (show) View.VISIBLE else View.GONE
    if (parentOverlay == null) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
    } else {
        loadingOverlay.visibility = View.GONE
    }
}

fun DashboardCoursePageFragment.resetPagingState() {
    currentSkip = 0
    hasMorePages = true
    isPaging = false
}
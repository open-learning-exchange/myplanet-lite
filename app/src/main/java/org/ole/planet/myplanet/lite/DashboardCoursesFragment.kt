/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class DashboardCoursesFragment : Fragment(R.layout.fragment_dashboard_courses) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val tabLayout: TabLayout = view.findViewById(R.id.dashboardCoursesTabs)
        val viewPager: ViewPager2 = view.findViewById(R.id.dashboardCoursesViewPager)

        val teamDashboard = arguments?.getBoolean(ARG_TEAM_DASHBOARD) == true
        val pages = if (teamDashboard) listOf(0, 2) else listOf(0, 1)
        viewPager.adapter = CoursesPagerAdapter(this, pages)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text =
                when (pages[position]) {
                    0 -> getString(R.string.dashboard_courses_tab_mine)
                    1 -> getString(R.string.dashboard_courses_tab_all)
                    else -> getString(R.string.dashboard_courses_tab_team)
                }
        }.attach()
    }

    private class CoursesPagerAdapter(
        fragment: Fragment,
        private val pages: List<Int>,
    ) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = pages.size

        override fun createFragment(position: Int) = DashboardCoursePageFragment.newInstance(pages[position])
    }
    companion object {
        private const val ARG_TEAM_DASHBOARD = "team_dashboard"

        fun newTeamsInstance() = DashboardCoursesFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_TEAM_DASHBOARD, true) }
        }
    }

}

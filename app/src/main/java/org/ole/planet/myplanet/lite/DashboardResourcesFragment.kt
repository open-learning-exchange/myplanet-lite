package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class DashboardResourcesFragment : Fragment(R.layout.fragment_dashboard_resources) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tabLayout: TabLayout = view.findViewById(R.id.dashboardResourcesTabs)
        val viewPager: ViewPager2 = view.findViewById(R.id.dashboardResourcesViewPager)

        viewPager.adapter = ResourcesPagerAdapter(this)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.dashboard_resources_tab_resources)
                else -> getString(R.string.dashboard_resources_tab_team_resources)
            }
        }.attach()
    }

    private class ResourcesPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 2

        override fun createFragment(position: Int) = DashboardResourcesPageFragment.newInstance(
            isTeamResources = position == 1
        )
    }
}

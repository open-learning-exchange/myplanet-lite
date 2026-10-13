package org.ole.planet.myplanet.lite

import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class DashboardCoursesFragmentTest {

    private lateinit var controller: ActivityController<FragmentActivity>
    private lateinit var activity: FragmentActivity
    private lateinit var fragment: DashboardCoursesFragment

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(FragmentActivity::class.java)
        activity = controller.get()
        controller.create().start().resume()

        fragment = DashboardCoursesFragment()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment)
            .commitNow()
    }

    @Test
    fun `test view creation and view pager setup`() {
        val view = fragment.view
        assertNotNull(view)

        val tabLayout = view?.findViewById<TabLayout>(R.id.dashboardCoursesTabs)
        assertNotNull(tabLayout)

        val viewPager = view?.findViewById<ViewPager2>(R.id.dashboardCoursesViewPager)
        assertNotNull(viewPager)

        assertEquals(2, tabLayout?.tabCount)
        assertEquals("My courses", tabLayout?.getTabAt(0)?.text)
        assertEquals("Courses", tabLayout?.getTabAt(1)?.text)

        val adapter = viewPager?.adapter
        assertNotNull(adapter)
        assertEquals(2, adapter?.itemCount)
    }

    @Test
    fun `test view pager adapter creates correct fragment`() {
        val view = fragment.view
        assertNotNull(view)

        val viewPager = view?.findViewById<ViewPager2>(R.id.dashboardCoursesViewPager)
        assertNotNull(viewPager)

        val adapter = viewPager?.adapter as androidx.viewpager2.adapter.FragmentStateAdapter
        assertNotNull(adapter)

        val method = androidx.viewpager2.adapter.FragmentStateAdapter::class.java.getDeclaredMethod("createFragment", Int::class.java)
        method.isAccessible = true

        val fragment0 = method.invoke(adapter, 0)
        assertTrue(fragment0 is DashboardCoursePageFragment)
        assertEquals(0, (fragment0 as DashboardCoursePageFragment).arguments?.getInt("tab_position"))

        val fragment1 = method.invoke(adapter, 1)
        assertTrue(fragment1 is DashboardCoursePageFragment)
        assertEquals(1, (fragment1 as DashboardCoursePageFragment).arguments?.getInt("tab_position"))


    }
    @Test
    fun `teams courses contains my courses and team courses`() {
        val teams = DashboardCoursesFragment.newTeamsInstance()
        activity.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, teams).commitNow()
        val tabs = teams.requireView().findViewById<TabLayout>(R.id.dashboardCoursesTabs)
        assertEquals(2, tabs.tabCount)
        assertEquals("My courses", tabs.getTabAt(0)?.text)
        assertEquals("Team courses", tabs.getTabAt(1)?.text)
        val adapter = teams.requireView().findViewById<ViewPager2>(R.id.dashboardCoursesViewPager).adapter
            as androidx.viewpager2.adapter.FragmentStateAdapter
        assertEquals(0, adapter.createFragment(0).requireArguments().getInt("tab_position"))
        assertEquals(2, adapter.createFragment(1).requireArguments().getInt("tab_position"))
    }

}

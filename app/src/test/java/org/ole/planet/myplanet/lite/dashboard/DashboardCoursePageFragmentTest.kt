package org.ole.planet.myplanet.lite.dashboard

import android.os.Bundle
import androidx.fragment.app.testing.launchFragmentInContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Before
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.ole.planet.myplanet.lite.DashboardCoursePageFragment

@RunWith(RobolectricTestRunner::class)
class DashboardCoursePageFragmentTest {

    @Before
    fun setup() {
        SecurePreferencesProvider.injectedPreferences = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getSharedPreferences("test_prefs", android.content.Context.MODE_PRIVATE)
    }

    @Test
    fun `newInstance creates fragment with correct tab position argument`() {
        // Arrange
        val expectedTabPosition = 1

        // Act
        val fragment = DashboardCoursePageFragment.newInstance(expectedTabPosition)

        // Assert
        assertNotNull(fragment)
        assertNotNull(fragment.arguments)
        assertEquals(expectedTabPosition, fragment.requireArguments().getInt("tab_position"))
    }

    @Test
    fun `fragment launches successfully with arguments`() {
        // Arrange
        val expectedTabPosition = 2
        val args = Bundle().apply {
            putInt("tab_position", expectedTabPosition)
        }

        // Act
        val scenario = launchFragmentInContainer<DashboardCoursePageFragment>(
            fragmentArgs = args,
            themeResId = com.google.android.material.R.style.Theme_Material3_Light_NoActionBar
        )

        // Assert
        scenario.onFragment { fragment ->
            assertNotNull(fragment)
            assertEquals(expectedTabPosition, fragment.requireArguments().getInt("tab_position"))
        }
    }
}

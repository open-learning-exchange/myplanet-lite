package org.ole.planet.myplanet.lite

import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.appbar.MaterialToolbar
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.mockito.Mockito
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class PrivacyPolicyActivityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyPlanetLite)
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = Mockito.mock(android.content.SharedPreferences::class.java)
        val editor = Mockito.mock(android.content.SharedPreferences.Editor::class.java)
        Mockito.`when`(org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences?.edit()).thenReturn(editor)
        Mockito.`when`(editor.putString(Mockito.anyString(), Mockito.anyString())).thenReturn(editor)
    }

    private fun buildActivityController(): ActivityController<PrivacyPolicyActivity> {
        return Robolectric.buildActivity(PrivacyPolicyActivity::class.java).also {
            it.get().setTheme(R.style.Theme_MyPlanetLite)
        }.create().start().resume()
    }

    @Test
    fun `activity launches successfully`() {
        val controller = buildActivityController()
        val activity = controller.get()
        assertNotNull(activity)
    }

    @Test
    fun `toolbar and content view are present`() {
        val controller = buildActivityController()
        val activity = controller.get()

        val toolbar: MaterialToolbar = activity.findViewById(R.id.privacyPolicyToolbar)
        val contentView: TextView = activity.findViewById(R.id.privacyPolicyContent)

        assertNotNull("Toolbar should be present", toolbar)
        assertNotNull("Content TextView should be present", contentView)
    }

    @Test
    fun `content view is not empty after markdown is loaded`() {
        val controller = buildActivityController()
        val activity = controller.get()

        val contentView: TextView = activity.findViewById(R.id.privacyPolicyContent)

        assertTrue("Content should not be empty", contentView.text.isNotEmpty())
    }

    @Test
    fun `navigation icon click triggers finish`() {
        val controller = buildActivityController()
        val activity = controller.get()

        val toolbar: MaterialToolbar = activity.findViewById(R.id.privacyPolicyToolbar)

        val shadowToolbar = shadowOf(toolbar as androidx.appcompat.widget.Toolbar)
        shadowToolbar.navigationOnClickListener.onClick(toolbar)

        assertTrue("Activity should be finishing after back navigation", activity.isFinishing)
    }

    @org.junit.After
    fun tearDown() {
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = null
    }
}

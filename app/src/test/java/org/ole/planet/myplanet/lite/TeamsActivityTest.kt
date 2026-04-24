package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.view.View
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import com.google.android.material.appbar.MaterialToolbar
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TeamsActivityTest {

    @Before
    fun setUp() {
        // Mock SharedPreferences to avoid EncryptedSharedPreferences error
        val mockPrefs = mock(SharedPreferences::class.java)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `test activity launches and initializes views`() {
        val controller = Robolectric.buildActivity(TeamsActivity::class.java)
        val activity = controller.create().start().resume().get()

        assertNotNull(activity)

        val root = activity.findViewById<View>(R.id.teamsRoot)
        assertNotNull(root)

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.teamsToolbar)
        assertNotNull(toolbar)
    }

    @Test
    fun `test toolbar navigation click calls onBackPressed`() {
        val controller = Robolectric.buildActivity(TeamsActivity::class.java)
        val activity = controller.create().start().resume().get()

        val toolbar = activity.findViewById<MaterialToolbar>(R.id.teamsToolbar)

        var backPressed = false
        activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                backPressed = true
            }
        })

        // Find the navigation icon click listener
        var navButton: View? = null
        for (i in 0 until toolbar.childCount) {
            val child = toolbar.getChildAt(i)
            if (child is ImageButton) {
                navButton = child
                break
            }
        }

        if (navButton != null) {
            navButton.performClick()
        } else {
            // Use reflection on Toolbar to get the nav button view
            val mNavButtonViewField = androidx.appcompat.widget.Toolbar::class.java.getDeclaredField("mNavButtonView")
            mNavButtonViewField.isAccessible = true
            val mNavButtonView = mNavButtonViewField.get(toolbar) as? ImageButton
            if (mNavButtonView != null) {
                mNavButtonView.performClick()
            } else {
                // Manually trigger the navigation click listener if button is not found
                try {
                    val mNavListenerField = androidx.appcompat.widget.Toolbar::class.java.getDeclaredField("mNavListener")
                    mNavListenerField.isAccessible = true
                    val mNavListener = mNavListenerField.get(toolbar) as? View.OnClickListener
                    mNavListener?.onClick(toolbar)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        assertTrue("Back pressed should be called", backPressed)
    }
}

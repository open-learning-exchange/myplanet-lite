package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

class TestActivity : BaseActivity() {
    val rootView: View by lazy { FrameLayout(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(rootView)
    }

    fun callApplyDeviceOrientationLock() {
        applyDeviceOrientationLock()
    }

    fun callSetupEdgeToEdgePadding(rootView: View) {
        setupEdgeToEdgePadding(rootView)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class BaseActivityTest {

    @Before
    fun setUp() {
        val mockSharedPreferences = mockk<SharedPreferences>(relaxed = true)
        every { mockSharedPreferences.getString(any(), any()) } returns "en"

        mockkObject(SecurePreferencesProvider)
        every { SecurePreferencesProvider.getServerPreferences(any()) } returns mockSharedPreferences
    }

    @After
    fun tearDown() {
        unmockkObject(SecurePreferencesProvider)
    }

    @Test
    fun testOnCreateAppliesLanguage() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertNotNull(activity)
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "sw600dp")
    fun testOrientationLockOnTablet() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get() as TestActivity
            activity.callApplyDeviceOrientationLock()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, activity.requestedOrientation)
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "sw400dp")
    fun testOrientationLockOnPhone() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get() as TestActivity
            activity.callApplyDeviceOrientationLock()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, activity.requestedOrientation)
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun testSetupEdgeToEdgePadding() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        try {
            val activity = controller.get() as TestActivity
            var listener: OnApplyWindowInsetsListener? = null
            mockkStatic(ViewCompat::class)
            every { ViewCompat.setOnApplyWindowInsetsListener(any(), any()) } answers {
                listener = it.invocation.args[1] as OnApplyWindowInsetsListener
            }
            activity.callSetupEdgeToEdgePadding(activity.rootView)
            assertNotNull("Listener should be registered", listener)
            val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), androidx.core.graphics.Insets.of(10, 20, 30, 40))
                .build()
            listener?.onApplyWindowInsets(activity.rootView, insets)
            assertEquals(10, activity.rootView.paddingLeft)
            assertEquals(20, activity.rootView.paddingTop)
            assertEquals(30, activity.rootView.paddingRight)
            assertEquals(40, activity.rootView.paddingBottom)
            unmockkStatic(ViewCompat::class)
        } finally {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            controller.pause().stop().destroy()
        }
    }
}

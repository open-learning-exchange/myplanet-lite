package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P])
class MyPlanetLiteTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPrefs = context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)
        SecurePreferencesProvider.injectedPreferences = testPrefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun testOnCreate() {
        val controller: ActivityController<MyPlanetLite> = Robolectric.buildActivity(MyPlanetLite::class.java)
        val app = controller.get()
        controller.create()
        assertNotNull(app)
    }
}

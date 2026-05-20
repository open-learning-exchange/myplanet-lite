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
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

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

    @Test
    fun attemptStoredSessionRestore_exception_handled() {
        val fakeService = object : org.ole.planet.myplanet.lite.auth.AuthService {
            override suspend fun login(usernameOrEmail: String, password: String) =
                org.ole.planet.myplanet.lite.auth.AuthResult.Success(org.ole.planet.myplanet.lite.auth.LoginResponse(ok = true))
            override suspend fun logout() {}
            override suspend fun getStoredToken(): String {
                throw RuntimeException("Simulated error")
            }
            override suspend fun authenticate(baseUrl: String, credentials: org.ole.planet.myplanet.lite.auth.UserCredentials) =
                org.ole.planet.myplanet.lite.auth.AuthResult.Success(org.ole.planet.myplanet.lite.auth.LoginResponse(ok = true))
        }
        org.ole.planet.myplanet.lite.auth.AuthDependencies.overrideAuthService(fakeService)

        val controller: ActivityController<MyPlanetLite> = Robolectric.buildActivity(MyPlanetLite::class.java)
        val activity = controller.get()
        controller.create().start().resume()

        // Call the private method via reflection to ensure the error path is triggered.
        val method = MyPlanetLite::class.java.getDeclaredMethod("attemptStoredSessionRestore", String::class.java)
        method.isAccessible = true
        method.invoke(activity, "https://planet.example.org")

        // Wait for coroutines to execute
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        // If it doesn't crash, the exception was caught successfully.
        org.junit.Assert.assertTrue(true)

        org.ole.planet.myplanet.lite.auth.AuthDependencies.overrideAuthService(null)
    }
}

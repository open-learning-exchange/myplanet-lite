package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.blongho.country_data.World
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

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
    fun serverConfigurationDialog_startsWithCleanFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SecurePreferencesProvider
            .getServerPreferences(context)
            .edit()
            .putString("server_url", "https://previous.planet.gt")
            .putString("server_display_name", "Previous Planet")
            .putString("country_code", "US")
            .apply()

        val controller: ActivityController<MyPlanetLite> = Robolectric.buildActivity(MyPlanetLite::class.java)
        val activity = controller.get()
        controller.create().start().resume()

        val method =
            MyPlanetLite::class.java.getDeclaredMethod(
                "showServerConfigurationDialog",
                MaterialAutoCompleteTextView::class.java,
                TextInputLayout::class.java,
            )
        method.isAccessible = true
        method.invoke(
            activity,
            activity.findViewById<MaterialAutoCompleteTextView>(R.id.serverUrlInput),
            activity.findViewById<TextInputLayout>(R.id.serverInputLayout),
        )

        val dialog = ShadowAlertDialog.getLatestDialog()
        val serverUrlInput = dialog.findViewById<MaterialAutoCompleteTextView>(R.id.serverUrlInput)
        val serverNameInput = dialog.findViewById<TextInputEditText>(R.id.serverNameInput)
        val countryInput = dialog.findViewById<MaterialAutoCompleteTextView>(R.id.countryInput)

        assertEquals("https://", serverUrlInput.text.toString())
        assertEquals("", serverNameInput.text.toString())
        val firstCountry =
            World
                .getAllCountries()
                .filterNot { setOf("CN", "HK", "TW", "IL", "PS").contains(it.alpha2.uppercase()) }
                .sortedBy { it.name }
                .first()
                .name
        assertEquals(firstCountry, countryInput.text.toString())
    }

    @Test
    fun serverOptions_placeCustomServersAboveClearBetweenDividers() {
        val controller: ActivityController<MyPlanetLite> = Robolectric.buildActivity(MyPlanetLite::class.java)
        val activity = controller.get()
        controller.create().start().resume()

        val addCustomServer =
            MyPlanetLite::class.java.getDeclaredMethod(
                "addCustomServer",
                String::class.java,
                String::class.java,
                String::class.java,
            )
        addCustomServer.isAccessible = true
        addCustomServer.invoke(activity, "Local Planet", "http://10.0.2.2", "GT")

        val loadServerConfiguration = MyPlanetLite::class.java.getDeclaredMethod("loadServerConfiguration")
        loadServerConfiguration.isAccessible = true
        val currentConfig = loadServerConfiguration.invoke(activity)
        val createServerOptions =
            MyPlanetLite::class.java.getDeclaredMethod(
                "createServerOptions",
                Class.forName("org.ole.planet.myplanet.lite.MyPlanetLite\$ServerConfiguration"),
            )
        createServerOptions.isAccessible = true
        val options = createServerOptions.invoke(activity, currentConfig) as List<*>
        val displayNameField = Class.forName("org.ole.planet.myplanet.lite.MyPlanetLite\$ServerOption")
            .getDeclaredField("displayName")
            .apply { isAccessible = true }
        val actionTypeField = Class.forName("org.ole.planet.myplanet.lite.MyPlanetLite\$ServerOption")
            .getDeclaredField("actionType")
            .apply { isAccessible = true }

        val customIndex = options.indexOfFirst { displayNameField.get(it) == "Local Planet" }
        val clearIndex = options.indexOfFirst { displayNameField.get(it) == activity.getString(R.string.server_option_clear) }

        assertTrue(customIndex > 0)
        assertTrue(clearIndex > customIndex)
        assertEquals("DIVIDER", actionTypeField.get(options[customIndex - 1]!!)?.toString())
        assertEquals("DIVIDER", actionTypeField.get(options[customIndex + 1]!!)?.toString())
        assertEquals(customIndex + 2, clearIndex)
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

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.auth.AuthService
import org.ole.planet.myplanet.lite.auth.UserCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowNetworkCapabilities

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SplashScreenImplTest {

    private lateinit var context: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockPrefs = context.getSharedPreferences("mock_prefs", Context.MODE_PRIVATE)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        UserProfileDatabase.getInstance(context).clearProfile()
        AuthDependencies.overrideAuthService(MockAuthService())

        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
        AuthDependencies.resetForTesting()
        UserProfileDatabase.getInstance(context).clearProfile()
        mockWebServer.shutdown()
    }

    @Suppress("DEPRECATION")
    private fun setNetworkState(isOnline: Boolean) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val shadowConnectivityManager = Shadows.shadowOf(connectivityManager)

        if (isOnline) {
            val networkInfo = org.robolectric.shadows.ShadowNetworkInfo.newInstance(
                android.net.NetworkInfo.DetailedState.CONNECTED,
                ConnectivityManager.TYPE_WIFI,
                0,
                true,
                android.net.NetworkInfo.State.CONNECTED
            )
            shadowConnectivityManager.setActiveNetworkInfo(networkInfo)

            val capabilities = ShadowNetworkCapabilities.newInstance()
            Shadows.shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                shadowConnectivityManager.setNetworkCapabilities(activeNetwork, capabilities)
            } else {
                shadowConnectivityManager.setDefaultNetworkActive(true)
            }
        } else {
            shadowConnectivityManager.setActiveNetworkInfo(null)
            shadowConnectivityManager.setDefaultNetworkActive(false)
        }
    }

    private fun createTestUserProfile(): UserProfile {
        return UserProfile(
            username = "testuser",
            firstName = null,
            middleName = null,
            lastName = null,
            email = null,
            language = null,
            phoneNumber = null,
            birthDate = null,
            gender = null,
            level = null,
            avatarImage = null,
            revision = null,
            derivedKey = null,
            rawDocument = null,
            isUserAdmin = false
        )
    }

    private fun waitForNextIntent(scenario: ActivityScenario<SplashScreenImpl>): Intent? {
        val maxWaitTimeMs = 5000
        val incrementMs = 100
        var totalWaitTime = 0
        var nextIntent: Intent? = null

        while (totalWaitTime < maxWaitTimeMs) {
            Thread.sleep(incrementMs.toLong())
            Shadows.shadowOf(Looper.getMainLooper()).runToEndOfTasks()

            scenario.onActivity { activity ->
                val shadowActivity = Shadows.shadowOf(activity)
                nextIntent = shadowActivity.nextStartedActivity
            }

            if (nextIntent != null) {
                break
            }
            totalWaitTime += incrementMs
        }
        return nextIntent
    }

    @Test
    fun `test splash screen routes to MyPlanetLite when no server URL is set`() {
        mockPrefs.edit().clear().commit()

        ActivityScenario.launch<SplashScreenImpl>(SplashScreenImpl::class.java).use { scenario ->
            val nextIntent = waitForNextIntent(scenario)
            assertNotNull("Expected nextIntent to not be null", nextIntent)
            assertEquals(MyPlanetLite::class.java.name, nextIntent?.component?.className)
            assertTrue(nextIntent?.getBooleanExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, false) ?: false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `test splash screen routes to MyPlanetLite when no token is stored`() {
        mockPrefs.edit().putString("server_url", "https://example.com").commit()
        val mockAuth = MockAuthService()
        mockAuth.storedToken = null
        AuthDependencies.overrideAuthService(mockAuth)

        ActivityScenario.launch<SplashScreenImpl>(SplashScreenImpl::class.java).use { scenario ->
            val nextIntent = waitForNextIntent(scenario)
            assertNotNull("Expected nextIntent to not be null", nextIntent)
            assertEquals(MyPlanetLite::class.java.name, nextIntent?.component?.className)
            assertTrue(nextIntent?.getBooleanExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, false) ?: false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `test splash screen routes to MyPlanetLite when user profile is not found`() {
        mockPrefs.edit().putString("server_url", "https://example.com").commit()
        val mockAuth = MockAuthService()
        mockAuth.storedToken = "valid_token"
        AuthDependencies.overrideAuthService(mockAuth)

        ActivityScenario.launch<SplashScreenImpl>(SplashScreenImpl::class.java).use { scenario ->
            val nextIntent = waitForNextIntent(scenario)
            assertNotNull("Expected nextIntent to not be null", nextIntent)
            assertEquals(MyPlanetLite::class.java.name, nextIntent?.component?.className)
            assertTrue(nextIntent?.getBooleanExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, false) ?: false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `test splash screen routes to DashboardActivity OFFLINE when network is offline`() {
        mockPrefs.edit().putString("server_url", "https://example.com").commit()
        val mockAuth = MockAuthService()
        mockAuth.storedToken = "valid_token"
        AuthDependencies.overrideAuthService(mockAuth)

        UserProfileDatabase.getInstance(context).saveProfile(createTestUserProfile())
        setNetworkState(false)

        ActivityScenario.launch<SplashScreenImpl>(SplashScreenImpl::class.java).use { scenario ->
            val nextIntent = waitForNextIntent(scenario)
            assertNotNull("Expected nextIntent to not be null for offline", nextIntent)
            assertEquals(DashboardActivity::class.java.name, nextIntent?.component?.className)
            assertTrue(nextIntent?.getBooleanExtra(DashboardActivity.EXTRA_OFFLINE_MODE, false) ?: false)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `test splash screen routes to DashboardActivity ONLINE when profile refresh succeeds`() {
        val baseUrl = mockWebServer.url("/").toString()
        mockPrefs.edit().putString("server_url", baseUrl).commit()
        val mockAuth = MockAuthService()
        mockAuth.storedToken = "valid_token"
        AuthDependencies.overrideAuthService(mockAuth)

        UserProfileDatabase.getInstance(context).saveProfile(createTestUserProfile())
        setNetworkState(true)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("{\"_id\":\"org.couchdb.user:testuser\",\"name\":\"testuser\",\"roles\":[]}"))

        ActivityScenario.launch<SplashScreenImpl>(SplashScreenImpl::class.java).use { scenario ->
            val nextIntent = waitForNextIntent(scenario)
            assertNotNull("Expected nextIntent to not be null for online", nextIntent)
            assertEquals(DashboardActivity::class.java.name, nextIntent?.component?.className)
            assertEquals(false, nextIntent?.getBooleanExtra(DashboardActivity.EXTRA_OFFLINE_MODE, false))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun `test splash screen routes to MyPlanetLite when profile refresh fails`() {
        val baseUrl = mockWebServer.url("/").toString()
        mockPrefs.edit().putString("server_url", baseUrl).commit()
        val mockAuth = MockAuthService()
        mockAuth.storedToken = "valid_token"
        AuthDependencies.overrideAuthService(mockAuth)

        UserProfileDatabase.getInstance(context).saveProfile(createTestUserProfile())
        setNetworkState(true)

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        mockWebServer.enqueue(MockResponse().setResponseCode(401))

        ActivityScenario.launch<SplashScreenImpl>(SplashScreenImpl::class.java).use { scenario ->
            val nextIntent = waitForNextIntent(scenario)
            assertNotNull("Expected nextIntent to not be null for failed refresh", nextIntent)
            assertEquals(MyPlanetLite::class.java.name, nextIntent?.component?.className)
            assertTrue(nextIntent?.getBooleanExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, false) ?: false)
            assertTrue(mockAuth.isLoggedOut)
            Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }
}

class MockAuthService : AuthService {
    var storedToken: String? = null
    var isLoggedOut = false

    override suspend fun authenticate(baseUrl: String, credentials: UserCredentials): AuthResult = AuthResult.Error(null, "Not implemented")
    override suspend fun login(usernameOrEmail: String, password: String): AuthResult = AuthResult.Error(null, "Not implemented")
    override suspend fun getStoredToken(): String? = storedToken
    override suspend fun logout() { isLoggedOut = true }
}

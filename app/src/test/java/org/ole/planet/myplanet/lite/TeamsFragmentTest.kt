package org.ole.planet.myplanet.lite

import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.testing.launchFragmentInContainer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.junit.Before
import org.junit.After
import android.content.SharedPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.robolectric.shadows.ShadowLooper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.StandardTestDispatcher
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthService
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlinx.coroutines.runBlocking
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamsRepository
import org.ole.planet.myplanet.lite.dashboard.MembershipDocument

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
@OptIn(ExperimentalCoroutinesApi::class)
class TeamsFragmentTest {

    private lateinit var mockPrefs: FakeSharedPreferences
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies.resetForTesting()
        Dispatchers.setMain(testDispatcher)
        mockPrefs = FakeSharedPreferences()
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        UserProfileDatabase.resetForTesting(ApplicationProvider.getApplicationContext())

        val mockAuthService = mock(AuthService::class.java)
        runBlocking {
            `when`(mockAuthService.getStoredToken()).thenReturn("fake-token")
        }
        AuthDependencies.overrideAuthService(mockAuthService)
    }

    @After
    fun tearDown() {
        org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies.resetForTesting()
        SecurePreferencesProvider.injectedPreferences = null
        ProfileCredentialsStore.setSessionCredentials(null)
        AuthDependencies.resetForTesting()
        Dispatchers.resetMain()
    }

    @Test
    fun `loadTeams shows error when network fails`() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))
        server.start()

        val context = ApplicationProvider.getApplicationContext<Context>()

        mockPrefs.edit()
            .putString(DashboardServerPreferences.KEY_SERVER_URL, server.url("/").toString())
            .putString("remembered_username", "testuser")
            .apply()

        val dynamicKey = ProfileCredentialsStore.getPasswordKey("testuser")
        mockPrefs.edit().putString(dynamicKey, "testpass").apply()

        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("testuser", "testpass"))

        UserProfileDatabase.getInstance(context).saveProfile(
            UserProfile(
                username = "testuser",
                firstName = "Test",
                middleName = "",
                lastName = "User",
                email = "",
                language = "",
                phoneNumber = "",
                birthDate = "",
                gender = "",
                level = "",
                avatarImage = null,
                revision = "",
                derivedKey = "",
                rawDocument = "",
                isUserAdmin = false
            )
        )

        launchFragmentInContainer<TeamsFragment>(themeResId = R.style.Theme_MyPlanetLite).use { scenario ->
            // Let init finish
            testDispatcher.scheduler.advanceUntilIdle()
            ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

            scenario.onFragment { fragment ->
                // Injecting a mock repository via reflection so we can forcefully fail fetchMemberships which is called first inside fetchAllTeamsData
                val mockRepository = mock(DashboardTeamsRepository::class.java)
                runBlocking {
                    val result = Result.failure<List<MembershipDocument>>(Exception("Network failure"))
                    `when`(mockRepository.fetchMemberships(
                        org.mockito.kotlin.any(),
                        org.mockito.kotlin.any(),
                        org.mockito.kotlin.any(),
                        org.mockito.kotlin.any()
                    )).thenReturn(result)
                }

                org.ole.planet.myplanet.lite.dashboard.DashboardTeamsDependencies.overrideRepository(mockRepository)

                // Set baseUrl properly in the preferences (it might be failing because we used KEY_SERVER_URL directly instead of what getServerBaseUrl uses)
                val baseField = TeamsFragment::class.java.getDeclaredField("baseUrl")
                baseField.isAccessible = true
                baseField.set(fragment, server.url("/").toString())

                // Let's call loadTeams using reflection
                val loadTeamsMethod = TeamsFragment::class.java.getDeclaredMethod("loadTeams")
                loadTeamsMethod.isAccessible = true
                loadTeamsMethod.invoke(fragment)

                testDispatcher.scheduler.advanceUntilIdle()
                ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

                val emptyView = fragment.view?.findViewById<TextView>(R.id.teamsEmptyView)
                assertNotNull(emptyView)

                // Assert that the error view is displayed with the correct error message
                assertTrue("Expected emptyView to be visible, but it wasn't. Text: ${emptyView?.text}", emptyView!!.isVisible)
                assertEquals(fragment.getString(R.string.dashboard_teams_error_loading), emptyView.text.toString())
            }
        }

        server.shutdown()
    }

    // --- Fakes ---
    class FakeSharedPreferences : SharedPreferences {
        private val data = mutableMapOf<String, Any?>()

        override fun getAll(): Map<String, *> = data
        override fun getString(key: String, defValue: String?): String? = data[key] as? String ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = data[key] as? Set<String> ?: defValues
        override fun getInt(key: String, defValue: Int): Int = data[key] as? Int ?: defValue
        override fun getLong(key: String, defValue: Long): Long = data[key] as? Long ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = data[key] as? Float ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = data[key] as? Boolean ?: defValue
        override fun contains(key: String): Boolean = data.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(data)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    class FakeEditor(private val data: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()
        private val toRemove = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor { temp[key] = values; return this }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor { temp[key] = value; return this }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { temp[key] = value; return this }
        override fun remove(key: String): SharedPreferences.Editor { toRemove.add(key); return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) data.clear()
            for (key in toRemove) data.remove(key)
            data.putAll(temp)
        }
    }
}

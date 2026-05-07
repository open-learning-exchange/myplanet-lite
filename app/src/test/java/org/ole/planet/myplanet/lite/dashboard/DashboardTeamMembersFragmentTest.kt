package org.ole.planet.myplanet.lite.dashboard

import android.content.SharedPreferences
import android.view.View
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as MaterialR
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.DashboardTeamMembersFragment
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.auth.AuthService
import org.ole.planet.myplanet.lite.auth.UserCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardTeamMembersFragmentTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var mockPrefs: FakeSharedPreferences

    @Before
    fun setUp() {
        DashboardTeamsRepository.resetCacheForTesting()
        DashboardTeamsRepository.overrideDispatcher = UnconfinedTestDispatcher()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        mockPrefs = FakeSharedPreferences()
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        mockPrefs.edit()
            .putString(DashboardServerPreferences.KEY_SERVER_URL, mockWebServer.url("/").toString())
            .putString("remembered_username", "testuser")
            .putString("remembered_password", "pass")
            .putString("selected_team_id", "team123")
            .putString("selected_team_name", "Test Team")
            .apply()

        AuthDependencies.overrideAuthService(object : AuthService {
            override suspend fun login(usernameOrEmail: String, password: String): AuthResult {
                return AuthResult.Success(
                    org.ole.planet.myplanet.lite.auth.LoginResponse(
                        ok = true,
                        name = usernameOrEmail,
                        roles = emptyList(),
                        sessionCookie = "test-token"
                    )
                )
            }
            override suspend fun authenticate(baseUrl: String, credentials: UserCredentials): AuthResult {
                return AuthResult.Success(
                    org.ole.planet.myplanet.lite.auth.LoginResponse(
                        ok = true,
                        name = credentials.name,
                        roles = emptyList(),
                        sessionCookie = "test-token"
                    )
                )
            }
            override suspend fun logout() {}

            override suspend fun getStoredToken(): String = "test-token"
        })
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        SecurePreferencesProvider.resetForTesting()
        AuthDependencies.resetForTesting()
        DashboardAvatarLoader.resetForTesting()
    }

    @Test
    fun `test fragment launches and shows members`() {
        val membershipsResponse = """
            {
                "docs": [
                    {
                        "_id": "membership1",
                        "teamId": "team123",
                        "userId": "org.couchdb.user:user1"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(membershipsResponse).setResponseCode(200))

        val usersResponse = """
            {
                "docs": [
                    {
                        "_id": "org.couchdb.user:user1",
                        "firstName": "First",
                        "lastName": "User"
                    }
                ]
            }
        """.trimIndent()
        mockWebServer.enqueue(MockResponse().setBody(usersResponse).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        // Enqueue extra responses to handle potential redundant requests during fragment lifecycle
        mockWebServer.enqueue(MockResponse().setBody(membershipsResponse).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody(usersResponse).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        mockWebServer.enqueue(MockResponse().setBody(membershipsResponse).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody(usersResponse).setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        launchFragmentInContainer<DashboardTeamMembersFragment>(themeResId = MaterialR.style.Theme_MaterialComponents_Light).use { scenario ->
            repeat(5) { ShadowLooper.runUiThreadTasksIncludingDelayedTasks() }
            scenario.onFragment { fragment ->
                assertNotNull(fragment)
                val recyclerView = fragment.view?.findViewById<RecyclerView>(R.id.dashboardTeamMembersList)
                assertNotNull(recyclerView)
                assertEquals(View.VISIBLE, recyclerView?.visibility)
                assertEquals(1, recyclerView?.adapter?.itemCount)
            }
        }
    }
}

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

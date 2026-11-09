package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Looper
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.auth.*
import org.ole.planet.myplanet.lite.dashboard.CalendarNotificationOutbox
import org.ole.planet.myplanet.lite.dashboard.TeamEventDraft
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class TeamEventNotificationIntegrationTest {
    @After fun cleanup() {
        AuthDependencies.resetForTesting()
        ProfileCredentialsStore.setSessionCredentials(null)
        SecurePreferencesProvider.resetForTesting()
    }

    @Test fun `notification failure never changes successful event save into a retry`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val application = RuntimeEnvironment.getApplication()
            shadowOf(application).grantPermissions(android.Manifest.permission.READ_CALENDAR)
            val google = LinkedGoogleProviderFixture().apply { calendarAvailable = false }
            org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(android.provider.CalendarContract.AUTHORITY, google)
            val base = server.url("/").toString()
            val prefs = application.getSharedPreferences("edit_notification_integration", Context.MODE_PRIVATE)
            prefs.edit().clear().putString("server_url", base).commit()
            SecurePreferencesProvider.injectedPreferences = prefs
            val credentials = StoredCredentials("ana", "password")
            ProfileCredentialsStore.setSessionCredentials(credentials)
            AuthDependencies.overrideAuthService(object : AuthService {
                override suspend fun getStoredToken(): String? = null
                override suspend fun logout() = Unit
                override suspend fun login(usernameOrEmail: String, password: String): AuthResult = error("unused")
                override suspend fun authenticate(baseUrl: String, credentials: UserCredentials): AuthResult = error("unused")
            })
            val eventPosts = AtomicInteger()
            val shelfPosts = AtomicInteger()
            val day = LocalDate.now().plusDays(1)
            val json = JSONObject().put("_id", "event-1").put("_rev", "1-old").put("title", "Original")
                .put("description", "Agenda").put("startDate", day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
                .put("createdBy", "ana").put("link", JSONObject().put("teams", "team-1"))
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/db/meetups/event-1" -> MockResponse().setBody(json.toString())
                    "/db/_users/org.couchdb.user:ana" -> MockResponse().setBody("""{"name":"ana"}""")
                    "/db/meetups" -> {
                        eventPosts.incrementAndGet()
                        MockResponse().setBody("""{"ok":true,"id":"event-1","rev":"2-new"}""")
                    }
                    "/db/shelf/_find" -> if (shelfPosts.incrementAndGet() == 1) MockResponse().setResponseCode(500)
                        else MockResponse().setBody("""{"docs":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            fun await(condition: () -> Boolean) {
                repeat(300) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
                    if (condition()) return
                    Thread.sleep(10)
                }
                fail("Event save did not complete")
            }
            val model = TeamEventCreationViewModel(application)
            model.load("team-1", "event-1")
            await { model.state.value.editable != null }
            model.save("team-1", TeamEventDraft("Updated", "Agenda", day, null, "", ""), "event-1")
            await { model.state.value.saved != null && shelfPosts.get() == 1 }
            assertNull(model.state.value.errorRes)
            assertEquals("2-new", model.state.value.saved!!.revision)
            val queue = CalendarNotificationOutbox(application)
            assertEquals(1, queue.pending(base, "ana"))
            assertEquals(0, queue.retry(base, credentials, null))
            assertEquals(1, eventPosts.get())
            assertEquals(2, shelfPosts.get())
            // Repeated save input after success cannot create a second event update.
            model.save("team-1", TeamEventDraft("Updated", "Agenda", day, null, "", ""), "event-1")
            assertEquals(1, eventPosts.get())
        }
    }
}

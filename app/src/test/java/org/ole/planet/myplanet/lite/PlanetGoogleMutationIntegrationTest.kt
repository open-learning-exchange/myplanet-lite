package org.ole.planet.myplanet.lite

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Looper
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.auth.*
import org.ole.planet.myplanet.lite.dashboard.*
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlanetGoogleMutationIntegrationTest {
    private lateinit var app: Application
    private lateinit var provider: LinkedGoogleProviderFixture
    private lateinit var server: MockWebServer
    private lateinit var base: String
    private val posts = AtomicInteger()
    private val deletes = AtomicInteger()
    private val date = LocalDate.now().plusDays(1)
    private fun event() = TeamMeetup("event-1", "1-old", "Meeting", "Agenda",
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), null, "09:00", "10:00", "none", 10,
        emptySet(), "Room", "", "ana")
    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        server = MockWebServer().apply { start() }; base = server.url("/").toString()
        val prefs = app.getSharedPreferences("google_mutation_integration", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("server_url", base).commit(); SecurePreferencesProvider.injectedPreferences = prefs
        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("ana", "password"))
        shadowOf(app).grantPermissions(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        provider = LinkedGoogleProviderFixture()
        ShadowContentResolver.registerProviderInternal(CalendarContract.AUTHORITY, provider)
        provider.add(10, event().exportOccurrence(date, "Agenda"), planetGoogleMarker(base, "team-1", "event-1", date.toString()))
        AuthDependencies.overrideAuthService(object : AuthService {
            override suspend fun getStoredToken(): String? = null
            override suspend fun logout() = Unit
            override suspend fun login(usernameOrEmail: String, password: String): AuthResult = error("unused")
            override suspend fun authenticate(baseUrl: String, credentials: UserCredentials): AuthResult = error("unused")
        })
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/db/meetups/event-1" -> MockResponse().setBody(event().syncJson().put("link", JSONObject().put("teams", "team-1")).toString())
                "/db/_users/org.couchdb.user:ana" -> MockResponse().setBody("""{"name":"ana"}""")
                "/db/meetups" -> { posts.incrementAndGet(); MockResponse().setBody("""{"ok":true,"id":"event-1","rev":"2-new"}""") }
                "/db/meetups/_bulk_docs" -> { deletes.incrementAndGet(); MockResponse().setBody("""[{"ok":true,"id":"event-1","rev":"2-deleted"}]""") }
                "/db/shelf/_find" -> MockResponse().setBody("""{"docs":[]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
    }
    @After fun cleanup() {
        server.shutdown(); AuthDependencies.resetForTesting(); ProfileCredentialsStore.setSessionCredentials(null)
        SecurePreferencesProvider.resetForTesting()
    }
    private fun await(condition: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            if (condition()) return
            Thread.sleep(10)
        }
        fail("Mutation did not finish")
    }
    @Test fun `Google edit failure leaves Planet saved and retry never reposts event`() = runTest {
        provider.failId = 10
        val model = TeamEventCreationViewModel(app)
        model.load("team-1", "event-1"); await { model.state.value.editable != null }
        val draft = TeamEventDraft("Updated", "Agenda", date, null, "11:00", "12:00")
        model.save("team-1", draft, "event-1"); await { model.state.value.saved != null }
        assertTrue(model.state.value.syncWarning); assertEquals(1, posts.get())
        assertEquals(1, GoogleEventLinkStore(app, base, "ana").jobs().size)
        provider.failId = null
        assertEquals(0, PlanetGoogleSync(app).retry(base, "ana"))
        assertEquals("Updated", provider.active().values.single().getAsString(CalendarContract.Events.TITLE))
        model.save("team-1", draft, "event-1"); assertEquals(1, posts.get())
    }
    @Test fun `Google deletion failure leaves Planet deleted and retry never deletes Planet twice`() = runTest {
        provider.failId = 10
        val model = TeamEventDeletionViewModel(app)
        model.delete("team-1", "event-1", "1-old")
        await { model.state.value.ready }
        assertTrue(model.state.value.linkedGoogle)
        assertEquals(0, deletes.get())
        assertEquals(0, provider.deleted)
        model.delete("team-1", "event-1", "1-old", confirm = true)
        await { model.state.value.deleted }
        assertTrue(model.state.value.syncWarning); assertEquals(1, deletes.get())
        provider.failId = null
        assertEquals(0, PlanetGoogleSync(app).retry(base, "ana"))
        assertTrue(provider.active().isEmpty())
        model.delete("team-1", "event-1", "1-old"); assertEquals(1, deletes.get())
        assertEquals(0, posts.get())
    }
    @Test fun `preparing deletion leaves both copies untouched until confirmation`() {
        val model = TeamEventDeletionViewModel(app)
        model.delete("team-1", "event-1", "1-old")
        await { model.state.value.ready }
        assertTrue(model.state.value.linkedGoogle)
        assertEquals(0, deletes.get())
        assertEquals(0, provider.deleted)
        assertEquals(1, provider.active().size)
    }
    @Test fun `unchecked Google option deletes only Planet without Google write permission`() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        val model = TeamEventDeletionViewModel(app)
        model.delete("team-1", "event-1", "1-old")
        await { model.state.value.ready }
        assertTrue(model.state.value.deleteGoogle)
        assertTrue(model.state.value.linkedGoogle)
        model.selectGoogle(false)
        model.delete("team-1", "event-1", "1-old", confirm = true)
        await { model.state.value.deleted }
        assertEquals(1, deletes.get())
        assertEquals(0, provider.deleted)
        assertEquals(1, provider.active().size)
        assertTrue(GoogleEventLinkStore(app, base, "ana").jobs().isEmpty())
        assertFalse(model.state.value.syncWarning)
    }
    @Test fun `unlinked event uses Planet only confirmation`() {
        provider.events.clear()
        val model = TeamEventDeletionViewModel(app)
        model.delete("team-1", "event-1", "1-old")
        await { model.state.value.ready }
        assertFalse(model.state.value.linkedGoogle)
        assertEquals(0, deletes.get())
        model.delete("team-1", "event-1", "1-old", confirm = true)
        await { model.state.value.deleted }
        assertEquals(1, deletes.get())
        assertEquals(0, provider.deleted)
    }
    @Test fun `new Planet overlap blocks creation before posting`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/db/teams/team-1" -> MockResponse().setBody("""{"_id":"team-1","public":true}""")
                "/db/meetups/_find" -> MockResponse().setBody(JSONObject().put("docs", org.json.JSONArray().put(
                    event().syncJson().put("link", JSONObject().put("teams", "team-1")))).toString())
                else -> { posts.incrementAndGet(); MockResponse().setResponseCode(500) }
            }
        }
        val model = TeamEventCreationViewModel(app)
        model.save("team-1", TeamEventDraft("New", "Agenda", date, null, "09:30", "10:30"))
        await { !model.state.value.saving }
        assertEquals(R.string.calendar_overlap_planet, model.state.value.overlapRes)
        model.resolveOverlap(false)
        assertNull(model.state.value.overlapRes)
        assertNull(model.state.value.saved)
        assertEquals(0, posts.get())
    }
    @Test fun `accepting overlap creates exactly one Planet event`() {
        SecurePreferencesProvider.getServerPreferences(app).edit().putString("server_code", "planet-a").commit()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/db/teams/team-1" -> MockResponse().setBody("""{"_id":"team-1","public":true,"teamType":"sync","teamPlanetCode":"planet-a"}""")
                "/db/meetups/_find" -> MockResponse().setBody(JSONObject().put("docs", org.json.JSONArray().put(
                    event().syncJson().put("link", JSONObject().put("teams", "team-1")))).toString())
                "/db/meetups" -> { posts.incrementAndGet(); MockResponse().setBody("""{"ok":true,"id":"new-event","rev":"1-new"}""") }
                else -> MockResponse().setResponseCode(404)
            }
        }
        val model = TeamEventCreationViewModel(app)
        model.save("team-1", TeamEventDraft("New", "Agenda", date, null, "09:30", "10:30"))
        await { model.state.value.overlapRes != null }
        assertEquals(0, posts.get())
        model.resolveOverlap(true)
        await { model.state.value.saved != null }
        assertEquals(1, posts.get())
    }
    @Test fun `connected Google overlap blocks new event without Planet mutation`() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/db/teams/team-1" -> MockResponse().setBody("""{"_id":"team-1","public":true}""")
                "/db/meetups/_find" -> MockResponse().setBody("""{"docs":[]}""")
                else -> { posts.incrementAndGet(); MockResponse().setResponseCode(500) }
            }
        }
        val selection = GoogleCalendarSelectionStore(app)
        selection.save(selection.scope("team-1")!!, setOf("1:${provider.account}"))
        val model = TeamEventCreationViewModel(app)
        model.save("team-1", TeamEventDraft("New", "Agenda", date, null, "09:30", "10:30"))
        await { !model.state.value.saving }
        assertEquals(R.string.calendar_overlap_google, model.state.value.overlapRes)
        assertNull(model.state.value.saved)
        assertEquals(0, posts.get())
    }
    @Test fun `denied Google write permission prevents Planet mutation`() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_CALENDAR)
        val model = TeamEventCreationViewModel(app)
        model.load("team-1", "event-1"); await { model.state.value.editable != null }
        model.save("team-1", TeamEventDraft("Updated", "Agenda", date, null, "11:00", "12:00"), "event-1")
        await { model.state.value.permissions != null }
        assertEquals(Manifest.permission.WRITE_CALENDAR, model.state.value.permissions!!.single())
        assertEquals(0, posts.get())
        model.permissionResult(false)
        assertNull(model.state.value.saved); assertEquals(R.string.calendar_google_sync_permission, model.state.value.errorRes)
        assertEquals(0, provider.deleted)
    }
}

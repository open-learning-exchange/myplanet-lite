package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CalendarNotificationOutboxTest {
    private lateinit var context: Context
    private val credentials = StoredCredentials("ana", "password")
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val prefs = context.getSharedPreferences("notification_test", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        SecurePreferencesProvider.injectedPreferences = prefs
    }
    @After fun teardown() = SecurePreferencesProvider.resetForTesting()

    @Test fun `partial bulk success persists only failed notifications across restart`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val base = server.url("/").toString()
            val batches = mutableListOf<JSONArray>()
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                    "/db/shelf/_find" -> {
                        val query = JSONObject(request.body.clone().readUtf8())
                        assertEquals("event-1", query.getJSONObject("selector").getJSONObject("meetupIds").getJSONArray("\$in").getString(0))
                        assertEquals("_id", query.getJSONArray("fields").getString(0))
                        MockResponse().setBody("""{"docs":[{"_id":"org.couchdb.user:maria"},{"_id":"juan"}]}""")
                    }
                    "/db/notifications/_bulk_docs" -> {
                        val docs = JSONObject(request.body.clone().readUtf8()).getJSONArray("docs")
                        batches += docs
                        val results = JSONArray()
                        for (index in 0 until docs.length()) {
                            val doc = docs.getJSONObject(index)
                            val row = JSONObject().put("id", doc.getString("_id"))
                            if (batches.size == 1 && index == 1) row.put("error", "forbidden")
                            else row.put("ok", true).put("rev", "1-saved")
                            results.put(row)
                        }
                        MockResponse().setBody(results.toString())
                    }
                    else -> error("Notification retry must never update events: ${request.path}")
                }
            }
            val queue = CalendarNotificationOutbox(context)
            queue.enqueue(base, "ana", "event-1", "Updated <meeting>")
            assertEquals(1, queue.retry(base, credentials, "cookie"))
            val first = batches[0]
            assertEquals(2, first.length())
            val notification = first.getJSONObject(0)
            assertEquals("org.couchdb.user:maria", notification.getString("user"))
            assertEquals("<b>\"Updated &lt;meeting&gt;\"</b> has been updated.", notification.getString("message"))
            assertEquals("/meetups/view/event-1", notification.getString("link"))
            assertEquals("meetup", notification.getString("type"))
            assertEquals("unread", notification.getString("status"))
            assertEquals(0, CalendarNotificationOutbox(context).retry(base, credentials, "cookie"))
            assertEquals(1, batches[1].length())
            assertEquals(first.getJSONObject(1).getString("_id"), batches[1].getJSONObject(0).getString("_id"))
            assertEquals(3, server.requestCount)
            assertEquals(0, queue.pending(base, "another-user"))
            assertEquals(0, queue.pending("https://another.example", "ana"))
        }
    }

    @Test fun `shelf failure retries only notifications and empty queue makes no requests`() = runTest {
        MockWebServer().use { server ->
            server.start(); val base = server.url("/").toString()
            val queue = CalendarNotificationOutbox(context)
            assertEquals(0, queue.retry(base, credentials, null)); assertEquals(0, server.requestCount)
            queue.enqueue(base, "ana", "event-1", "Updated")
            server.enqueue(MockResponse().setResponseCode(500))
            assertEquals(1, queue.retry(base, credentials, null))
            server.enqueue(MockResponse().setBody("""{"docs":[]}"""))
            assertEquals(0, queue.retry(base, credentials, null))
            repeat(2) { assertEquals("/db/shelf/_find", server.takeRequest().path) }
        }
    }

    @Test fun `stable ids avoid duplicates when an accepted response is lost`() = runTest {
        MockWebServer().use { server ->
            server.start(); val base = server.url("/").toString()
            var saved: JSONObject? = null
            var attempts = 0
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == "/db/shelf/_find") return MockResponse().setBody("""{"docs":[{"_id":"maria"}]}""")
                    if (request.path == "/db/notifications/_bulk_docs") {
                        val document = JSONObject(request.body.clone().readUtf8()).getJSONArray("docs").getJSONObject(0)
                        attempts++
                        if (saved == null) { saved = document; return MockResponse().setResponseCode(500) }
                        assertEquals(saved.getString("_id"), document.getString("_id"))
                        return MockResponse().setBody(JSONArray().put(JSONObject().put("id", document.getString("_id")).put("error", "conflict")).toString())
                    }
                    assertEquals("GET", request.method)
                    return MockResponse().setBody(saved!!.put("status", "read").toString())
                }
            }
            val queue = CalendarNotificationOutbox(context)
            queue.enqueue(base, "ana", "event-1", "Meeting")
            assertEquals(1, queue.retry(base, credentials, null))
            assertEquals(0, queue.retry(base, credentials, null))
            assertEquals(2, attempts)
        }
    }
}

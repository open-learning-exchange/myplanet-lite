package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class VoicePayloadConsistencyTest {
    @Test
    fun `create and update retain the same destination and metadata for all dashboards`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val composer = VoicesComposerRepository(OkHttpClient(), moshi)
            val editor = DashboardNewsActionsRepository(OkHttpClient(), moshi, Dispatchers.IO)
            val createdBodies = mutableListOf<JSONObject>()
            for (destination in listOf("community", "team", "enterprise")) {
                val teamId = if (destination == "community") null else "target-id"
                val enterprise = destination == "enterprise"
                server.enqueue(MockResponse().setBody("""{"ok":true,"id":"voice-id","rev":"1-rev"}"""))
                assertTrue(composer.createVoice(VoicesComposerRepository.CreateVoiceParams(
                    baseUrl = server.url("/").toString(),
                    credentials = StoredCredentials("ana", "password"),
                    sessionCookie = "AuthSession=test",
                    message = "Original message",
                    createdOn = "planet",
                    parentCode = "parent",
                    replyTo = null,
                    images = listOf(VoicesComposerRepository.ImagePayload("image-id", "image.jpg", "![](resources/image-id/image.jpg)")),
                    labels = listOf("label"),
                    userPayload = null,
                    teamId = teamId,
                    teamName = if (teamId != null) "Target" else null,
                    enterpriseMode = enterprise,
                    enterpriseType = if (enterprise) "sync" else null,
                    enterprisePlanetCode = if (enterprise) "enterprise-planet" else null,
                )).isSuccess)
                val createRequest = server.takeRequest()
                assertEquals("POST", createRequest.method)
                assertEquals("/db/news", createRequest.path)
                assertEquals("AuthSession=test", createRequest.getHeader("Cookie"))
                val created = JSONObject(createRequest.body.readUtf8())
                createdBodies.add(JSONObject(created.toString()))
                val visibility = created.getJSONArray("viewIn").getJSONObject(0)
                assertEquals(if (teamId == null) "community" else "teams", visibility.getString("section"))
                assertEquals(teamId ?: "planet@parent", visibility.getString("_id"))
                if (teamId != null) {
                    assertEquals(destination, visibility.getString("mode"))
                    assertFalse(visibility.getBoolean("public"))
                }
                assertEquals(if (enterprise) "sync" else "news", created.getString("messageType"))
                assertEquals(if (enterprise) "enterprise-planet" else "planet", created.getString("messagePlanetCode"))
                created.put("_id", "voice-id").put("_rev", "2-latest")
                created.put("reactions", JSONObject().put("like", 2))
                server.enqueue(MockResponse().setBody(created.toString()))
                server.enqueue(MockResponse().setBody("""{"ok":true,"id":"voice-id","rev":"3-rev"}"""))
                val document = moshi.adapter(DashboardNewsRepository.NewsDocument::class.java).fromJson(created.toString())!!
                assertTrue(editor.updateNews(
                    server.url("/").toString(), "AuthSession=test", document,
                    "Edited message", listOf(DashboardNewsRepository.NewsImage("image-id", "image.jpg", "![](resources/image-id/image.jpg)")),
                    teamId, if (teamId != null) "Target" else null, enterprise,
                ).isSuccess)
                assertEquals("GET", server.takeRequest().method)
                val updateRequest = server.takeRequest()
                assertEquals("POST", updateRequest.method)
                assertEquals("/db/news/", updateRequest.path)
                val updated = JSONObject(updateRequest.body.readUtf8())
                assertEquals("Edited message", updated.getString("message"))
                assertTrue(updated.getLong("updatedDate") >= created.getLong("updatedDate"))
                for (key in created.keys()) {
                    if (key != "message" && key != "updatedDate") {
                        assertEquals("$destination field $key", created.get(key).toString(), updated.get(key).toString())
                    }
                }
            }
            assertEquals(createdBodies[0].keys().asSequence().toSet(), createdBodies[1].keys().asSequence().toSet())
            assertEquals(createdBodies[1].keys().asSequence().toSet(), createdBodies[2].keys().asSequence().toSet())
        }
    }

    @Test
    fun `enterprise update repairs missing visibility mode without turning it into a team`() = runTest {
        MockWebServer().use { server ->
            server.start()
            val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            val editor = DashboardNewsActionsRepository(OkHttpClient(), moshi, Dispatchers.IO)
            for (visibility in listOf("[]", """[{"section":"teams","_id":"enterprise-id"}]""")) {
                val json = """{"_id":"voice-id","_rev":"2-rev","createdOn":"planet","parentCode":"parent","viewIn":$visibility,"messageType":"sync"}"""
                server.enqueue(MockResponse().setBody(json))
                server.enqueue(MockResponse().setBody("""{"ok":true,"id":"voice-id","rev":"3-rev"}"""))
                val document = moshi.adapter(DashboardNewsRepository.NewsDocument::class.java).fromJson(json)!!
                assertTrue(editor.updateNews(server.url("/").toString(), null, document, "Edited", emptyList(), "enterprise-id", "Enterprise", true).isSuccess)
                server.takeRequest()
                val updated = JSONObject(server.takeRequest().body.readUtf8())
                val entry = updated.getJSONArray("viewIn").getJSONObject(0)
                assertEquals("enterprise", entry.getString("mode"))
                assertEquals("enterprise-id", entry.getString("_id"))
                assertFalse(entry.getBoolean("public"))
                assertEquals("sync", updated.getString("messageType"))
            }
        }
    }
}

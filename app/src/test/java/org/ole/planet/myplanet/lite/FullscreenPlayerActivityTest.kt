package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Build
import android.widget.ImageButton
import androidx.lifecycle.Lifecycle
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.R])
class FullscreenPlayerActivityTest {

    @Test
    fun testCreateIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mediaUrls = arrayListOf("http://example.com/video.mp4")
        val startIndex = 1
        val startPositionMs = 1000L
        val authHeader = "Basic auth"

        val intent = FullscreenPlayerActivity.createIntent(context, mediaUrls, startIndex, startPositionMs, authHeader)

        val expectedMediaUrls = intent.getStringArrayListExtra("extra_media_urls")
        val expectedStartIndex = intent.getIntExtra("extra_start_index", -1)
        val expectedStartPosition = intent.getLongExtra("extra_start_position", -1L)
        val expectedAuthHeader = intent.getStringExtra("extra_auth_header")

        assertEquals(mediaUrls, expectedMediaUrls)
        assertEquals(startIndex, expectedStartIndex)
        assertEquals(startPositionMs, expectedStartPosition)
        assertEquals(authHeader, expectedAuthHeader)
    }

    @Test
    fun testActivityLaunchAndExit() {
        val mediaUrls = arrayListOf("http://example.com/video.mp4")
        val intent = FullscreenPlayerActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            mediaUrls,
            0,
            0L,
            "Basic abcdef"
        )

        ActivityScenario.launch<FullscreenPlayerActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)

                val playerView = activity.findViewById<PlayerView>(R.id.fullscreenPlayerView)
                assertNotNull(playerView)
                assertNotNull(playerView.player)

                val exitButton = activity.findViewById<ImageButton>(R.id.fullscreenExitButton)
                assertNotNull(exitButton)
                exitButton.performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun testActivityEmptyMediaUrls() {
        val intent = FullscreenPlayerActivity.createIntent(
            ApplicationProvider.getApplicationContext(),
            arrayListOf(),
            0,
            0L,
            "Basic abcdef"
        )

        ActivityScenario.launch<FullscreenPlayerActivity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}

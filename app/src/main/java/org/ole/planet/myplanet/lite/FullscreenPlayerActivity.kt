/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-01-05
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecRenderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import okhttp3.Credentials
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class FullscreenPlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_fullscreen_player)
        hideSystemBars()

        val mediaUrls = intent.getStringArrayListExtra(EXTRA_MEDIA_URLS).orEmpty()
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0)
        val startPosition = intent.getLongExtra(EXTRA_START_POSITION, 0L)
        val authHeader = intent.getStringExtra(EXTRA_AUTH_HEADER) ?: resolveAuthHeader()

        if (mediaUrls.isEmpty()) {
            finish()
            return
        }

        val playerView: PlayerView = findViewById(R.id.fullscreenPlayerView)
        findViewById<ImageButton>(R.id.fullscreenExitButton).setOnClickListener {
            showSystemBars()
            finish()
        }
        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(buildDataSourceFactory(authHeader)))
            .build()
            .also { exo ->
                playerView.player = exo
                exo.addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        val messageRes = if (isDecoderCapabilityError(error)) {
                            R.string.fullscreen_player_unsupported_video
                        } else {
                            R.string.course_wizard_play_error
                        }
                        Toast.makeText(this@FullscreenPlayerActivity, getString(messageRes), Toast.LENGTH_SHORT).show()
                    }
                })
                val items = mediaUrls.map { MediaItem.fromUri(it) }
                exo.setMediaItems(items, startIndex, startPosition)
                exo.prepare()
                exo.playWhenReady = true
            }
    }

    private fun hideSystemBars() {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun showSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
    }

    private fun buildHttpDataSourceFactory(authorizationHeader: String?): DefaultHttpDataSource.Factory {
        val factory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        authorizationHeader?.let { factory.setDefaultRequestProperties(mapOf("Authorization" to it)) }
        return factory
    }

    private fun buildDataSourceFactory(authorizationHeader: String?): DefaultDataSource.Factory {
        return DefaultDataSource.Factory(this, buildHttpDataSourceFactory(authorizationHeader))
    }

    private fun resolveAuthHeader(): String? {
        val credentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
        val baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
        return if (credentials != null && !baseUrl.isNullOrBlank()) {
            Credentials.basic(credentials.username, credentials.password)
        } else {
            null
        }
    }

    private fun isDecoderCapabilityError(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES) {
            return true
        }
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is MediaCodecRenderer.DecoderInitializationException) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    override fun finish() {
        deliverPlaybackResult()
        super.finish()
    }

    override fun onStop() {
        deliverPlaybackResult()
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }

    private fun deliverPlaybackResult() {
        player?.let { exo ->
            val result = Intent().apply {
                putExtra(EXTRA_RESULT_INDEX, exo.currentMediaItemIndex)
                putExtra(EXTRA_RESULT_POSITION, exo.currentPosition)
                putExtra(EXTRA_RESULT_PLAY_WHEN_READY, exo.playWhenReady)
            }
            setResult(RESULT_OK, result)
        }
    }

    companion object {
        private const val EXTRA_MEDIA_URLS = "extra_media_urls"
        private const val EXTRA_START_INDEX = "extra_start_index"
        private const val EXTRA_START_POSITION = "extra_start_position"
        private const val EXTRA_AUTH_HEADER = "extra_auth_header"
        const val EXTRA_RESULT_INDEX = "extra_result_index"
        const val EXTRA_RESULT_POSITION = "extra_result_position"
        const val EXTRA_RESULT_PLAY_WHEN_READY = "extra_result_play_when_ready"

        fun createIntent(
            context: Context,
            mediaUrls: ArrayList<String>,
            startIndex: Int,
            startPositionMs: Long,
            authorizationHeader: String?
        ): Intent {
            return Intent(context, FullscreenPlayerActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_MEDIA_URLS, mediaUrls)
                putExtra(EXTRA_START_INDEX, startIndex)
                putExtra(EXTRA_START_POSITION, startPositionMs)
                putExtra(EXTRA_AUTH_HEADER, authorizationHeader)
            }
        }
    }
}

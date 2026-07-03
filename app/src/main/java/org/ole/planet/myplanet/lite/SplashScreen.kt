/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.squareup.moshi.Moshi
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.ServerConnectivityRepository
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.profile.UserProfileSync
import org.ole.planet.myplanet.lite.util.IntentUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class SplashScreen : BaseActivity() {

    private val connectivityClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    private val connectivityMoshi: Moshi by lazy { Moshi.Builder().build() }
    private val serverConnectivityRepository: ServerConnectivityRepository by lazy {
        ServerConnectivityRepository(connectivityClient, connectivityMoshi)
    }

    private val userProfileDatabase: UserProfileDatabase by lazy {
        UserProfileDatabase.getInstance(applicationContext)
    }
    private val userProfileSync: UserProfileSync by lazy {
        UserProfileSync(connectivityClient, userProfileDatabase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (shouldReturnToExistingTask(intent)) {
            finish()
            return
        }
        applyDeviceOrientationLock()
        cacheDeviceIdentifiers()
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)
        setupEdgeToEdgePadding(findViewById(R.id.main))
        startLogoAnimation()
        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            routeToNextActivity()
        }
    }

    private fun shouldReturnToExistingTask(launchIntent: Intent?): Boolean {
        if (isTaskRoot) {
            return false
        }
        val isLauncherIntent = launchIntent?.action == Intent.ACTION_MAIN &&
            launchIntent.hasCategory(Intent.CATEGORY_LAUNCHER)
        return isLauncherIntent
    }

    private fun startLogoAnimation() {
        val logo = findViewById<ImageView>(R.id.logoImageView)
        val startOffset = -resources.displayMetrics.heightPixels * 0.5f
        logo.translationY = startOffset
        logo.alpha = 0f
        logo.post {
            logo.animate()
                .translationY(0f)
                .rotationBy(360f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
        val appName = findViewById<View>(R.id.appNameContainer)
        appName.scaleX = 1f
        appName.scaleY = 1f
        appName.post {
            appName.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(200)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    appName.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .start()
                }
                .start()
        }
        val appVersion = findViewById<TextView>(R.id.appVersionTextView)
        appVersion.text = getString(R.string.app_version, BuildConfig.VERSION_NAME)
        appVersion.translationY = resources.displayMetrics.density * 40
        appVersion.alpha = 0f
        appVersion.post {
            appVersion.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(400)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private suspend fun routeToNextActivity() {
        val launchMode = attemptDirectDashboardLaunch()
        val nextIntent = when (launchMode) {
            DashboardLaunchMode.ONLINE -> Intent(this@SplashScreen, DashboardActivity::class.java)
            DashboardLaunchMode.OFFLINE -> Intent(this@SplashScreen, DashboardActivity::class.java).apply {
                putExtra(DashboardActivity.EXTRA_OFFLINE_MODE, true)
            }
            DashboardLaunchMode.NONE -> Intent(this@SplashScreen, MyPlanetLite::class.java).apply {
                putExtra(MyPlanetLite.EXTRA_ALLOW_AUTO_LOGIN, true)
            }
        }

        AppNavigator.forwardDeepLinkIntent(intent, nextIntent, launchMode == DashboardLaunchMode.NONE)

        startActivity(nextIntent)
        finish()
    }

    private suspend fun attemptDirectDashboardLaunch(): DashboardLaunchMode {
        val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
        val baseUrl = preferences.getString(KEY_SERVER_URL, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: return DashboardLaunchMode.NONE
        val authService = AuthDependencies.provideAuthService(this, baseUrl)
        val storedToken = authService.getStoredToken()
        if (storedToken.isNullOrBlank()) {
            return DashboardLaunchMode.NONE
        }
        val cachedUsername = withContext(Dispatchers.IO) {
            userProfileDatabase.getProfile()?.username
        }?.takeIf { it.isNotBlank() } ?: return DashboardLaunchMode.NONE
        val isServerReachable = withContext(Dispatchers.IO) {
            serverConnectivityRepository.checkServerConnectivity(baseUrl).reachable
        }
        if (!isServerReachable) {
            return DashboardLaunchMode.OFFLINE
        }

        val refreshed = userProfileSync.refreshProfile(baseUrl, cachedUsername, storedToken)
        return if (refreshed) {
            DashboardLaunchMode.ONLINE
        } else {
            authService.logout()
            DashboardLaunchMode.NONE
        }
    }





    @SuppressLint("HardwareIds")
    private fun cacheDeviceIdentifiers() {
        val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)

        val androidId = runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val storedUniqueId = preferences.getString(KEY_DEVICE_UNIQUE_ANDROID_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val uniqueAndroidId = storedUniqueId ?: generateUniqueAndroidId()
        val customDeviceName = resolveCustomDeviceName().trim()

        with(preferences.edit()) {
            androidId?.let { putString(KEY_DEVICE_ANDROID_ID, it) }
            putString(KEY_DEVICE_UNIQUE_ANDROID_ID, uniqueAndroidId)
            putString(KEY_DEVICE_CUSTOM_DEVICE_NAME, customDeviceName)
            apply()
        }
    }

    private fun generateUniqueAndroidId(): String {
        return UUID.randomUUID().toString()
    }

    private fun resolveCustomDeviceName(): String {
        val globalName = runCatching {
            Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        if (globalName != null) {
            return globalName
        }

        val systemName = runCatching {
            Settings.System.getString(contentResolver, "device_name")
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        if (systemName != null) {
            return systemName
        }

        return org.ole.planet.myplanet.lite.util.DeviceUtils.getDeviceName()
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2000L
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_UNIQUE_ANDROID_ID = "device_unique_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
    }

    private enum class DashboardLaunchMode {
        NONE,
        ONLINE,
        OFFLINE
    }
}

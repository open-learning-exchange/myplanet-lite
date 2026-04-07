/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import java.util.UUID
import kotlin.text.Charsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.profile.UserProfileSync
import org.ole.planet.myplanet.lite.util.IntentUtils

class SplashScreen : AppCompatActivity() {

    private val connectivityClient: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    private val connectivityManager by lazy { getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    private val userProfileDatabase: UserProfileDatabase by lazy {
        UserProfileDatabase.getInstance(applicationContext)
    }
    private val userProfileSync: UserProfileSync by lazy {
        UserProfileSync(connectivityClient, userProfileDatabase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        cacheDeviceIdentifiers()
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash_screen)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
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
        lifecycleScope.launch {
            delay(SPLASH_DELAY_MS)
            val launchMode = attemptDirectDashboardLaunch()
            val nextIntent = when (launchMode) {
                DashboardLaunchMode.ONLINE -> Intent(this@SplashScreen, DashboardActivity::class.java)
                DashboardLaunchMode.OFFLINE -> Intent(this@SplashScreen, DashboardActivity::class.java).apply {
                    putExtra(DashboardActivity.EXTRA_OFFLINE_MODE, true)
                }
                DashboardLaunchMode.NONE -> Intent(this@SplashScreen, MyPlanetLite::class.java)
            }

            if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
                if (launchMode == DashboardLaunchMode.NONE) {
                    nextIntent.action = intent.action
                    nextIntent.data = intent.data
                } else {
                    val postId = IntentUtils.extractDeepLinkPostId(intent)
                    if (postId != null) {
                        nextIntent.putExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID, postId)
                    }
                }
            }

            startActivity(nextIntent)
            finish()
        }
    }

    private suspend fun attemptDirectDashboardLaunch(): DashboardLaunchMode {
        val preferences = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
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
        if (!isDeviceOnline()) {
            return DashboardLaunchMode.OFFLINE
        }
        val refreshed = userProfileSync.refreshProfile(baseUrl, cachedUsername, storedToken)
        if (refreshed) {
            return DashboardLaunchMode.ONLINE
        }
        authService.logout()
        return DashboardLaunchMode.NONE
    }

    private fun isDeviceOnline(): Boolean {
        val manager = connectivityManager ?: return false
        val activeNetwork = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun applyDeviceOrientationLock() {
        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        requestedOrientation = if (isTablet) {
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    @SuppressLint("HardwareIds")
    private fun cacheDeviceIdentifiers() {
        val preferences = applicationContext.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val androidId = runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }

        val storedUniqueId = preferences.getString(KEY_DEVICE_UNIQUE_ANDROID_ID, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        val uniqueAndroidId = storedUniqueId ?: generateUniqueAndroidId(androidId)
        val customDeviceName = resolveCustomDeviceName().trim()

        with(preferences.edit()) {
            androidId?.let { putString(KEY_DEVICE_ANDROID_ID, it) }
            putString(KEY_DEVICE_UNIQUE_ANDROID_ID, uniqueAndroidId)
            putString(KEY_DEVICE_CUSTOM_DEVICE_NAME, customDeviceName)
            apply()
        }
    }

    private fun generateUniqueAndroidId(androidId: String?): String {
        val source = buildString {
            if (!androidId.isNullOrBlank()) {
                append(androidId)
            }
            append(':').append(Build.BRAND)
            append(':').append(Build.DEVICE)
            append(':').append(Build.FINGERPRINT)
            append(':').append(Build.HARDWARE)
            append(':').append(Build.ID)
            append(':').append(Build.MODEL)
        }

        return runCatching {
            UUID.nameUUIDFromBytes(source.toByteArray(Charsets.UTF_8)).toString()
        }.getOrElse {
            UUID.randomUUID().toString()
        }
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

        return formatManufacturerModel()
    }

    private fun formatManufacturerModel(): String {
        val manufacturer = Build.MANUFACTURER.trim()
        val model = Build.MODEL.trim()

        return when {
            manufacturer.isEmpty() && model.isEmpty() -> {
                val device = Build.DEVICE.trim()
                if (device.isNotEmpty()) device else DEFAULT_DEVICE_NAME
            }
            manufacturer.isEmpty() -> model
            model.isEmpty() -> manufacturer
            model.startsWith(manufacturer, ignoreCase = true) -> model
            else -> "$manufacturer $model"
        }
    }

    companion object {
        private const val SPLASH_DELAY_MS = 2000L
        private const val PREFS_NAME = "server_preferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_UNIQUE_ANDROID_ID = "device_unique_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        private const val DEFAULT_DEVICE_NAME = "Android Device"
    }

    private enum class DashboardLaunchMode {
        NONE,
        ONLINE,
        OFFLINE
    }
}

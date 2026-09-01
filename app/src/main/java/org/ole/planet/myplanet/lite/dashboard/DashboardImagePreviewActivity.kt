/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite.dashboard

import android.os.Bundle
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewAdapter

class DashboardImagePreviewActivity : AppCompatActivity() {
    private lateinit var viewPager: ViewPager2
    private lateinit var progressBar: ProgressBar

    private var imageLoader: DashboardPostImageLoader? = null
    private var adapter: DashboardImagePreviewAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_image_preview)

        viewPager = findViewById(R.id.dashboardPreviewPager)
        progressBar = findViewById(R.id.dashboardPreviewLoading)
        val imagePaths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS)?.filter { it.isNotBlank() }
        if (imagePaths.isNullOrEmpty()) {
            finish()
            return
        }
        val startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0).coerceIn(0, imagePaths.size - 1)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                val baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
                val requiresServerBase =
                    imagePaths.any { path ->
                        val trimmed = path.trim()
                        val scheme =
                            android.net.Uri
                                .parse(trimmed)
                                .scheme
                                ?.lowercase()
                        trimmed.isNotEmpty() &&
                            scheme != "http" &&
                            scheme != "https" &&
                            scheme != "file"
                    }
                if (requiresServerBase && baseUrl.isNullOrEmpty()) {
                    finish()
                    return@repeatOnLifecycle
                }
                val sessionCookie =
                    if (!baseUrl.isNullOrEmpty()) {
                        val authService = AuthDependencies.provideAuthService(applicationContext, baseUrl)
                        authService.getStoredToken()
                    } else {
                        null
                    }
                imageLoader = DashboardPostImageLoader(baseUrl.orEmpty(), sessionCookie, lifecycleScope)
                setupPager(imagePaths, startIndex)
            }
        }
    }

    private fun setupPager(
        imagePaths: List<String>,
        startIndex: Int,
    ) {
        val loader = imageLoader ?: return
        adapter =
            DashboardImagePreviewAdapter(imagePaths, loader) {
                finish()
            }
        viewPager.adapter = adapter
        viewPager.setCurrentItem(startIndex, false)
        viewPager.isUserInputEnabled = imagePaths.size > 1
        progressBar.isVisible = false
    }

    companion object {
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
        const val EXTRA_START_INDEX = "extra_start_index"
    }
}

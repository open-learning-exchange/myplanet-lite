/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-01-10
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.createBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.github.chrisbanes.photoview.PhotoView
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

class FullscreenPdfActivity : AppCompatActivity() {

    private val httpClient = OkHttpClient()
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_fullscreen_pdf)
        hideSystemBars()

        val progressView: View = findViewById(R.id.fullscreenPdfLoading)
        val pager: ViewPager2 = findViewById(R.id.fullscreenPdfPager)
        val pageIndicator: TextView = findViewById(R.id.fullscreenPdfPageIndicator)
        findViewById<ImageButton>(R.id.fullscreenPdfExitButton).setOnClickListener {
            showSystemBars()
            finish()
        }

        val pdfUrl = intent.getStringExtra(EXTRA_PDF_URL)
        if (pdfUrl.isNullOrBlank()) {
            finish()
            return
        }
        val authHeader = intent.getStringExtra(EXTRA_AUTH_HEADER) ?: resolveAuthHeader()

        lifecycleScope.launch {
            progressView.visibility = View.VISIBLE
            val file = downloadPdf(pdfUrl, authHeader)
            if (file == null) {
                Toast.makeText(
                    this@FullscreenPdfActivity,
                    getString(R.string.course_wizard_play_error),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            pdfFile = file
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor = fd
            pdfRenderer = PdfRenderer(fd)
            val renderer = pdfRenderer
            if (renderer == null || renderer.pageCount == 0) {
                Toast.makeText(
                    this@FullscreenPdfActivity,
                    getString(R.string.course_wizard_play_error),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
                return@launch
            }
            val pageCount = renderer.pageCount
            pager.adapter = PdfPageAdapter(renderer)
            pageIndicator.text = getString(
                R.string.course_wizard_pdf_page_counter,
                1,
                pageCount
            )
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    pageIndicator.text = getString(
                        R.string.course_wizard_pdf_page_counter,
                        position + 1,
                        pageCount
                    )
                }
            })
            progressView.visibility = View.GONE
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

    private fun saveToTempFile(response: Response): File? {
        val body = response.body
        val file = File.createTempFile("course_resource_", ".pdf", cacheDir)
        body.byteStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private suspend fun downloadPdf(url: String, authHeader: String?): File? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val parsedUri = android.net.Uri.parse(url)
                if (parsedUri.scheme == "file") {
                    val localFile = File(parsedUri.path.orEmpty())
                    if (localFile.exists()) {
                        return@withContext localFile
                    }
                }
                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (!authHeader.isNullOrBlank()) {
                            addHeader("Authorization", authHeader)
                        }
                    }
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    saveToTempFile(response)
                }
            }.getOrNull()
        }
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

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
        fileDescriptor?.close()
        pdfRenderer = null
        fileDescriptor = null
        pdfFile?.delete()
        pdfFile = null
    }

    private class PdfPageAdapter(
        private val renderer: PdfRenderer
    ) : RecyclerView.Adapter<PdfPageAdapter.PdfPageViewHolder>() {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): PdfPageViewHolder {
            val imageView = PhotoView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.MATCH_PARENT
                )
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                maximumScale = 6.0f
            }
            return PdfPageViewHolder(imageView)
        }

        override fun getItemCount(): Int = renderer.pageCount

        override fun onBindViewHolder(holder: PdfPageViewHolder, position: Int) {
            val page = renderer.openPage(position)
            val bitmap = createBitmap(page.width, page.height)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            holder.bind(bitmap)
        }

        override fun onViewRecycled(holder: PdfPageViewHolder) {
            holder.clear()
        }

        class PdfPageViewHolder(private val imageView: PhotoView) : RecyclerView.ViewHolder(imageView) {
            fun bind(bitmap: Bitmap) {
                imageView.setImageBitmap(bitmap)
            }

            fun clear() {
                imageView.setImageDrawable(null)
            }
        }
    }

    companion object {
        private const val EXTRA_PDF_URL = "extra_pdf_url"
        private const val EXTRA_AUTH_HEADER = "extra_auth_header"

        fun createIntent(context: Context, pdfUrl: String, authorizationHeader: String?): Intent {
            return Intent(context, FullscreenPdfActivity::class.java).apply {
                putExtra(EXTRA_PDF_URL, pdfUrl)
                putExtra(EXTRA_AUTH_HEADER, authorizationHeader)
            }
        }
    }
}

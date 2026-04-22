package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FullscreenPdfActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `activity finishes when launched without EXTRA_PDF_URL`() {
        val controller = Robolectric.buildActivity(FullscreenPdfActivity::class.java)
        val activity = controller.create().get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `createIntent returns correct intent with extras`() {
        val pdfUrl = "https://example.com/file.pdf"
        val authHeader = "Basic dXNlcjpwYXNz"

        val intent = FullscreenPdfActivity.createIntent(context, pdfUrl, authHeader)

        assertEquals(FullscreenPdfActivity::class.java.name, intent.component?.className)
        assertEquals(pdfUrl, intent.getStringExtra("extra_pdf_url"))
        assertEquals(authHeader, intent.getStringExtra("extra_auth_header"))
    }
}

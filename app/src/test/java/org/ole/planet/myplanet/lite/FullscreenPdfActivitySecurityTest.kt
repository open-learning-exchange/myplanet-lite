package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.net.InetAddress
import android.net.Uri

@RunWith(RobolectricTestRunner::class)
class FullscreenPdfActivitySecurityTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun isSafeLocalFile(context: Context, uri: Uri): Boolean {
        val method = FullscreenPdfActivity::class.java.getDeclaredMethod(
            "isSafeLocalFile",
            Context::class.java,
            android.net.Uri::class.java
        )
        method.isAccessible = true
        val controller = org.robolectric.Robolectric.buildActivity(FullscreenPdfActivity::class.java)
        val activity = controller.get()
        return method.invoke(activity, context, uri) as Boolean
    }

    private fun isSafeHttpUrl(url: String): Boolean {
        val method = FullscreenPdfActivity::class.java.getDeclaredMethod(
            "isSafeHttpUrl",
            String::class.java
        )
        method.isAccessible = true
        val controller = org.robolectric.Robolectric.buildActivity(FullscreenPdfActivity::class.java)
        val activity = controller.get()
        return method.invoke(activity, url) as Boolean
    }

    @Test
    fun `isSafeLocalFile rejects arbitrary file access`() {
        val maliciousUri = Uri.parse("file:///etc/passwd")
        assertFalse(isSafeLocalFile(context, maliciousUri))
    }

    @Test
    fun `isSafeLocalFile accepts safe file access`() {
        val safeFile = File(context.filesDir, "safe.pdf")
        val safeUri = Uri.parse("file://${safeFile.absolutePath}")
        assertTrue(isSafeLocalFile(context, safeUri))
    }

    @Test
    fun `isSafeHttpUrl rejects SSRF attempts`() {
        assertFalse(isSafeHttpUrl("http://localhost:8080/admin"))
        assertFalse(isSafeHttpUrl("http://127.0.0.1/admin"))
        assertFalse(isSafeHttpUrl("http://10.0.0.1/internal"))
    }

    @Test
    fun `isSafeHttpUrl accepts public URLs`() {
        assertTrue(isSafeHttpUrl("https://example.com/file.pdf"))
    }
}

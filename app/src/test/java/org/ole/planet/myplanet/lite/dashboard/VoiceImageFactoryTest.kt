package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class VoiceImageFactoryTest {

    private lateinit var context: Context
    private lateinit var cacheDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        cacheDir = File(context.cacheDir, "test_cache")
        cacheDir.mkdirs()
    }

    @Test
    fun testGenerateImageFileName() {
        val fileName = VoiceImageFactory.generateImageFileName()
        assertTrue(fileName.startsWith("post"))
        assertTrue(fileName.endsWith(".jpg"))
    }

    @Test
    fun testCreatePendingVoiceImage_smallImage() {
        // Create a small bitmap
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val imageFile = File(context.cacheDir, "small.png")
        FileOutputStream(imageFile).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val uri = Uri.fromFile(imageFile)

        val pendingImage = VoiceImageFactory.createPendingVoiceImage(
            uri,
            context.contentResolver,
            cacheDir
        ) { fileName -> "id_$fileName" }

        assertNotNull(pendingImage)
        assertTrue(pendingImage.fileName.startsWith("post"))
        assertTrue(pendingImage.file.exists())
        assertEquals(pendingImage.id, "id_${pendingImage.fileName}")
        assertTrue(pendingImage.jpegBytes.isNotEmpty())
    }

    @Test
    fun testCreatePendingVoiceImage_largeImage() {
        // Create a large bitmap
        val bitmap = Bitmap.createBitmap(2000, 1500, Bitmap.Config.ARGB_8888)
        val imageFile = File(context.cacheDir, "large.png")
        FileOutputStream(imageFile).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val uri = Uri.fromFile(imageFile)

        val pendingImage = VoiceImageFactory.createPendingVoiceImage(
            uri,
            context.contentResolver,
            cacheDir
        ) { fileName -> "id_$fileName" }

        assertNotNull(pendingImage)
        assertTrue(pendingImage.file.exists())

        // Let's decode the compressed image to verify its size is scaled down
        val decoded = android.graphics.BitmapFactory.decodeByteArray(pendingImage.jpegBytes, 0, pendingImage.jpegBytes.size)
        assertTrue(decoded.width <= 1280)
        assertTrue(decoded.height <= 1280)
        // Since max side is 2000 (width), scaled width should be 1280, height should be 1500 * (1280/2000) = 960
        assertEquals(1280, decoded.width)
        assertEquals(960, decoded.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreatePendingVoiceImage_invalidUri() {
        val mockContext = mock(Context::class.java)
        val mockResolver = mock(android.content.ContentResolver::class.java)
        `when`(mockContext.contentResolver).thenReturn(mockResolver)

        val invalidUri = Uri.parse("content://invalid/uri")
        `when`(mockResolver.openInputStream(invalidUri)).thenReturn(null)

        VoiceImageFactory.createPendingVoiceImage(
            invalidUri,
            mockResolver,
            cacheDir
        ) { "id" }
    }
}

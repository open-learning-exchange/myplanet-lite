package org.ole.planet.myplanet.lite.dashboard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PendingVoiceImageTest {

    @Test
    fun testPendingVoiceImage_initialization_withDefaultValues() {
        val file = File("test.jpg")
        val jpegBytes = byteArrayOf(1, 2, 3)
        val image = PendingVoiceImage(
            id = "123",
            fileName = "test.jpg",
            file = file,
            jpegBytes = jpegBytes
        )

        assertEquals("123", image.id)
        assertEquals("test.jpg", image.fileName)
        assertEquals(file, image.file)
        assertArrayEquals(jpegBytes, image.jpegBytes)
        assertNull(image.resourceId)
        assertNull(image.resourceRevision)
        assertNull(image.uploadedMarkdown)
    }

    @Test
    fun testPendingVoiceImage_initialization_withAllValues() {
        val file = File("test.jpg")
        val jpegBytes = byteArrayOf(1, 2, 3)
        val image = PendingVoiceImage(
            id = "123",
            fileName = "test.jpg",
            file = file,
            jpegBytes = jpegBytes,
            resourceId = "resId",
            resourceRevision = "resRev",
            uploadedMarkdown = "markdown"
        )

        assertEquals("123", image.id)
        assertEquals("test.jpg", image.fileName)
        assertEquals(file, image.file)
        assertArrayEquals(jpegBytes, image.jpegBytes)
        assertEquals("resId", image.resourceId)
        assertEquals("resRev", image.resourceRevision)
        assertEquals("markdown", image.uploadedMarkdown)
    }

    @Test
    fun testPendingVoiceImage_mutableProperties() {
        val file = File("test.jpg")
        val jpegBytes = byteArrayOf(1, 2, 3)
        val image = PendingVoiceImage(
            id = "123",
            fileName = "test.jpg",
            file = file,
            jpegBytes = jpegBytes
        )

        image.resourceId = "newResId"
        image.resourceRevision = "newResRev"
        image.uploadedMarkdown = "newMarkdown"

        assertEquals("newResId", image.resourceId)
        assertEquals("newResRev", image.resourceRevision)
        assertEquals("newMarkdown", image.uploadedMarkdown)
    }
}

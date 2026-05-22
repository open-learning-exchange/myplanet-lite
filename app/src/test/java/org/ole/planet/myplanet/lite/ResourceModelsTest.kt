package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceModelsTest {

    @Test
    fun `storedResourceKey formats correctly for team resource`() {
        val key = storedResourceKey(id = "123", filename = "doc.pdf", isTeamResource = true)
        assertEquals("team::123::doc.pdf", key)
    }

    @Test
    fun `storedResourceKey formats correctly for community resource`() {
        val key = storedResourceKey(id = "456", filename = "video.mp4", isTeamResource = false)
        assertEquals("community::456::video.mp4", key)
    }

    @Test
    fun `uniqueKey delegates to storedResourceKey correctly`() {
        val resourceUi = ResourceUi(
            id = "789",
            filename = "audio.mp3",
            name = "Test Audio",
            type = "audio/mp3",
            date = "2023-01-01",
            createdDate = 1672531200000L,
            isDownloaded = true,
            isDownloadable = false,
            isTeamResource = true
        )
        val key = resourceUi.uniqueKey()
        assertEquals("team::789::audio.mp3", key)
    }

    @Test
    fun `resourceIdentityKey returns id when not blank`() {
        val resourceUi = ResourceUi(
            id = "abc",
            filename = "file.txt",
            name = "Test File",
            type = "text/plain",
            date = "2023-01-01",
            createdDate = null,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )
        val key = resourceUi.resourceIdentityKey()
        assertEquals("abc", key)
    }

    @Test
    fun `resourceIdentityKey falls back to uniqueKey when id is blank`() {
        val resourceUi = ResourceUi(
            id = "   ",
            filename = "file.txt",
            name = "Test File",
            type = "text/plain",
            date = "2023-01-01",
            createdDate = null,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )
        val key = resourceUi.resourceIdentityKey()
        assertEquals("community::   ::file.txt", key)
    }

    @Test
    fun `resourceIdentityKey falls back to uniqueKey when id is empty`() {
        val resourceUi = ResourceUi(
            id = "",
            filename = "file.txt",
            name = "Test File",
            type = "text/plain",
            date = "2023-01-01",
            createdDate = null,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )
        val key = resourceUi.resourceIdentityKey()
        assertEquals("community::::file.txt", key)
    }
}

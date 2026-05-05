package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResourceMenuActionTest {

    @Test
    fun testFromMenuItemId_validIds() {
        assertEquals(ResourceMenuAction.AddPdf, ResourceMenuAction.fromMenuItemId(R.id.action_add_pdf))
        assertEquals(ResourceMenuAction.AddImage, ResourceMenuAction.fromMenuItemId(R.id.action_add_image))
        assertEquals(ResourceMenuAction.AddVideo, ResourceMenuAction.fromMenuItemId(R.id.action_add_video))
        assertEquals(ResourceMenuAction.AddAudio, ResourceMenuAction.fromMenuItemId(R.id.action_add_audio))
        assertEquals(ResourceMenuAction.TakePhoto, ResourceMenuAction.fromMenuItemId(R.id.action_take_photo))
        assertEquals(ResourceMenuAction.TakeVideo, ResourceMenuAction.fromMenuItemId(R.id.action_take_video))
        assertEquals(ResourceMenuAction.RecordAudio, ResourceMenuAction.fromMenuItemId(R.id.action_record_audio))
    }

    @Test
    fun testFromMenuItemId_invalidId() {
        assertNull(ResourceMenuAction.fromMenuItemId(-1))
        assertNull(ResourceMenuAction.fromMenuItemId(0))
    }

    @Test
    fun testEnumProperties() {
        assertEquals(R.id.action_add_pdf, ResourceMenuAction.AddPdf.menuItemId)
        assertEquals(R.id.action_add_image, ResourceMenuAction.AddImage.menuItemId)
        assertEquals(R.id.action_add_video, ResourceMenuAction.AddVideo.menuItemId)
        assertEquals(R.id.action_add_audio, ResourceMenuAction.AddAudio.menuItemId)
        assertEquals(R.id.action_take_photo, ResourceMenuAction.TakePhoto.menuItemId)
        assertEquals(R.id.action_take_video, ResourceMenuAction.TakeVideo.menuItemId)
        assertEquals(R.id.action_record_audio, ResourceMenuAction.RecordAudio.menuItemId)
    }
}

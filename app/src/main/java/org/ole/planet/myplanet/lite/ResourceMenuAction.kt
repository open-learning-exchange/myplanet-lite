package org.ole.planet.myplanet.lite

internal enum class ResourceMenuAction(val menuItemId: Int) {
    AddPdf(R.id.action_add_pdf),
    AddImage(R.id.action_add_image),
    AddVideo(R.id.action_add_video),
    AddAudio(R.id.action_add_audio),
    TakePhoto(R.id.action_take_photo),
    TakeVideo(R.id.action_take_video),
    RecordAudio(R.id.action_record_audio);

    companion object {
        fun fromMenuItemId(menuItemId: Int): ResourceMenuAction? {
            return entries.firstOrNull { it.menuItemId == menuItemId }
        }
    }
}

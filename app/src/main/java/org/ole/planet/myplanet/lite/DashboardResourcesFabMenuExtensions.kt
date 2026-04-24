package org.ole.planet.myplanet.lite

import android.view.ContextThemeWrapper
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.MenuCompat
import androidx.core.content.FileProvider
import java.io.File

internal fun DashboardResourcesPageFragment.showAddResourceMenu(anchor: View) {
    val themedContext = ContextThemeWrapper(requireContext(), R.style.Widget_MyPlanet_PopupMenu)
    val popup = PopupMenu(themedContext, anchor)
    popup.inflate(R.menu.menu_dashboard_resources_add)
    MenuCompat.setGroupDividerEnabled(popup.menu, true)
    forceShowMenuIcons(popup)
    startFabMenuSpin(anchor)
    popup.setOnMenuItemClickListener { menuItem ->
        val action = ResourceMenuAction.fromMenuItemId(menuItem.itemId)
        if (action == null) {
            false
        } else {
            handleResourceMenuAction(action)
            true
        }
    }
    popup.setOnDismissListener {
        stopFabMenuSpin()
    }
    popup.show()
}

internal fun DashboardResourcesPageFragment.startFabMenuSpin(fab: View) {
    stopFabMenuSpin()
    spinningFab = fab
    isFabMenuSpinActive = true
    runFabSpinCycle(fab)
}

internal fun DashboardResourcesPageFragment.stopFabMenuSpin() {
    isFabMenuSpinActive = false
    spinningFab?.animate()?.cancel()
    spinningFab?.rotation = 0f
    spinningFab = null
}

internal fun DashboardResourcesPageFragment.runFabSpinCycle(fab: View) {
    if (!isFabMenuSpinActive) {
        return
    }
    fab.animate()
        .rotationBy(360f)
        .setDuration(800)
        .setInterpolator(LinearInterpolator())
        .withEndAction {
            if (!isFabMenuSpinActive) {
                return@withEndAction
            }
            fab.animate()
                .rotationBy(800f)
                .setDuration(800)
                .setInterpolator(LinearInterpolator())
                .withEndAction {
                    if (!isFabMenuSpinActive) {
                        return@withEndAction
                    }
                    fab.animate()
                        .rotationBy(360f)
                        .setDuration(800)
                        .setInterpolator(LinearInterpolator())
                        .withEndAction {
                            if (!isFabMenuSpinActive) {
                                return@withEndAction
                            }
                            fab.postDelayed(
                                {
                                    runFabSpinCycle(fab)
                                },
                                10L
                            )
                        }
                        .start()
                }
                .start()
        }
        .start()
}

internal fun DashboardResourcesPageFragment.executeResourceMenuAction(action: ResourceMenuAction) {
    when (action) {
        ResourceMenuAction.AddPdf -> pickPdfLauncher.launch(arrayOf("application/pdf"))
        ResourceMenuAction.AddImage -> pickImageLauncher.launch(arrayOf("image/*"))
        ResourceMenuAction.AddVideo -> pickVideoLauncher.launch(arrayOf("video/*"))
        ResourceMenuAction.TakePhoto -> {
            val context = requireContext()
            val sharedImagesDir = File(context.cacheDir, "shared_images")
            if (!sharedImagesDir.exists()) {
                sharedImagesDir.mkdirs()
            }
            val photoFile = File(sharedImagesDir, "captured_photo_${System.currentTimeMillis()}.jpg")
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, photoFile)
            latestPhotoUri = uri
            takePhotoLauncher.launch(uri)
        }
        ResourceMenuAction.TakeVideo -> captureVideo()
        ResourceMenuAction.AddAudio -> pickAudioLauncher.launch(arrayOf("audio/*"))
        ResourceMenuAction.RecordAudio -> showRecordAudioPopup()
    }
}

internal fun DashboardResourcesPageFragment.forceShowMenuIcons(popup: PopupMenu) {
    runCatching {
        val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
        popupField.isAccessible = true
        val menuPopupHelper = popupField.get(popup)
        val setForceIcons = menuPopupHelper.javaClass.getDeclaredMethod(
            "setForceShowIcon",
            Boolean::class.javaPrimitiveType
        )
        setForceIcons.invoke(menuPopupHelper, true)
    }
}

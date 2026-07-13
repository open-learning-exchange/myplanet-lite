package org.ole.planet.myplanet.lite

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaPickerPolicyTest {
    @Test
    fun visualMediaFlowsUsePhotoPickerInsteadOfGetContent() {
        val appSources = sourceRoot("src/main/java")
            .walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .joinToString("\n") { it.readText() }

        assertFalse(appSources.contains("ActivityResultContracts.GetContent"))
        assertTrue(appSources.contains("ActivityResultContracts.PickVisualMedia"))
        assertTrue(appSources.contains("PickVisualMedia.ImageOnly"))
        assertTrue(appSources.contains("PickVisualMedia.VideoOnly"))
    }

    @Test
    fun manifestRemovesBroadMediaAndStoragePermissions() {
        val manifest = sourceRoot("src/main/AndroidManifest.xml").readText()

        listOf(
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
        ).forEach { permission ->
            val permissionIndex = manifest.indexOf(permission)
            assertTrue("$permission should be explicitly removed", permissionIndex >= 0)
            val followingManifest = manifest.substring(permissionIndex)
            assertTrue(
                "$permission should use tools:node=\"remove\"",
                followingManifest.substringBefore("/>").contains("tools:node=\"remove\""),
            )
        }
    }

    private fun sourceRoot(path: String): File {
        val userDir = File(System.getProperty("user.dir"))
        return listOf(
            File(userDir, path),
            File(userDir, "app/$path"),
        ).first { it.exists() }
    }
}

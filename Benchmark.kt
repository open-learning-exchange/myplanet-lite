import java.util.Locale

data class LessonResource(val mediaType: String)

fun main() {
    val resources = List(100000) {
        when (it % 5) {
            0 -> LessonResource("Video/MP4")
            1 -> LessonResource("Image/JPEG")
            2 -> LessonResource("application/pdf")
            3 -> LessonResource("Audio/MP3")
            else -> LessonResource("application/json")
        }
    }

    // Warmup
    for (i in 1..10) {
        testOld(resources)
        testNew(resources)
        testIgnore(resources)
    }

    val start1 = System.nanoTime()
    for (i in 1..100) testOld(resources)
    val end1 = System.nanoTime()

    val start2 = System.nanoTime()
    for (i in 1..100) testNew(resources)
    val end2 = System.nanoTime()

    val start3 = System.nanoTime()
    for (i in 1..100) testIgnore(resources)
    val end3 = System.nanoTime()

    println("Old: ${(end1 - start1) / 1000000} ms")
    println("New: ${(end2 - start2) / 1000000} ms")
    println("Ignore Case: ${(end3 - start3) / 1000000} ms")
}

fun testOld(resources: List<LessonResource>) {
    val videoResources = resources.filter { it.mediaType.lowercase(Locale.ROOT).contains("video") }
    val imageResources = resources.filter { it.mediaType.lowercase(Locale.ROOT).contains("image") }
    val displayResources = resources.filter { resource ->
        val mediaType = resource.mediaType.lowercase(Locale.ROOT)
        mediaType.contains("video") || mediaType.contains("pdf") || mediaType.contains("image") ||
                mediaType.contains("audio")
    }
}

fun testNew(resources: List<LessonResource>) {
    val videoResources = mutableListOf<LessonResource>()
    val imageResources = mutableListOf<LessonResource>()
    val displayResources = mutableListOf<LessonResource>()

    resources.forEach { resource ->
        val mediaType = resource.mediaType.lowercase(Locale.ROOT)
        val isVideo = mediaType.contains("video")
        val isImage = mediaType.contains("image")
        val isPdf = mediaType.contains("pdf")
        val isAudio = mediaType.contains("audio")

        if (isVideo) videoResources.add(resource)
        if (isImage) imageResources.add(resource)
        if (isVideo || isImage || isPdf || isAudio) {
            displayResources.add(resource)
        }
    }
}

fun testIgnore(resources: List<LessonResource>) {
    val videoResources = mutableListOf<LessonResource>()
    val imageResources = mutableListOf<LessonResource>()
    val displayResources = mutableListOf<LessonResource>()

    resources.forEach { resource ->
        val mediaType = resource.mediaType
        val isVideo = mediaType.contains("video", ignoreCase = true)
        val isImage = mediaType.contains("image", ignoreCase = true)
        val isPdf = mediaType.contains("pdf", ignoreCase = true)
        val isAudio = mediaType.contains("audio", ignoreCase = true)

        if (isVideo) videoResources.add(resource)
        if (isImage) imageResources.add(resource)
        if (isVideo || isImage || isPdf || isAudio) {
            displayResources.add(resource)
        }
    }
}

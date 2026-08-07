package org.ole.planet.myplanet.lite

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion

object OfflineCourseStorage {

    private const val ROOT_DIR = ".offline_courses"
    private const val MANIFEST = "course.json"

    fun isCourseDownloaded(context: Context, courseId: String): Boolean {
        return manifestFile(context, courseId).exists()
    }

    fun downloadedCourseIds(context: Context): Set<String> {
        val root = File(context.filesDir, ROOT_DIR)
        if (!root.exists()) return emptySet()
        return root.listFiles()
            ?.filter { it.isDirectory && File(it, MANIFEST).exists() }
            ?.map { it.name }
            ?.toSet()
            ?: emptySet()
    }

    enum class DownloadSource { MY_COURSES, TEAM_COURSES }

    fun loadDownloadedCourses(
        context: Context,
        source: DownloadSource? = null
    ): List<CourseItem> {
        return downloadedCourseIds(context).mapNotNull { courseId ->
            runCatching {
                val json = manifestFile(context, courseId).readText()
                parseCourse(JSONObject(json))
            }.getOrNull()
        }.filter { item ->
            source == null || item.downloadSource == source
        }
    }

    fun saveCourseManifest(
        context: Context,
        course: CourseItem,
        source: DownloadSource
    ) {
        val file = manifestFile(context, course.id)
        file.parentFile?.mkdirs()
        val localCover = course.coverPath
            ?.let { coverFile(context, course.id, it) }
            ?.takeIf { it.exists() }
            ?.toURI()
            ?.toString()
        file.writeText(serializeCourse(course.copy(coverPath = localCover ?: course.coverPath), source).toString())
    }

    fun resourceFile(
        context: Context,
        courseId: String,
        resourceId: String,
        filename: String
    ): File {
        return File(courseDir(context, courseId), "resources/$resourceId/$filename")
    }

    fun findExistingResourceFile(
        context: Context,
        courseId: String,
        resourceId: String,
        filename: String
    ): File? {
        val candidates = listOf(
            filename,
            Uri.decode(filename),
            Uri.encode(filename)
        ).distinct()
        return candidates
            .map { resourceFile(context, courseId, resourceId, it) }
            .firstOrNull { it.exists() }
    }

    fun availableStorageBytes(context: Context): Long {
        return context.filesDir.usableSpace
    }

    fun deleteCourse(context: Context, courseId: String): Boolean {
        val safeCourseId = courseId.trim().takeIf { it.isNotEmpty() } ?: return false
        if (safeCourseId.contains("..") || safeCourseId.contains("/") || safeCourseId.contains("\\")) {
            return false
        }
        val dir = courseDir(context, safeCourseId)
        return !dir.exists() || dir.deleteRecursively()
    }

    fun markdownImageFile(context: Context, courseId: String, source: String): File {
        val digest = sha256(source)
        val extension = source.substringAfterLast('.', "").substringBefore('?').substringBefore('#')
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
            ?.lowercase()
            ?: "img"
        return File(courseDir(context, courseId), "markdown/$digest.$extension")
    }

    fun coverFile(context: Context, courseId: String, source: String): File {
        val extension = source.substringBefore('?').substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
            ?.lowercase()
            ?: "img"
        return File(courseDir(context, courseId), "cover/${sha256(source)}.$extension")
    }

    private fun legacyMarkdownImageFile(context: Context, courseId: String, source: String): File {
        val digest = legacySha1(source)
        val extension = source.substringAfterLast('.', "").substringBefore('?').substringBefore('#')
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,5}")) }
            ?.lowercase()
            ?: "img"
        return File(courseDir(context, courseId), "markdown/$digest.$extension")
    }

    fun localMarkdownImageUri(context: Context, courseId: String, source: String): String? {
        val file = markdownImageFile(context, courseId, source)
        if (file.exists()) return file.toURI().toString()

        val legacyFile = legacyMarkdownImageFile(context, courseId, source)
        if (legacyFile.exists()) {
            if (legacyFile.renameTo(file)) {
                return file.toURI().toString()
            }
            return legacyFile.toURI().toString()
        }

        return null
    }

    private fun courseDir(context: Context, courseId: String): File {
        return File(File(context.filesDir, ROOT_DIR), courseId)
    }

    private fun manifestFile(context: Context, courseId: String): File {
        return File(courseDir(context, courseId), MANIFEST)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun legacySha1(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun serializeCourse(
        course: CourseItem,
        source: DownloadSource
    ): JSONObject {
        val steps = JSONArray().apply {
            course.steps.forEach { step ->
                put(
                    JSONObject()
                        .put("title", step.title)
                        .put("description", step.description)
                        .put("mediaTypes", JSONArray(step.mediaTypes))
                        .put("survey", serializeSurveyDocument(step.survey))
                        .put("exam", serializeSurveyDocument(step.exam))
                        .put(
                            "resources",
                            JSONArray().apply {
                                step.resources.forEach { resource ->
                                    put(
                                        JSONObject()
                                            .put("id", resource.id)
                                            .put("filename", resource.filename)
                                            .put("mediaType", resource.mediaType)
                                    )
                                }
                            }
                        )
                )
            }
        }
        return JSONObject()
            .put("id", course.id)
            .put("title", course.title)
            .put("description", course.description)
            .put("coverPath", course.coverPath)
            .put("rating", course.rating)
            .put("progressPercent", course.progressPercent)
            .put("currentStep", course.currentStep)
            .put("downloadSource", source.name)
            .put("steps", steps)
    }

    private fun parseCourse(json: JSONObject): CourseItem {
        val stepsArray = json.optJSONArray("steps") ?: JSONArray()
        val steps = (0 until stepsArray.length()).mapNotNull { i ->
            val step = stepsArray.optJSONObject(i) ?: return@mapNotNull null
            val mediaTypesArray = step.optJSONArray("mediaTypes") ?: JSONArray()
            val mediaTypes = (0 until mediaTypesArray.length()).mapNotNull { j ->
                mediaTypesArray.optString(j).takeIf { it.isNotBlank() }
            }
            val resourcesArray = step.optJSONArray("resources") ?: JSONArray()
            val resources = (0 until resourcesArray.length()).mapNotNull { j ->
                val resource = resourcesArray.optJSONObject(j) ?: return@mapNotNull null
                val id = resource.optString("id")
                val filename = resource.optString("filename")
                if (id.isBlank() || filename.isBlank()) return@mapNotNull null
                CourseItem.LessonResource(
                    id = id,
                    filename = filename,
                    mediaType = resource.optString("mediaType")
                )
            }
            CourseItem.LessonStep(
                title = step.optString("title"),
                description = step.optString("description"),
                mediaTypes = mediaTypes,
                resources = resources,
                survey = parseSurveyDocument(step.optJSONObject("survey")),
                exam = parseSurveyDocument(step.optJSONObject("exam"))
            )
        }

        return CourseItem(
            id = json.optString("id"),
            title = json.optString("title"),
            description = json.optString("description"),
            coverPath = json.optString("coverPath").takeIf { it.isNotBlank() },
            steps = steps,
            rating = json.optDouble("rating", 4.0),
            progressPercent = json.optInt("progressPercent", 0),
            currentStep = json.optInt("currentStep", -1).takeIf { it >= 0 },
            downloadSource = runCatching {
                DownloadSource.valueOf(json.optString("downloadSource", DownloadSource.MY_COURSES.name))
            }.getOrDefault(DownloadSource.MY_COURSES)
        )
    }

    private fun serializeSurveyDocument(document: SurveyDocument?): JSONObject? {
        if (document == null) return null
        return JSONObject()
            .put("id", document.id)
            .put("rev", document.rev)
            .put("name", document.name)
            .put("description", document.description)
            .put("passingPercentage", document.passingPercentage)
            .put("sourceSurveyId", document.sourceSurveyId)
            .put("teamId", document.teamId)
            .put("createdDate", document.createdDate)
            .put("totalMarks", document.totalMarks)
            .put(
                "questions",
                JSONArray().apply {
                    document.questions.orEmpty().forEach { question ->
                        put(
                            JSONObject()
                                .put("body", question.body)
                                .put("type", question.type)
                                .put("correctChoice", kotlinToJsonValue(question.correctChoice))
                                .put("marks", question.marks)
                                .put("hasOtherOption", question.hasOtherOption)
                                .put("scaleMax", question.scaleMax)
                                .put(
                                    "choices",
                                    JSONArray().apply {
                                        question.choices.orEmpty().forEach { choice ->
                                            put(
                                                JSONObject()
                                                    .put("text", choice.text)
                                                    .put("id", choice.id)
                                            )
                                        }
                                    }
                                )
                        )
                    }
                }
            )
    }

    private fun parseSurveyDocument(json: JSONObject?): SurveyDocument? {
        if (json == null) return null
        val questionsArray = json.optJSONArray("questions") ?: JSONArray()
        val questions = (0 until questionsArray.length()).mapNotNull { i ->
            val item = questionsArray.optJSONObject(i) ?: return@mapNotNull null
            val choicesArray = item.optJSONArray("choices") ?: JSONArray()
            val choices = (0 until choicesArray.length()).mapNotNull { j ->
                val choice = choicesArray.optJSONObject(j) ?: return@mapNotNull null
                SurveyChoice(
                    text = choice.optString("text").takeIf { it.isNotBlank() },
                    id = choice.optString("id").takeIf { it.isNotBlank() }
                )
            }
            SurveyQuestion(
                body = item.optString("body").takeIf { it.isNotBlank() },
                type = item.optString("type").takeIf { it.isNotBlank() },
                correctChoice = jsonToKotlinValue(item.opt("correctChoice")),
                marks = item.optInt("marks").takeIf { item.has("marks") && !item.isNull("marks") },
                choices = choices,
                hasOtherOption = item.optBoolean("hasOtherOption", false),
                scaleMax = item.optInt("scaleMax").takeIf { item.has("scaleMax") && !item.isNull("scaleMax") }
            )
        }
        return SurveyDocument(
            id = json.optString("id").takeIf { it.isNotBlank() },
            rev = json.optString("rev").takeIf { it.isNotBlank() },
            name = json.optString("name").takeIf { it.isNotBlank() },
            description = json.optString("description").takeIf { it.isNotBlank() },
            passingPercentage = json.optInt("passingPercentage")
                .takeIf { json.has("passingPercentage") && !json.isNull("passingPercentage") },
            sourceSurveyId = json.optString("sourceSurveyId").takeIf { it.isNotBlank() },
            teamId = json.optString("teamId").takeIf { it.isNotBlank() },
            createdDate = json.optString("createdDate").takeIf { it.isNotBlank() },
            questions = questions,
            totalMarks = json.optInt("totalMarks")
                .takeIf { json.has("totalMarks") && !json.isNull("totalMarks") }
        )
    }

    private fun kotlinToJsonValue(value: Any?): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, mapValue) ->
                    if (key != null) {
                        put(key.toString(), kotlinToJsonValue(mapValue))
                    }
                }
            }
            is Iterable<*> -> JSONArray().apply {
                value.forEach { item -> put(kotlinToJsonValue(item)) }
            }
            is Array<*> -> JSONArray().apply {
                value.forEach { item -> put(kotlinToJsonValue(item)) }
            }
            else -> value
        }
    }

    private fun jsonToKotlinValue(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> buildMap {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, jsonToKotlinValue(value.opt(key)))
                }
            }
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    add(jsonToKotlinValue(value.opt(index)))
                }
            }
            else -> value
        }
    }
}

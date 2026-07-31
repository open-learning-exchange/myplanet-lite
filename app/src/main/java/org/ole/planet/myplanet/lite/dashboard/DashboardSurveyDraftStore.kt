package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import org.ole.planet.myplanet.lite.SurveyAnswer
import org.ole.planet.myplanet.lite.SurveyRespondent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.security.MessageDigest

internal class DashboardSurveyDraftStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    @Synchronized
    fun save(entry: DraftEntry): Boolean =
        runCatching {
            directory.mkdirs()
            val destination = fileFor(entry.key)
            val temporary = File(directory, "${destination.name}.tmp")
            ObjectOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeObject(entry)
                output.flush()
            }
            if (destination.exists()) destination.delete()
            temporary.renameTo(destination)
        }.getOrDefault(false)

    @Synchronized
    fun get(key: String): DraftEntry? = read(fileFor(key))

    @Synchronized
    fun getForTeam(teamId: String?, owner: String?): List<DraftEntry> =
        directory.listFiles()
            .orEmpty()
            .mapNotNull(::read)
            .filter { it.teamId == teamId && it.owner == owner }
            .sortedByDescending { it.updatedAt }

    @Synchronized
    fun delete(key: String): Boolean = !fileFor(key).exists() || fileFor(key).delete()

    private fun read(file: File): DraftEntry? =
        runCatching {
            ObjectInputStream(file.inputStream().buffered()).use { it.readObject() as? DraftEntry }
        }.getOrNull()

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(directory, "$name.draft")
    }

    data class DraftEntry(
        val key: String,
        val document: SurveyDocument,
        val teamId: String?,
        val teamName: String?,
        val owner: String?,
        val currentIndex: Int,
        val answers: Map<Int, SurveyAnswer>,
        val respondent: SurveyRespondent,
        val birthDateSelection: Long?,
        val updatedAt: Long,
    ) : Serializable {
        val surveyId: String?
            get() = document.id

        val surveyRev: String?
            get() = document.rev
    }

    companion object {
        private const val DIRECTORY_NAME = "survey_drafts"

        fun key(surveyId: String?, teamId: String?, owner: String?): String =
            listOf(teamId.orEmpty(), surveyId.orEmpty(), owner.orEmpty()).joinToString("|")
    }
}

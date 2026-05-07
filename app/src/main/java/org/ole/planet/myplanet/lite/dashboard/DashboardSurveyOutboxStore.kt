/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-19
 */

package org.ole.planet.myplanet.lite.dashboard

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.util.getStringOrNull

class DashboardSurveyOutboxStore private constructor(
    context: Context,
    moshi: Moshi = Moshi.Builder()
        .add(FlexibleSurveyJsonAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build(),
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val submissionAdapter = moshi.adapter(SurveySubmission::class.java)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE outbox_submissions(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                survey_id TEXT,
                team_id TEXT,
                team_name TEXT,
                survey_name TEXT,
                created_at INTEGER NOT NULL,
                payload TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_outbox_team_id ON outbox_submissions(team_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < DATABASE_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS outbox_submissions")
            onCreate(db)
        }
    }

    suspend fun saveSubmission(
        submission: SurveySubmission,
        surveyId: String?,
        surveyName: String?,
        teamId: String?,
        teamName: String?,
    ): Boolean {
        val serialized = submissionAdapter.toJson(submission) ?: return false
        return withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COLUMN_SURVEY_ID, surveyId)
                put(COLUMN_TEAM_ID, teamId)
                put(COLUMN_TEAM_NAME, teamName)
                put(COLUMN_SURVEY_NAME, surveyName)
                put(COLUMN_CREATED_AT, System.currentTimeMillis())
                put(COLUMN_PAYLOAD, serialized)
            }
            writableDatabase.insert(TABLE_SUBMISSIONS, null, values) != -1L
        }
    }

    suspend fun getPendingForTeam(teamId: String?): List<OutboxEntry> = withContext(Dispatchers.IO) {
        val rawEntries = readableDatabase.query(
            TABLE_SUBMISSIONS,
            arrayOf(
                COLUMN_ID,
                COLUMN_SURVEY_ID,
                COLUMN_TEAM_ID,
                COLUMN_TEAM_NAME,
                COLUMN_SURVEY_NAME,
                COLUMN_CREATED_AT,
                COLUMN_PAYLOAD,
            ),
            if (teamId.isNullOrBlank()) null else "$COLUMN_TEAM_ID = ?",
            if (teamId.isNullOrBlank()) null else arrayOf(teamId),
            null,
            null,
            "$COLUMN_CREATED_AT DESC, $COLUMN_ID DESC",
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val surveyIdIdx = cursor.getColumnIndexOrThrow(COLUMN_SURVEY_ID)
            val teamIdIdx = cursor.getColumnIndexOrThrow(COLUMN_TEAM_ID)
            val teamNameIdx = cursor.getColumnIndexOrThrow(COLUMN_TEAM_NAME)
            val surveyNameIdx = cursor.getColumnIndexOrThrow(COLUMN_SURVEY_NAME)
            val createdAtIdx = cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)
            val payloadIdx = cursor.getColumnIndexOrThrow(COLUMN_PAYLOAD)

            buildList {
                while (cursor.moveToNext()) {
                    val payload = cursor.getStringOrNull(payloadIdx) ?: continue
                    add(
                        RawEntry(
                            id = cursor.getLong(idIdx),
                            surveyId = cursor.getStringOrNull(surveyIdIdx),
                            teamId = cursor.getStringOrNull(teamIdIdx),
                            teamName = cursor.getStringOrNull(teamNameIdx),
                            surveyName = cursor.getStringOrNull(surveyNameIdx),
                            createdAt = cursor.getLong(createdAtIdx),
                            payload = payload,
                        )
                    )
                }
            }
        }

        withContext(Dispatchers.Default) {
            rawEntries.mapNotNull { raw ->
                val parsed = try { submissionAdapter.fromJson(raw.payload) } catch(e: Exception) { null } ?: return@mapNotNull null
                OutboxEntry(
                    id = raw.id,
                    surveyId = raw.surveyId,
                    teamId = raw.teamId,
                    teamName = raw.teamName,
                    surveyName = raw.surveyName,
                    createdAt = raw.createdAt,
                    submission = parsed,
                )
            }
        }
    }

    suspend fun getEntry(id: Long): OutboxEntry? = withContext(Dispatchers.IO) {
        val rawEntry = readableDatabase.query(
            TABLE_SUBMISSIONS,
            arrayOf(
                COLUMN_ID,
                COLUMN_SURVEY_ID,
                COLUMN_TEAM_ID,
                COLUMN_TEAM_NAME,
                COLUMN_SURVEY_NAME,
                COLUMN_CREATED_AT,
                COLUMN_PAYLOAD,
            ),
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(COLUMN_ID)
            val surveyIdIdx = cursor.getColumnIndexOrThrow(COLUMN_SURVEY_ID)
            val teamIdIdx = cursor.getColumnIndexOrThrow(COLUMN_TEAM_ID)
            val teamNameIdx = cursor.getColumnIndexOrThrow(COLUMN_TEAM_NAME)
            val surveyNameIdx = cursor.getColumnIndexOrThrow(COLUMN_SURVEY_NAME)
            val createdAtIdx = cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)
            val payloadIdx = cursor.getColumnIndexOrThrow(COLUMN_PAYLOAD)

            if (cursor.moveToFirst()) {
                val payload = cursor.getStringOrNull(payloadIdx) ?: return@use null
                RawEntry(
                    id = cursor.getLong(idIdx),
                    surveyId = cursor.getStringOrNull(surveyIdIdx),
                    teamId = cursor.getStringOrNull(teamIdIdx),
                    teamName = cursor.getStringOrNull(teamNameIdx),
                    surveyName = cursor.getStringOrNull(surveyNameIdx),
                    createdAt = cursor.getLong(createdAtIdx),
                    payload = payload,
                )
            } else {
                null
            }
        }

        if (rawEntry != null) {
            withContext(Dispatchers.Default) {
                val parsed = try { submissionAdapter.fromJson(rawEntry.payload) } catch(e: Exception) { null } ?: return@withContext null
                OutboxEntry(
                    id = rawEntry.id,
                    surveyId = rawEntry.surveyId,
                    teamId = rawEntry.teamId,
                    teamName = rawEntry.teamName,
                    surveyName = rawEntry.surveyName,
                    createdAt = rawEntry.createdAt,
                    submission = parsed,
                )
            }
        } else {
            null
        }
    }

    suspend fun deleteEntry(id: Long): Boolean = withContext(Dispatchers.IO) {
        writableDatabase.delete(TABLE_SUBMISSIONS, "$COLUMN_ID = ?", arrayOf(id.toString())) > 0
    }

    data class OutboxEntry(
        val id: Long,
        val surveyId: String?,
        val teamId: String?,
        val teamName: String?,
        val surveyName: String?,
        val createdAt: Long,
        val submission: SurveySubmission,
    )

    private class RawEntry(
        val id: Long,
        val surveyId: String?,
        val teamId: String?,
        val teamName: String?,
        val surveyName: String?,
        val createdAt: Long,
        val payload: String,
    )

    companion object {
        private const val DATABASE_NAME = "dashboard_survey_outbox.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SUBMISSIONS = "outbox_submissions"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SURVEY_ID = "survey_id"
        private const val COLUMN_TEAM_ID = "team_id"
        private const val COLUMN_TEAM_NAME = "team_name"
        private const val COLUMN_SURVEY_NAME = "survey_name"
        private const val COLUMN_CREATED_AT = "created_at"
        private const val COLUMN_PAYLOAD = "payload"

        @Volatile
        private var instance: DashboardSurveyOutboxStore? = null

        fun getInstance(context: Context): DashboardSurveyOutboxStore {
            return instance ?: synchronized(this) {
                instance ?: DashboardSurveyOutboxStore(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(context: Context) {
            instance?.close()
            instance = null
            context.deleteDatabase(DATABASE_NAME)
        }
    }
}

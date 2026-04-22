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
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.util.getStringOrNull

class DashboardOfflineSurveyStore(
    context: Context,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val documentAdapter = moshi.adapter(SurveyDocument::class.java)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SURVEYS(
                $COLUMN_ID TEXT PRIMARY KEY,
                $COLUMN_REV TEXT,
                $COLUMN_TEAM_ID TEXT,
                $COLUMN_DOCUMENT TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_surveys_team_id ON $TABLE_SURVEYS($COLUMN_TEAM_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < DATABASE_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS surveys")
            onCreate(db)
        }
    }

    suspend fun saveSurvey(document: SurveyDocument, fallbackTeamId: String?): Boolean {
        val id = document.id?.trim().orEmpty()
        if (id.isEmpty()) {
            return false
        }
        val serialized = documentAdapter.toJson(document) ?: return false
        val rev = document.rev
        val teamId = document.teamId ?: fallbackTeamId
        return withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COLUMN_ID, id)
                put(COLUMN_REV, rev)
                put(COLUMN_TEAM_ID, teamId)
                put(COLUMN_DOCUMENT, serialized)
            }
            writableDatabase.insertWithOnConflict(
                TABLE_SURVEYS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            ) != -1L
        }
    }

    suspend fun getSavedSurveyIds(): Set<String> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_SURVEYS,
            arrayOf(COLUMN_ID),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildSet {
                val idIndex = cursor.getColumnIndexOrThrow(COLUMN_ID)
                while (cursor.moveToNext()) {
                    add(cursor.getString(idIndex))
                }
            }
        }
    }

    suspend fun getSavedSurveysForTeam(teamId: String): List<SurveyDocument> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_SURVEYS,
            arrayOf(COLUMN_DOCUMENT),
            "$COLUMN_TEAM_ID = ?",
            arrayOf(teamId),
            null,
            null,
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    cursor.getStringOrNull(COLUMN_DOCUMENT)
                        ?.let { json -> documentAdapter.fromJson(json) }
                        ?.let { add(it) }
                }
            }
        }
    }

    suspend fun getSavedSurveyRevisions(): Map<String, String?> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            TABLE_SURVEYS,
            arrayOf(COLUMN_ID, COLUMN_REV),
            null,
            null,
            null,
            null,
            null,
        ).use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val id = cursor.getStringOrNull(COLUMN_ID)?.trim().orEmpty()
                    if (id.isNotEmpty()) {
                        put(id, cursor.getStringOrNull(COLUMN_REV))
                    }
                }
            }
        }
    }

    private companion object {
        private const val DATABASE_NAME = "dashboard_offline_surveys.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SURVEYS = "surveys"
        private const val COLUMN_ID = "id"
        private const val COLUMN_REV = "rev"
        private const val COLUMN_TEAM_ID = "team_id"
        private const val COLUMN_DOCUMENT = "document"
    }
}

/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-25
 */

package org.ole.planet.myplanet.lite.surveys

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.util.getStringOrNull

class SurveyTranslationCache private constructor(
    context: Context,
    moshi: Moshi = Moshi.Builder().build(),
) : SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    private val choicesAdapter = moshi.adapter<List<String?>>(TYPES_STRING_LIST)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_TRANSLATIONS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SURVEY_ID TEXT NOT NULL,
                $COLUMN_QUESTION_INDEX INTEGER NOT NULL,
                $COLUMN_TARGET_LANGUAGE TEXT NOT NULL,
                $COLUMN_BODY TEXT,
                $COLUMN_CHOICES TEXT,
                UNIQUE($COLUMN_SURVEY_ID, $COLUMN_QUESTION_INDEX, $COLUMN_TARGET_LANGUAGE)
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < DATABASE_VERSION) {
            db.execSQL("DROP TABLE IF EXISTS survey_translations")
            onCreate(db)
        }
    }

    suspend fun getTranslation(
        surveyId: String,
        questionIndex: Int,
        targetLanguage: String,
    ): SurveyTranslationManager.TranslatedQuestion? {
        val normalizedLanguage = targetLanguage.lowercase()
        return withContext(Dispatchers.IO) {
            val db = readableDatabase
            db.query(
                TABLE_TRANSLATIONS,
                arrayOf(COLUMN_BODY, COLUMN_CHOICES),
                "$COLUMN_SURVEY_ID = ? AND $COLUMN_QUESTION_INDEX = ? AND $COLUMN_TARGET_LANGUAGE = ?",
                arrayOf(surveyId, questionIndex.toString(), normalizedLanguage),
                null,
                null,
                null,
            ).use { c ->
                if (c.moveToFirst()) {
                    val indexBody = c.getColumnIndexOrThrow(COLUMN_BODY)
                    val indexChoices = c.getColumnIndexOrThrow(COLUMN_CHOICES)
                    val body = c.getStringOrNull(indexBody)
                    val choicesJson = c.getStringOrNull(indexChoices)
                    val choices = choicesJson?.let { choicesAdapter.fromJson(it) } ?: emptyList()
                    SurveyTranslationManager.TranslatedQuestion(body, choices)
                } else {
                    null
                }
            }
        }
    }

    suspend fun getTranslations(
        surveyId: String,
        targetLanguage: String,
    ): Map<Int, SurveyTranslationManager.TranslatedQuestion> {
        val normalizedLanguage = targetLanguage.lowercase()
        return withContext(Dispatchers.IO) {
            val db = readableDatabase
            val result = HashMap<Int, SurveyTranslationManager.TranslatedQuestion>()
            db.query(
                TABLE_TRANSLATIONS,
                arrayOf(COLUMN_QUESTION_INDEX, COLUMN_BODY, COLUMN_CHOICES),
                "$COLUMN_SURVEY_ID = ? AND $COLUMN_TARGET_LANGUAGE = ?",
                arrayOf(surveyId, normalizedLanguage),
                null,
                null,
                null,
            ).use { c ->
                val indexQuestionIndex = c.getColumnIndexOrThrow(COLUMN_QUESTION_INDEX)
                val indexBody = c.getColumnIndexOrThrow(COLUMN_BODY)
                val indexChoices = c.getColumnIndexOrThrow(COLUMN_CHOICES)

                while (c.moveToNext()) {
                    val questionIndex = c.getInt(indexQuestionIndex)
                    val body = c.getStringOrNull(indexBody)
                    val choicesJson = c.getStringOrNull(indexChoices)
                    val choices = choicesJson?.let { choicesAdapter.fromJson(it) } ?: emptyList()
                    result[questionIndex] = SurveyTranslationManager.TranslatedQuestion(body, choices)
                }
            }
            result
        }
    }

    suspend fun saveTranslations(
        surveyId: String,
        targetLanguage: String,
        translations: Map<Int, SurveyTranslationManager.TranslatedQuestion>,
    ) {
        if (translations.isEmpty()) return
        val normalizedLanguage = targetLanguage.lowercase()
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            db.beginTransaction()
            try {
                translations.forEach { (questionIndex, translation) ->
                    val values = ContentValues().apply {
                        put(COLUMN_SURVEY_ID, surveyId)
                        put(COLUMN_QUESTION_INDEX, questionIndex)
                        put(COLUMN_TARGET_LANGUAGE, normalizedLanguage)
                        put(COLUMN_BODY, translation.body)
                        put(COLUMN_CHOICES, choicesAdapter.toJson(translation.choices))
                    }
                    db.insertWithOnConflict(
                        TABLE_TRANSLATIONS,
                        null,
                        values,
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    suspend fun saveTranslation(
        surveyId: String,
        questionIndex: Int,
        targetLanguage: String,
        translation: SurveyTranslationManager.TranslatedQuestion,
    ) {
        val normalizedLanguage = targetLanguage.lowercase()
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(COLUMN_SURVEY_ID, surveyId)
                put(COLUMN_QUESTION_INDEX, questionIndex)
                put(COLUMN_TARGET_LANGUAGE, normalizedLanguage)
                put(COLUMN_BODY, translation.body)
                put(COLUMN_CHOICES, choicesAdapter.toJson(translation.choices))
            }
            writableDatabase.insertWithOnConflict(
                TABLE_TRANSLATIONS,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "survey_translations.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_TRANSLATIONS = "survey_translations"
        private const val COLUMN_ID = "id"
        private const val COLUMN_SURVEY_ID = "survey_id"
        private const val COLUMN_QUESTION_INDEX = "question_index"
        private const val COLUMN_TARGET_LANGUAGE = "target_language"
        private const val COLUMN_BODY = "body"
        private const val COLUMN_CHOICES = "choices"
        private val TYPES_STRING_LIST = Types.newParameterizedType(List::class.java, String::class.javaObjectType)

        @Volatile
        private var instance: SurveyTranslationCache? = null

        fun getInstance(context: Context): SurveyTranslationCache {
            return instance ?: synchronized(this) {
                instance ?: SurveyTranslationCache(context.applicationContext).also { instance = it }
            }
        }

        fun resetForTesting(context: Context) {
            instance?.close()
            instance = null
            context.deleteDatabase(DATABASE_NAME)
        }
    }
}

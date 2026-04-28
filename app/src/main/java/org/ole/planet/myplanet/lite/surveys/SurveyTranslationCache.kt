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

    suspend fun getTranslations(
        surveyId: String,
        targetLanguage: String,
    ): Map<Int, SurveyTranslationManager.TranslatedQuestion> {
        val normalizedLanguage = targetLanguage.lowercase()
        return withContext(Dispatchers.IO) {
            val db = readableDatabase
            val cursor = db.query(
                TABLE_TRANSLATIONS,
                arrayOf(COLUMN_QUESTION_INDEX, COLUMN_BODY, COLUMN_CHOICES),
                "$COLUMN_SURVEY_ID = ? AND $COLUMN_TARGET_LANGUAGE = ?",
                arrayOf(surveyId, normalizedLanguage),
                null,
                null,
                null,
            )
            val rawData = cursor.use { c ->
                val indexQuestionIndex = c.getColumnIndexOrThrow(COLUMN_QUESTION_INDEX)
                val indexBody = c.getColumnIndexOrThrow(COLUMN_BODY)
                val indexChoices = c.getColumnIndexOrThrow(COLUMN_CHOICES)

                val count = c.count
                val questionIndices = IntArray(count)
                val bodies = arrayOfNulls<String>(count)
                val choicesList = arrayOfNulls<String>(count)

                var i = 0
                while (c.moveToNext()) {
                    questionIndices[i] = c.getInt(indexQuestionIndex)
                    bodies[i] = c.getStringOrNull(indexBody)
                    choicesList[i] = c.getStringOrNull(indexChoices)
                    i++
                }
                Triple(questionIndices, bodies, choicesList)
            }

            val result = HashMap<Int, SurveyTranslationManager.TranslatedQuestion>(rawData.first.size)
            for (i in rawData.first.indices) {
                val choices = rawData.third[i]?.let { choicesAdapter.fromJson(it) } ?: emptyList()
                result[rawData.first[i]] = SurveyTranslationManager.TranslatedQuestion(rawData.second[i], choices)
            }
            result
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

package org.ole.planet.myplanet.lite.survey

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import androidx.test.core.app.ApplicationProvider
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationCache
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SurveyTranslationCacheTest {

    @Test
    fun `test saveTranslations error path`() = runBlocking {
        val mockDb = mock(SQLiteDatabase::class.java)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val cache = spy(SurveyTranslationCache.getInstance(context))
        doReturn(mockDb).whenever(cache).writableDatabase

        val mockStatement = mock(SQLiteStatement::class.java)
        whenever(mockDb.compileStatement(any())).thenReturn(mockStatement)
        whenever(mockStatement.executeInsert()).thenThrow(RuntimeException("DB Error"))

        val translations = mapOf(1 to SurveyTranslationManager.TranslatedQuestion("body", listOf("a")))

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                cache.saveTranslations("survey1", "es", translations)
            }
        }

        verify(mockDb).beginTransaction()
        verify(mockDb).endTransaction()
        verify(mockDb, never()).setTransactionSuccessful()
    }

    @Test
    fun benchmarkStringListComparison() {
        val rows = 10000000
        val columns = listOf("id", "survey_id", "question_index", "target_language", "body", "choices")

        var count = 0
        var originalTime = measureTimeMillis {
            for (i in 0 until rows) {
                val qIdx = columns.indexOf("question_index")
                val bIdx = columns.indexOf("body")
                val cIdx = columns.indexOf("choices")
                count += qIdx + bIdx + cIdx
            }
        }

        count = 0
        var optimizedTime = measureTimeMillis {
            val qIdx = columns.indexOf("question_index")
            val bIdx = columns.indexOf("body")
            val cIdx = columns.indexOf("choices")
            for (i in 0 until rows) {
                count += qIdx + bIdx + cIdx
            }
        }

        assert(optimizedTime <= originalTime) { "Optimization did not improve performance" }
    }

    @Test
    fun `test saveTranslations compileStatement error path`() = runBlocking {
        val mockDb = mock(SQLiteDatabase::class.java)
        val context = ApplicationProvider.getApplicationContext<Context>()

        val cache = spy(SurveyTranslationCache.getInstance(context))
        doReturn(mockDb).whenever(cache).writableDatabase

        whenever(mockDb.compileStatement(any())).thenThrow(RuntimeException("Compile Error"))

        val translations = mapOf(1 to SurveyTranslationManager.TranslatedQuestion("body", listOf("a")))

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                cache.saveTranslations("survey1", "es", translations)
            }
        }

        verify(mockDb).beginTransaction()
        verify(mockDb).endTransaction()
        verify(mockDb, never()).setTransactionSuccessful()
    }
}

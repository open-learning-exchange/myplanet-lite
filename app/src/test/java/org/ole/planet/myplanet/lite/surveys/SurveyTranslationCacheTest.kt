package org.ole.planet.myplanet.lite.surveys

import org.junit.Test
import kotlin.system.measureTimeMillis

class SurveyTranslationCacheTest {

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
        println("Original time: ${originalTime} ms")

        count = 0
        var optimizedTime = measureTimeMillis {
            val qIdx = columns.indexOf("question_index")
            val bIdx = columns.indexOf("body")
            val cIdx = columns.indexOf("choices")
            for (i in 0 until rows) {
                count += qIdx + bIdx + cIdx
            }
        }
        println("Optimized time: ${optimizedTime} ms")

        assert(optimizedTime <= originalTime) { "Optimization did not improve performance" }
    }
}

package org.ole.planet.myplanet.lite.profile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LearningLevelTranslatorTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testToEnglish_ReturnsNullForEmptyInput() {
        assertNull(LearningLevelTranslator.toEnglish(context, ""))
        assertNull(LearningLevelTranslator.toEnglish(context, null))
        assertNull(LearningLevelTranslator.toEnglish(context, "   "))
    }

    @Test
    fun testToEnglish_TranslatesSpanishToEnglish() {
        assertEquals("Beginner", LearningLevelTranslator.toEnglish(context, "Principiante"))
        assertEquals("Intermediate", LearningLevelTranslator.toEnglish(context, "Intermedio"))
        assertEquals("Advanced", LearningLevelTranslator.toEnglish(context, "Avanzado"))
        assertEquals("Expert", LearningLevelTranslator.toEnglish(context, "Experto"))
    }

    @Test
    fun testToEnglish_TranslatesFrenchToEnglish() {
        assertEquals("Beginner", LearningLevelTranslator.toEnglish(context, "Débutant"))
        assertEquals("Intermediate", LearningLevelTranslator.toEnglish(context, "Intermédiaire"))
        assertEquals("Advanced", LearningLevelTranslator.toEnglish(context, "Avancé"))
        assertEquals("Expert", LearningLevelTranslator.toEnglish(context, "Expert"))
    }

    @Test
    fun testToEnglish_CaseInsensitive() {
        assertEquals("Beginner", LearningLevelTranslator.toEnglish(context, " bEgInNeR "))
        assertEquals("Beginner", LearningLevelTranslator.toEnglish(context, " pRiNcIpIaNtE "))
        assertEquals("Advanced", LearningLevelTranslator.toEnglish(context, " aVaNcÉ "))
    }

    @Test
    fun testToEnglish_ReturnsOriginalIfNotFound() {
        assertEquals("UnknownLevel", LearningLevelTranslator.toEnglish(context, "UnknownLevel"))
    }

    @Test
    fun testToLocalized_ReturnsNullForEmptyInput() {
        assertNull(LearningLevelTranslator.toLocalized(context, "", R.array.signup_level_options_language_es))
        assertNull(LearningLevelTranslator.toLocalized(context, null, R.array.signup_level_options_language_es))
        assertNull(LearningLevelTranslator.toLocalized(context, "   ", R.array.signup_level_options_language_es))
    }

    @Test
    fun testToLocalized_TranslatesEnglishToSpanish() {
        assertEquals("Principiante", LearningLevelTranslator.toLocalized(context, "Beginner", R.array.signup_level_options_language_es))
        assertEquals("Intermedio", LearningLevelTranslator.toLocalized(context, "Intermediate", R.array.signup_level_options_language_es))
        assertEquals("Avanzado", LearningLevelTranslator.toLocalized(context, "Advanced", R.array.signup_level_options_language_es))
        assertEquals("Experto", LearningLevelTranslator.toLocalized(context, "Expert", R.array.signup_level_options_language_es))
    }

    @Test
    fun testToLocalized_TranslatesLocalizedToAnotherLocalized() {
        // Translates French to Spanish
        assertEquals("Principiante", LearningLevelTranslator.toLocalized(context, "Débutant", R.array.signup_level_options_language_es))
        assertEquals("Intermedio", LearningLevelTranslator.toLocalized(context, "Intermédiaire", R.array.signup_level_options_language_es))
        assertEquals("Avanzado", LearningLevelTranslator.toLocalized(context, "Avancé", R.array.signup_level_options_language_es))
        assertEquals("Experto", LearningLevelTranslator.toLocalized(context, "Expert", R.array.signup_level_options_language_es))
    }

    @Test
    fun testToLocalized_TranslatesEnglishToFrench() {
        assertEquals("Débutant", LearningLevelTranslator.toLocalized(context, "Beginner", R.array.signup_level_options_language_fr))
        assertEquals("Intermédiaire", LearningLevelTranslator.toLocalized(context, "Intermediate", R.array.signup_level_options_language_fr))
        assertEquals("Avancé", LearningLevelTranslator.toLocalized(context, "Advanced", R.array.signup_level_options_language_fr))
        assertEquals("Expert", LearningLevelTranslator.toLocalized(context, "Expert", R.array.signup_level_options_language_fr))
    }

    @Test
    fun testToLocalized_ReturnsOriginalIfNotFound() {
        assertEquals("UnknownLevel", LearningLevelTranslator.toLocalized(context, "UnknownLevel", R.array.signup_level_options_language_es))
    }
}

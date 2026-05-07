package org.ole.planet.myplanet.lite.survey

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.AiKeys
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.AiModels
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.ConfigurationDocument
import org.ole.planet.myplanet.lite.surveys.OpenAiTranslationClient
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationCache
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager

class SurveyTranslationManagerTest {

    private lateinit var appContext: Context
    private lateinit var configurationRepository: ServerConfigurationRepository
    private lateinit var languageIdentifier: LanguageIdentifier
    private lateinit var translationClient: OpenAiTranslationClient
    private lateinit var translationCache: SurveyTranslationCache
    private lateinit var manager: SurveyTranslationManager

    @Before
    fun setUp() {
        appContext = mock()
        configurationRepository = mock()
        languageIdentifier = mock()
        translationClient = mock()
        translationCache = mock()
        manager = SurveyTranslationManager(
            appContext = appContext,
            configurationRepository = configurationRepository,
            languageIdentifier = languageIdentifier,
            translationClient = translationClient,
            translationCache = translationCache
        )
    }

    private fun mockSurvey(id: String = "survey123", questions: List<SurveyQuestion>? = null): SurveyDocument {
        return SurveyDocument(
            id = id,
            name = "Test Survey",
            questions = questions
        )
    }

    private fun mockConfig(openAiKey: String? = "test-key", openAiModel: String? = "test-model"): ConfigurationDocument {
        return ConfigurationDocument(
            keys = AiKeys(openAi = openAiKey),
            models = AiModels(openAi = openAiModel)
        )
    }

    @Test
    fun `translateQuestion returns null when targetLanguage is blank`() = runTest {
        val survey = mockSurvey()
        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "   "
        )
        assertNull(result)
        verify(translationCache, never()).getTranslation(any(), any(), any())
    }

    @Test
    fun `translateQuestion returns cached translation when forceRetranslate is false and cache exists`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        val cachedTranslation = SurveyTranslationManager.TranslatedQuestion(
            body = "Cached Question",
            choices = listOf("Cached Choice")
        )
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(cachedTranslation)

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es"
        )

        assertEquals(cachedTranslation, result)
        verify(configurationRepository, never()).fetchConfiguration(any())
    }

    @Test
    fun `translateQuestion fetches configuration and returns null if config fails`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.failure(Exception("Network Error")))

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es"
        )

        assertNull(result)
    }

    @Test
    fun `translateQuestion returns null if OpenAI key is missing`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig(openAiKey = null)))

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es"
        )

        assertNull(result)
    }

    @Test
    fun `translateQuestion returns null if detected language is same as target language`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "ES",
            detectedLanguage = "es"
        )

        assertNull(result)
    }

    @Test
    fun `translateQuestion returns null when languages share same base tag`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es-MX",
            detectedLanguage = "es-ES"
        )

        assertNull(result)
        verify(translationClient, never()).translate(any(), any(), any(), any(), any())
    }

    @Test
    fun `translateQuestion returns null if question index is out of bounds`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Q1")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 1,
            targetLanguage = "es",
            detectedLanguage = "en"
        )

        assertNull(result)
    }

    @Test
    fun `translateQuestion translates and caches result successfully`() = runTest {
        val question = SurveyQuestion(
            body = "Hello",
            choices = listOf(SurveyChoice(text = "Yes"), SurveyChoice(text = "No"))
        )
        val survey = mockSurvey(id = "survey123", questions = listOf(question))

        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))

        whenever(translationClient.translate("Hello", "es", "test-key", "test-model", "en")).thenReturn("Hola")
        whenever(translationClient.translate("Yes", "es", "test-key", "test-model", "en")).thenReturn("Sí")
        whenever(translationClient.translate("No", "es", "test-key", "test-model", "en")).thenReturn("No")

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es",
            detectedLanguage = "en"
        )

        val expected = SurveyTranslationManager.TranslatedQuestion(
            body = "Hola",
            choices = listOf("Sí", "No")
        )
        assertEquals(expected, result)

        verify(translationCache).saveTranslation("survey123", 0, "es", expected)
    }

    @Test
    fun `translateQuestion ignores cache when forceRetranslate is true`() = runTest {
        val question = SurveyQuestion(body = "Hello")
        val survey = mockSurvey(id = "survey123", questions = listOf(question))
        val cachedTranslation = SurveyTranslationManager.TranslatedQuestion(body = "Old Translation")

        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(cachedTranslation)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))
        whenever(translationClient.translate("Hello", "es", "test-key", "test-model", "en")).thenReturn("Hola")

        val result = manager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es",
            detectedLanguage = "en",
            forceRetranslate = true
        )

        val expected = SurveyTranslationManager.TranslatedQuestion(
            body = "Hola",
            choices = emptyList()
        )
        assertEquals(expected, result)
        verify(translationCache).saveTranslation("survey123", 0, "es", expected)
    }

    @Test
    fun `translateSurvey returns cached questions when configuration fetch fails`() = runTest {
        val cachedQuestion = SurveyTranslationManager.TranslatedQuestion(body = "Pregunta cacheada")
        whenever(translationCache.getTranslations("survey123", "es")).thenReturn(mapOf(0 to cachedQuestion))
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.failure(Exception("Network Error")))

        val result = manager.translateSurvey(
            baseUrl = "http://test.com",
            survey = mockSurvey(questions = listOf(SurveyQuestion(body = "Original"))),
            targetLanguage = "es"
        )

        assertEquals(cachedQuestion, result.translations[0])
        assertTrue(result.translations.isNotEmpty())
    }
}

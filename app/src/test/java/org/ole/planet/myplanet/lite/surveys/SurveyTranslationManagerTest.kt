package org.ole.planet.myplanet.lite.surveys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.AiKeys
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.AiModels
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.ConfigurationDocument
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SurveyTranslationManagerTest {

    private lateinit var context: Context
    private lateinit var configurationRepository: ServerConfigurationRepository
    private lateinit var languageIdentifier: LanguageIdentifier
    private lateinit var translationClient: OpenAiTranslationClient
    private lateinit var translationCache: SurveyTranslationCache
    private lateinit var translationManager: SurveyTranslationManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        configurationRepository = mock()
        languageIdentifier = mock()
        translationClient = mock()

        translationCache = mock()

        translationManager = SurveyTranslationManager(
            appContext = context,
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
        val result = translationManager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "   "
        )
        assertNull(result)
        verify(translationCache, never()).getTranslation(any<String>(), any<Int>(), any<String>())
    }

    @Test
    fun `translateQuestion returns cached translation when forceRetranslate is false and cache exists`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        val cachedTranslation = SurveyTranslationManager.TranslatedQuestion(
            body = "Cached Question",
            choices = listOf("Cached Choice")
        )
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(cachedTranslation)

        val result = translationManager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es"
        )

        assertEquals(cachedTranslation, result)
        verify(configurationRepository, never()).fetchConfiguration(any<String>())
    }

    @Test
    fun `translateQuestion fetches configuration and returns null if config fails`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Original")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(null))

        val result = translationManager.translateQuestion(
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

        val result = translationManager.translateQuestion(
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

        val result = translationManager.translateQuestion(
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

        val result = translationManager.translateQuestion(
            baseUrl = "http://test.com",
            survey = survey,
            questionIndex = 0,
            targetLanguage = "es-MX",
            detectedLanguage = "es-ES"
        )

        assertNull(result)
        verify(translationClient, never()).translate(any<String>(), any<String>(), any<String>(), any<String>(), anyOrNull())
    }

    @Test
    fun `translateQuestion returns null if question index is out of bounds`() = runTest {
        val survey = mockSurvey(id = "survey123", questions = listOf(SurveyQuestion(body = "Q1")))
        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))

        val result = translationManager.translateQuestion(
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
            choices = listOf(org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice(text = "Yes"), org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice(text = "No"))
        )
        val survey = mockSurvey(id = "survey123", questions = listOf(question))

        whenever(translationCache.getTranslation(any(), any(), any())).thenReturn(null)
        whenever(configurationRepository.fetchConfiguration("http://test.com")).thenReturn(Result.success(mockConfig()))

        whenever(translationClient.translate("Hello", "es", "test-key", "test-model", "en")).thenReturn("Hola")
        whenever(translationClient.translate("Yes", "es", "test-key", "test-model", "en")).thenReturn("Sí")
        whenever(translationClient.translate("No", "es", "test-key", "test-model", "en")).thenReturn("No")

        val result = translationManager.translateQuestion(
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

        val result = translationManager.translateQuestion(
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
    fun `translateSurvey with blank target language returns null result`() = runTest {
        val survey = SurveyDocument(
            id = "survey1",
            sourceSurveyId = "source1",
            name = "Test Survey",
            description = "A test survey",
            questions = emptyList()
        )

        val result = translationManager.translateSurvey(
            baseUrl = "http://example.com",
            survey = survey,
            targetLanguage = "   ",
            detectedLanguage = "en"
        )

        assertNull(result.sourceLanguage)
        assertNull(result.titleTranslation)
        assertNull(result.descriptionTranslation)
        assertTrue(result.translations.isEmpty())
    }

    @Test
    fun `translateSurvey with same source and target language returns early result`() = runTest {
        val survey = SurveyDocument(
            id = "survey1",
            sourceSurveyId = "source1",
            name = "Test Survey",
            description = "A test survey",
            questions = emptyList()
        )

        val config = ConfigurationDocument(
            keys = AiKeys(openAi = "test-key"),
            models = AiModels(openAi = "gpt-test")
        )
        whenever(configurationRepository.fetchConfiguration(any())).thenReturn(Result.success(config))

        val result = translationManager.translateSurvey(
            baseUrl = "http://example.com",
            survey = survey,
            targetLanguage = "EN", // mixed case to check normalization
            detectedLanguage = "en"
        )

        assertEquals("en", result.sourceLanguage)
        assertNull(result.titleTranslation)
        assertNull(result.descriptionTranslation)
        assertTrue(result.translations.isEmpty())

        verifyNoInteractions(translationClient)
    }

    @Test
    fun `translateSurvey without configuration returns cached result`() = runTest {
        // Setup cache with some data
        val cachedTranslation = SurveyTranslationManager.TranslatedQuestion(
            body = "Cached Title",
            choices = emptyList()
        )
        whenever(translationCache.getTranslations("survey1", "es")).thenReturn(mapOf(-1 to cachedTranslation))

        val survey = SurveyDocument(
            id = "survey1",
            sourceSurveyId = "source1",
            name = "Test Survey",
            description = "A test survey",
            questions = emptyList()
        )

        // Mock config to return null (failure)
        whenever(configurationRepository.fetchConfiguration(any<String>())).thenReturn(Result.success(null))

        val result = translationManager.translateSurvey(
            baseUrl = "http://example.com",
            survey = survey,
            targetLanguage = "es",
            detectedLanguage = "en"
        )

        assertNull(result.sourceLanguage) // Source language isn't detected if no config
        assertEquals("Cached Title", result.titleTranslation)
        assertNull(result.descriptionTranslation)
    }

    @Test
    fun `translateSurvey with successful response returns proper translation`() = runTest {
        val survey = SurveyDocument(
            id = "survey1",
            sourceSurveyId = "source1",
            name = "Test Survey",
            description = "A test survey",
            questions = listOf(
                SurveyQuestion(body = "Hello", choices = emptyList())
            )
        )

        val config = ConfigurationDocument(
            keys = AiKeys(openAi = "test-key"),
            models = AiModels(openAi = "gpt-test")
        )
        whenever(configurationRepository.fetchConfiguration(any())).thenReturn(Result.success(config))

        // Mock translate methods for title, description, and questions
        whenever(translationClient.translate(
            text = eq("Test Survey"),
            targetLanguage = eq("es"),
            apiKey = eq("test-key"),
            model = eq("gpt-test"),
            sourceLanguage = eq("en")
        )).thenReturn("Encuesta de prueba")

        whenever(translationClient.translate(
            text = eq("A test survey"),
            targetLanguage = eq("es"),
            apiKey = eq("test-key"),
            model = eq("gpt-test"),
            sourceLanguage = eq("en")
        )).thenReturn("Una encuesta de prueba")

        whenever(translationClient.translate(
            text = eq("Hello"),
            targetLanguage = eq("es"),
            apiKey = eq("test-key"),
            model = eq("gpt-test"),
            sourceLanguage = eq("en")
        )).thenReturn("Hola")

        val result = translationManager.translateSurvey(
            baseUrl = "http://example.com",
            survey = survey,
            targetLanguage = "es",
            detectedLanguage = "en"
        )

        assertEquals("en", result.sourceLanguage)
        assertEquals("Encuesta de prueba", result.titleTranslation)
        assertEquals("Una encuesta de prueba", result.descriptionTranslation)
        assertEquals(1, result.translations.size)
        assertEquals("Hola", result.translations[0]?.body)
    }

    @Test
    fun `detectSurveyLanguage returns correct language`() = runTest {
        val survey = SurveyDocument(
            id = "survey1",
            name = "Hello world",
            questions = listOf(SurveyQuestion(body = "How are you?"))
        )

        // Mock the ML Kit Task manually since Tasks.forResult() might be problematic in tests with suspendCancellableCoroutine
        val mockTask: Task<String> = mock()
        whenever(languageIdentifier.identifyLanguage(any())).thenReturn(mockTask)

        whenever(mockTask.addOnSuccessListener(any<OnSuccessListener<in String>>())).thenAnswer { invocation ->
            val listener = invocation.getArgument<OnSuccessListener<String>>(0)
            listener.onSuccess("en")
            mockTask
        }
        whenever(mockTask.addOnFailureListener(any<OnFailureListener>())).thenReturn(mockTask)

        val language = translationManager.detectSurveyLanguage(survey)

        assertEquals("en", language)
        verify(languageIdentifier).identifyLanguage(eq("Hello world. How are you?"))
    }
}

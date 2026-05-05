package org.ole.planet.myplanet.lite.surveys

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.mlkit.nl.languageid.LanguageIdentifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.ConfigurationDocument
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.AiKeys
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository.AiModels
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

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

        SurveyTranslationCache.resetForTesting(context)
        translationCache = SurveyTranslationCache.getInstance(context)

        translationManager = SurveyTranslationManager(
            appContext = context,
            configurationRepository = configurationRepository,
            languageIdentifier = languageIdentifier,
            translationClient = translationClient,
            translationCache = translationCache
        )
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
        translationCache.saveTranslation("survey1", -1, "es", cachedTranslation)

        val survey = SurveyDocument(
            id = "survey1",
            sourceSurveyId = "source1",
            name = "Test Survey",
            description = "A test survey",
            questions = emptyList()
        )

        // Mock config to return null (failure)
        whenever(configurationRepository.fetchConfiguration(any())).thenReturn(Result.success(null))

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

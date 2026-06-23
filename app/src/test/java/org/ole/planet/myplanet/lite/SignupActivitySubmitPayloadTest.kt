package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignupActivitySubmitPayloadTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val mockPrefs = mock(SharedPreferences::class.java)
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        SecurePreferencesProvider.injectedPreferences = null
        mockWebServer.shutdown()
    }

    @Test
    fun `submitSignupPayload returns FAILED when username is empty`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        activity.usernameInput.setText("")

        val deferred = async { activity.submitSignupPayload() }

        testDispatcher.scheduler.advanceUntilIdle()

        val result = deferred.await()
        assertEquals(SignupSubmissionResult.FAILED, result)
    }

    @Test
    fun `submitSignupPayload returns FAILED when buildUsernameLookupUrl returns null`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        activity.usernameInput.setText("testuser")
        activity.serverBaseUrl = "" // Causes buildUsernameLookupUrl to return null

        val deferred = async { activity.submitSignupPayload() }

        testDispatcher.scheduler.advanceUntilIdle()

        val result = deferred.await()
        assertEquals(SignupSubmissionResult.FAILED, result)
    }

    @Test
    fun `submitSignupPayload executes signup request and returns SUCCESS on valid input`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        activity.usernameInput.setText("testuser")
        val url = mockWebServer.url("/").toString()
        activity.serverBaseUrl = url

        // Setup valid inputs to pass buildSignupPayload
        activity.passwordInput.setText("password123")
        activity.confirmPasswordInput.setText("password123")
        activity.genderGroup.check(R.id.signupGenderMale) // Need to select a gender for buildSignupPayload to not return null

        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val deferred = async { activity.submitSignupPayload() }

        delay(10)
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val result = deferred.await()
        assertEquals(SignupSubmissionResult.SUCCESS, result)
    }

    @Test
    fun `submitSignupPayload returns FAILED when buildSignupPayload returns null`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        activity.usernameInput.setText("testuser")
        val url = mockWebServer.url("/").toString()
        activity.serverBaseUrl = url

        // Gender is not selected, buildSignupPayload will return null

        val deferred = async { activity.submitSignupPayload() }

        testDispatcher.scheduler.advanceUntilIdle()

        val result = deferred.await()
        assertEquals(SignupSubmissionResult.FAILED, result)
    }
}

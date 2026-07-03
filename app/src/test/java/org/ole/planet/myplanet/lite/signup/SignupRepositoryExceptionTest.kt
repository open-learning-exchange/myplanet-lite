package org.ole.planet.myplanet.lite.signup

import org.ole.planet.myplanet.lite.UsernameAvailability
import org.ole.planet.myplanet.lite.SignupSubmissionResult

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
class SignupRepositoryExceptionTest {
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockWebServer: MockWebServer
    private lateinit var signupRepository: SignupRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockWebServer = MockWebServer()
        mockWebServer.start()
        signupRepository = SignupRepository(okhttp3.OkHttpClient(), testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mockWebServer.shutdown()
    }

    @Test
    fun `submitSignup returns FAILED on Exception`() = runTest(testDispatcher) {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))
        val url = mockWebServer.url("/").toString()
        val payload = org.json.JSONObject().apply { put("test", "test") }

        val deferred = async {
            signupRepository.submitSignup(url, "testUser", payload)
        }
        delay(10)
        testDispatcher.scheduler.advanceUntilIdle()

        val result = deferred.await()
        assertEquals("FAILED", result.toString())
    }
}

package org.ole.planet.myplanet.lite

import android.content.SharedPreferences
import android.os.Build
import android.os.Looper
import android.widget.AutoCompleteTextView
import android.widget.RadioGroup
import com.google.android.material.textfield.TextInputEditText
import java.lang.reflect.Method
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.ole.planet.myplanet.lite.profile.GENDER_MALE
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class SignupActivityTest {

    private lateinit var mockWebServer: MockWebServer
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putBoolean(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(mockEditor)
        `when`(mockEditor.remove(org.mockito.ArgumentMatchers.anyString())).thenReturn(mockEditor)
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

    private fun getMethod(activity: SignupActivity, methodName: String, vararg params: Class<*>): Method {
        val method = SignupActivity::class.java.getDeclaredMethod(methodName, *params)
        method.isAccessible = true
        return method
    }

    @Test
    fun `test validateUsername`() {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        val usernameInput = activity.findViewById<TextInputEditText>(R.id.signupUsernameInput)
        val validateUsernameMethod = getMethod(activity, "validateUsername")

        // Invalid: empty
        usernameInput.setText("")
        var result = validateUsernameMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Invalid: starts with number
        usernameInput.setText("1user")
        result = validateUsernameMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Invalid: special chars
        usernameInput.setText("user@name")
        result = validateUsernameMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Valid
        usernameInput.setText("validUser123")
        result = validateUsernameMethod.invoke(activity) as Boolean
        assertTrue(result)
    }

    @Test
    fun `test validateNames`() {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        val firstNameInput = activity.findViewById<TextInputEditText>(R.id.signupFirstNameInput)
        val lastNameInput = activity.findViewById<TextInputEditText>(R.id.signupLastNameInput)
        val validateNamesMethod = getMethod(activity, "validateNames")

        // Invalid: empty
        firstNameInput.setText("")
        lastNameInput.setText("")
        var result = validateNamesMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Invalid: only first name
        firstNameInput.setText("John")
        lastNameInput.setText("")
        result = validateNamesMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Valid
        firstNameInput.setText("John")
        lastNameInput.setText("Doe")
        result = validateNamesMethod.invoke(activity) as Boolean
        assertTrue(result)
    }

    @Test
    fun `test validateContact`() {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        val emailInput = activity.findViewById<TextInputEditText>(R.id.signupEmailInput)
        val phoneInput = activity.findViewById<TextInputEditText>(R.id.signupPhoneInput)
        val validateContactMethod = getMethod(activity, "validateContact")

        // Invalid
        emailInput.setText("invalid-email")
        phoneInput.setText("not-a-phone")
        var result = validateContactMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Valid email, invalid phone
        emailInput.setText("test@example.com")
        phoneInput.setText("abc")
        result = validateContactMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Valid both
        emailInput.setText("test@example.com")
        phoneInput.setText("1234567890")
        result = validateContactMethod.invoke(activity) as Boolean
        assertTrue(result)
    }

    @Test
    fun `test validatePasswords`() {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        val passwordInput = activity.findViewById<TextInputEditText>(R.id.signupPasswordInput)
        val confirmPasswordInput = activity.findViewById<TextInputEditText>(R.id.signupConfirmPasswordInput)
        val validatePasswordsMethod = getMethod(activity, "validatePasswords")

        // Invalid: empty
        passwordInput.setText("")
        confirmPasswordInput.setText("")
        var result = validatePasswordsMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Invalid: mismatch
        passwordInput.setText("password123")
        confirmPasswordInput.setText("password456")
        result = validatePasswordsMethod.invoke(activity) as Boolean
        assertFalse(result)

        // Valid
        passwordInput.setText("password123")
        confirmPasswordInput.setText("password123")
        result = validatePasswordsMethod.invoke(activity) as Boolean
        assertTrue(result)
    }

    @Test
    fun `test checkUsernameAvailability`() = runTest(testDispatcher) {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        val setServerBaseUrl = SignupActivity::class.java.getDeclaredField("serverBaseUrl")
        setServerBaseUrl.isAccessible = true
        setServerBaseUrl.set(activity, mockWebServer.url("/").toString())

        // Test AVAILABLE
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val method = SignupActivity::class.memberFunctions.find { it.name == "checkUsernameAvailability" }
        method?.isAccessible = true

        val deferredAvailable = async<Any?> {
            method?.callSuspend(activity, "newuser")
        }

        delay(100)
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val resultAvailable = deferredAvailable.await()
        assertEquals("AVAILABLE", resultAvailable.toString())

        // Test TAKEN
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val deferredTaken = async<Any?> {
            method?.callSuspend(activity, "existinguser")
        }

        delay(100)
        testDispatcher.scheduler.advanceUntilIdle()
        shadowOf(Looper.getMainLooper()).idle()

        val resultTaken = deferredTaken.await()
        assertEquals("TAKEN", resultTaken.toString())
    }

    @Test
    fun `test buildSignupPayload`() {
        val controller = Robolectric.buildActivity(SignupActivity::class.java).create().start().resume()
        val activity = controller.get()

        // Setup inputs
        val firstNameInput = activity.findViewById<TextInputEditText>(R.id.signupFirstNameInput)
        val lastNameInput = activity.findViewById<TextInputEditText>(R.id.signupLastNameInput)
        val emailInput = activity.findViewById<TextInputEditText>(R.id.signupEmailInput)
        val phoneInput = activity.findViewById<TextInputEditText>(R.id.signupPhoneInput)
        val passwordInput = activity.findViewById<TextInputEditText>(R.id.signupPasswordInput)
        val genderGroup = activity.findViewById<RadioGroup>(R.id.signupGenderGroup)
        val languageInput = activity.findViewById<AutoCompleteTextView>(R.id.signupLanguageInput)
        val levelInput = activity.findViewById<AutoCompleteTextView>(R.id.signupLevelInput)

        firstNameInput.setText("John")
        lastNameInput.setText("Doe")
        emailInput.setText("john@example.com")
        phoneInput.setText("1234567890")
        passwordInput.setText("password123")
        genderGroup.check(R.id.signupGenderMale)
        languageInput.setText("English")
        levelInput.setText("Primary")

        val buildSignupPayloadMethod = getMethod(activity, "buildSignupPayload", String::class.java)
        val result = buildSignupPayloadMethod.invoke(activity, "johndoe") as org.json.JSONObject?

        assertNotNull(result)
        assertEquals("johndoe", result?.getString("name"))
        assertEquals("John", result?.getString("firstName"))
        assertEquals("Doe", result?.getString("lastName"))
        assertEquals("john@example.com", result?.getString("email"))
        assertEquals("1234567890", result?.getString("phoneNumber"))
        assertEquals("password123", result?.getString("password"))
        assertEquals(GENDER_MALE, result?.getString("gender"))
        assertEquals("English", result?.getString("language"))
    }
}

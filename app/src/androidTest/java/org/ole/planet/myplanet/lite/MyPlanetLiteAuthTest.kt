/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */

package org.ole.planet.myplanet.lite

import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.auth.AuthService
import org.ole.planet.myplanet.lite.auth.LoginResponse
import org.ole.planet.myplanet.lite.auth.UserCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

@RunWith(AndroidJUnit4::class)
class MyPlanetLiteAuthTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val testPrefs = context.getSharedPreferences("auth_test_secure_prefs", android.content.Context.MODE_PRIVATE)
        testPrefs
            .edit()
            .clear()
            .putBoolean("survey_translation_consent_accepted", true)
            .putBoolean("survey_translations_enabled", false)
            .commit()
        SecurePreferencesProvider.injectedPreferences = testPrefs
    }

    @After
    fun tearDown() {
        AuthDependencies.overrideAuthService(null)
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun login_shows_validation_errors_for_empty_fields() {
        AuthDependencies.overrideAuthService(FakeService(AuthResult.Success(LoginResponse(ok = true))))

        ActivityScenario.launch(MyPlanetLite::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.loginButton).apply {
                    isEnabled = true
                    performClick()
                }
            }
            onView(isRoot()).perform(waitFor(300))

            onView(withId(R.id.usernameInputLayout)).check(
                matches(
                    hasTextInputLayoutErrorText(
                        getStringResource(R.string.login_username_error),
                    ),
                ),
            )
            onView(withId(R.id.passwordInputLayout)).check(
                matches(
                    hasTextInputLayoutErrorText(
                        getStringResource(R.string.login_password_error),
                    ),
                ),
            )
        }
    }

    @Test
    fun login_invalid_credentials_shows_error_message() {
        AuthDependencies.overrideAuthService(FakeService(AuthResult.Error(code = 401, message = "")))

        ActivityScenario.launch(MyPlanetLite::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<TextInputEditText>(R.id.usernameInput).setText("user@planet.com")
                activity.findViewById<TextInputEditText>(R.id.passwordInput).setText("badpass")
                activity.findViewById<AutoCompleteTextView>(R.id.serverUrlInput).apply {
                    setText("Test Server", false)
                    tag = "https://planet.example.org"
                }
                activity.findViewById<Button>(R.id.loginButton).apply {
                    isEnabled = true
                    performClick()
                }
            }
            onView(isRoot()).perform(waitFor(300))

            onView(withId(R.id.errorText)).check(matches(withText(R.string.login_invalid_credentials)))
        }
    }

    private fun waitFor(millis: Long): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()

            override fun getDescription(): String = "Wait for $millis milliseconds."

            override fun perform(
                uiController: UiController,
                view: View?,
            ) {
                uiController.loopMainThreadForAtLeast(millis)
            }
        }

    private fun getStringResource(
        resId: Int,
        vararg formatArgs: Any,
    ): String =
        androidx.test.platform.app.InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(resId, *formatArgs)

    private fun hasTextInputLayoutErrorText(expectedError: String): Matcher<in View> =
        object : org.hamcrest.TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("with TextInputLayout error: $expectedError")
            }

            override fun matchesSafely(item: View): Boolean = item is TextInputLayout && item.error == expectedError
        }

    private class FakeService(
        private val result: AuthResult,
    ) : AuthService {
        override suspend fun login(
            usernameOrEmail: String,
            password: String,
        ): AuthResult = result

        override suspend fun logout() {}

        override suspend fun getStoredToken(): String? = null

        override suspend fun authenticate(
            baseUrl: String,
            credentials: UserCredentials,
        ): AuthResult = result
    }
}

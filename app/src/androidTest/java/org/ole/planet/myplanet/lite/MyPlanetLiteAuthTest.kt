/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */

package org.ole.planet.myplanet.lite

import android.view.View

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.google.android.material.textfield.TextInputLayout
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.auth.AuthResult
import org.ole.planet.myplanet.lite.auth.AuthService
import org.ole.planet.myplanet.lite.auth.LoginResponse

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyPlanetLiteAuthTest {

    @After
    fun tearDown() {
        AuthDependencies.overrideAuthService(null)
    }

    @Test
    fun login_shows_validation_errors_for_empty_fields() {
        AuthDependencies.overrideAuthService(FakeService(AuthResult.Success(LoginResponse(ok = true))))

        ActivityScenario.launch(MyPlanetLite::class.java).use {
            onView(withId(R.id.loginButton)).perform(click())

            onView(withId(R.id.usernameInputLayout)).check(matches(hasTextInputLayoutErrorText(
                getStringResource(R.string.login_username_error)
            )))
            onView(withId(R.id.passwordInputLayout)).check(matches(hasTextInputLayoutErrorText(
                getStringResource(R.string.login_password_error)
            )))
        }
    }

    @Test
    fun login_invalid_credentials_shows_error_message() {
        AuthDependencies.overrideAuthService(FakeService(AuthResult.Error(code = 401, message = "")))

        ActivityScenario.launch(MyPlanetLite::class.java).use {
            onView(withId(R.id.usernameInput)).perform(typeText("user@planet.com"), closeSoftKeyboard())
            onView(withId(R.id.passwordInput)).perform(typeText("badpass"), closeSoftKeyboard())
            onView(withId(R.id.loginButton)).perform(click())

            onView(withId(R.id.errorText)).check(matches(withText(R.string.login_invalid_credentials)))
        }
    }

    private fun getStringResource(resId: Int, vararg formatArgs: Any): String =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.getString(resId, *formatArgs)

    private fun hasTextInputLayoutErrorText(expectedError: String): Matcher<in View> {
        return object : org.hamcrest.TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("with TextInputLayout error: $expectedError")
            }

            override fun matchesSafely(item: View): Boolean {
                return item is TextInputLayout && item.error == expectedError
            }
        }
    }

    private class FakeService(private val result: AuthResult) : AuthService {
        override suspend fun login(usernameOrEmail: String, password: String): AuthResult = result
        override suspend fun logout() {}
        override suspend fun getStoredToken(): String? = null
    }
}

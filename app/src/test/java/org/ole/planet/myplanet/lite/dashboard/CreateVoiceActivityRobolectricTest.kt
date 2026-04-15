package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateVoiceActivityRobolectricTest {

    @Test
    fun `test activity launches successfully`() {
        val controller = Robolectric.buildActivity(CreateVoiceActivity::class.java).create().start().resume()
        val activity = controller.get()
        assertNotNull(activity)
    }

    @Test
    fun `test initial UI state`() {
        val controller = Robolectric.buildActivity(CreateVoiceActivity::class.java).create().start().resume()
        val activity = controller.get()

        val createVoiceInput: TextInputEditText = activity.findViewById(R.id.createVoiceInput)
        val createVoiceSubmitButton: MaterialButton = activity.findViewById(R.id.createVoiceSubmitButton)

        assertNotNull(createVoiceInput)
        assertNotNull(createVoiceSubmitButton)
        assertEquals("", createVoiceInput.text.toString())
    }

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyPlanetLite)
        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("test", "testpass"))
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
    }

    @Test
    fun `attemptPost shows empty error toast when message is blank`() {
        val controller = Robolectric.buildActivity(CreateVoiceActivity::class.java).create().start().resume()
        val activity = controller.get()

        val submitButton: MaterialButton = activity.findViewById(R.id.createVoiceSubmitButton)
        submitButton.performClick()

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val latestToast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Expected a Toast to be shown", latestToast)
        assertEquals(context.getString(R.string.create_voice_empty_error), latestToast)
    }


    @Test
    fun `attemptPost shows missing server toast when base url is blank`() {
        val controller = Robolectric.buildActivity(CreateVoiceActivity::class.java).create().start().resume()
        val activity = controller.get()

        val input: TextInputEditText = activity.findViewById(R.id.createVoiceInput)
        input.setText("Some test voice message")

        val submitButton: MaterialButton = activity.findViewById(R.id.createVoiceSubmitButton)
        submitButton.performClick()

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val latestToast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Expected a Toast to be shown", latestToast)
        assertEquals(context.getString(R.string.create_voice_missing_server), latestToast)
    }

    @Test
    fun `attemptPost shows missing credentials toast when credentials are null`() {
        // Mocking or circumventing the secure preferences exception for now
        ProfileCredentialsStore.setSessionCredentials(null)
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
        val controller = Robolectric.buildActivity(CreateVoiceActivity::class.java).create().start().resume()
        val activity = controller.get()

        val input: TextInputEditText = activity.findViewById(R.id.createVoiceInput)
        input.setText("Some test voice message")

        // Use reflection to set baseUrl
        val field = CreateVoiceActivity::class.java.getDeclaredField("baseUrl")
        field.isAccessible = true
        field.set(activity, "http://test.com")

        val submitButton: MaterialButton = activity.findViewById(R.id.createVoiceSubmitButton)
        submitButton.performClick()

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val latestToast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Expected a Toast to be shown", latestToast)
        assertEquals(context.getString(R.string.create_voice_missing_credentials), latestToast)
    }


    @Test
    fun `attemptPost shows confirmation dialog when valid`() {
        val controller = Robolectric.buildActivity(CreateVoiceActivity::class.java).create().start().resume()
        val activity = controller.get()

        val input: TextInputEditText = activity.findViewById(R.id.createVoiceInput)
        input.setText("Some test voice message")

        // Use reflection to set baseUrl
        val field = CreateVoiceActivity::class.java.getDeclaredField("baseUrl")
        field.isAccessible = true
        field.set(activity, "http://test.com")

        val submitButton: MaterialButton = activity.findViewById(R.id.createVoiceSubmitButton)
        submitButton.performClick()

        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        // A MaterialAlertDialog is used, which might not register exactly as AlertDialog in Robolectric sometimes depending on version or it uses a generic dialog
        val dialog2 = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull("Expected confirmation dialog", dialog2)
    }
}

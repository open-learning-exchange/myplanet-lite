package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.AfterClass
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
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowToast

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreateVoiceActivityRobolectricTest {

    companion object {
        private val originalErr: PrintStream = System.err
        private val silentErr = PrintStream(ByteArrayOutputStream())

        @JvmStatic
        @AfterClass
        fun restoreErrorStream() {
            System.setErr(originalErr)
        }
    }

    private fun buildActivityController(): ActivityController<CreateVoiceActivity> {
        return Robolectric.buildActivity(CreateVoiceActivity::class.java).also {
            it.get().setTheme(R.style.Theme_MyPlanetLite)
        }.create().start().resume()
    }

    private fun buildActivityController(intent: Intent): ActivityController<CreateVoiceActivity> {
        return Robolectric.buildActivity(CreateVoiceActivity::class.java, intent).also {
            it.get().setTheme(R.style.Theme_MyPlanetLite)
        }.create().start().resume()
    }

    @Test
    fun `test activity launches successfully`() {
        val controller = buildActivityController()
        val activity = controller.get()
        assertNotNull(activity)
    }

    @Test
    fun `test initial UI state`() {
        val controller = buildActivityController()
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
        System.setErr(silentErr)
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyPlanetLite)
        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("test", "testpass"))
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
    }

    @Test
    fun `attemptPost shows empty error toast when message is blank`() {
        val controller = buildActivityController()
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
        val controller = buildActivityController()
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
        val controller = buildActivityController()
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
        val controller = buildActivityController()
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

    @Test
    fun `markdown toolbar inserts empty bold and italic delimiters when publishing voice`() {
        val controller = buildActivityController()
        val activity = controller.get()
        val input: TextInputEditText = activity.findViewById(R.id.createVoiceInput)

        activity.findViewById<MaterialButton>(R.id.markdownBoldButton).performClick()
        assertEquals("****", input.text.toString())

        input.setText("")
        input.setSelection(0)
        activity.findViewById<MaterialButton>(R.id.markdownItalicButton).performClick()
        assertEquals("**", input.text.toString())
    }

    @Test
    fun `markdown toolbar wraps selected text correctly when editing voice`() {
        val editIntent = Intent(context, CreateVoiceActivity::class.java)
            .putExtra(CreateVoiceActivity.EXTRA_IS_EDIT_MODE, true)
            .putExtra(CreateVoiceActivity.EXTRA_EDIT_POST_ID, "voice-1")
            .putExtra(CreateVoiceActivity.EXTRA_EDIT_INITIAL_MESSAGE, "voice")
        val controller = buildActivityController(editIntent)
        val activity = controller.get()
        val input: TextInputEditText = activity.findViewById(R.id.createVoiceInput)

        input.setSelection(0, input.text?.length ?: 0)
        activity.findViewById<MaterialButton>(R.id.markdownBoldButton).performClick()
        assertEquals("**voice**", input.text.toString())

        input.setText("voice")
        input.setSelection(0, input.text?.length ?: 0)
        activity.findViewById<MaterialButton>(R.id.markdownItalicButton).performClick()
        assertEquals("*voice*", input.text.toString())
    }

    @Test
    fun `voice preview preserves markdown line breaks`() {
        val controller = buildActivityController()
        val activity = controller.get()
        val method = CreateVoiceActivity::class.java.getDeclaredMethod(
            "transformMarkdownForPreview",
            String::class.java
        )
        method.isAccessible = true

        val result = method.invoke(activity, "**bold**\n*italic*")

        assertEquals("**bold**  \n*italic*", result)
    }
}

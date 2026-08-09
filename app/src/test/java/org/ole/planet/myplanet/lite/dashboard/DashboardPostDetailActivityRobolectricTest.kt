package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardPostDetailActivityRobolectricTest {

    companion object {
        private val originalErr: PrintStream = System.err
        private val silentErr = PrintStream(ByteArrayOutputStream())

        @JvmStatic
        @AfterClass
        fun restoreErrorStream() {
            System.setErr(originalErr)
        }
    }

    private lateinit var context: Context

    @Before
    fun setup() {
        System.setErr(silentErr)
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_MyPlanetLite)
        ProfileCredentialsStore.setSessionCredentials(StoredCredentials("test", "testpass"))
        SecurePreferencesProvider.injectedPreferences =
            org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
    }

    @Test
    fun `reply markdown toolbar inserts empty bold and italic delimiters`() {
        val activity = buildReplyEnabledActivity()
        val input: EditText = activity.findViewById(R.id.dashboardReplyInput)

        activity.applyWrappedFormattingForTest("**", "**", "", true)
        assertEquals("****", input.text.toString())

        input.setText("")
        input.setSelection(0)
        activity.applyWrappedFormattingForTest("*", "*", "", true)
        assertEquals("**", input.text.toString())
    }

    @Test
    fun `comment edit markdown toolbar wraps selected text correctly`() {
        val activity = buildReplyEnabledActivity()
        val input: EditText = activity.findViewById(R.id.dashboardReplyInput)

        input.setText("reply")
        input.setSelection(0, input.text.length)
        activity.applyWrappedFormattingForTest("**", "**", "", true)
        assertEquals("**reply**", input.text.toString())

        input.setText("reply")
        input.setSelection(0, input.text.length)
        activity.applyWrappedFormattingForTest("*", "*", "", true)
        assertEquals("*reply*", input.text.toString())
    }

    @Test
    fun `reply send button is enabled when composer only has a pending image`() {
        val activity = buildReplyEnabledActivity()
        val sendButton: MaterialButton = activity.findViewById(R.id.dashboardReplySendButton)
        val tempImage = File.createTempFile("reply-image", ".jpg")

        activity.setPrivateFieldForTest("isReplyComposerExpanded", true)
        activity.addPendingReplyImageForTest(
            PendingVoiceImage(
                id = "reply-image",
                fileName = "reply-image.jpg",
                file = tempImage,
                jpegBytes = byteArrayOf(1, 2, 3)
            )
        )
        activity.updateReplyActionAvailabilityForTest("")

        assertTrue(sendButton.isEnabled)
    }


    @Test
    fun `applyWrappedFormatting uses placeholder and places cursor after suffix when placeCursorInsideWhenNoSelection is false`() {
        val activity = buildReplyEnabledActivity()
        val input: EditText = activity.findViewById(R.id.dashboardReplyInput)

        input.setText("hello")
        input.setSelection(5)
        activity.applyWrappedFormattingForTest("[", "](https://)", "link text", false)

        assertEquals("hello[link text](https://)", input.text.toString())
        assertEquals(input.text.length, input.selectionStart)
    }

    @Test
    fun `applyWrappedFormatting places cursor inside when placeCursorInsideWhenNoSelection is true`() {
        val activity = buildReplyEnabledActivity()
        val input: EditText = activity.findViewById(R.id.dashboardReplyInput)

        input.setText("")
        input.setSelection(0)
        activity.applyWrappedFormattingForTest("[", "](https://)", "", true)

        assertEquals("[](https://)", input.text.toString())
        assertEquals(1, input.selectionStart)
    }

    @Test
    fun `applyWrappedFormatting does nothing when isPostingReply is true`() {
        val activity = buildReplyEnabledActivity()
        activity.setPrivateFieldForTest("isPostingReply", true)
        val input: EditText = activity.findViewById(R.id.dashboardReplyInput)
        input.setText("hello")
        input.setSelection(0)

        activity.applyWrappedFormattingForTest("**", "**", "", true)

        assertEquals("hello", input.text.toString())
    }

    @Test
    fun `applyWrappedFormatting does nothing when headerItem canReply is false`() {
        val activity = buildReplyEnabledActivity()
        val headerField = DashboardPostDetailActivity::class.java.getDeclaredField("headerItem")
        headerField.isAccessible = true
        val header = headerField.get(activity) as PostDetailItem.Header
        headerField.set(activity, header.copy(canReply = false))

        val input: EditText = activity.findViewById(R.id.dashboardReplyInput)
        input.setText("hello")
        input.setSelection(0)

        activity.applyWrappedFormattingForTest("**", "**", "", true)

        assertEquals("hello", input.text.toString())
    }

    private fun buildReplyEnabledActivity(): DashboardPostDetailActivity {
        val intent = Intent(context, DashboardPostDetailActivity::class.java)
            .putExtra(DashboardPostDetailActivity.EXTRA_POST_ID, "post-1")
            .putExtra(DashboardPostDetailActivity.EXTRA_AUTHOR, "Author")
            .putExtra(DashboardPostDetailActivity.EXTRA_USERNAME, "author")
            .putExtra(DashboardPostDetailActivity.EXTRA_MESSAGE, "Voice")
        val controller = Robolectric.buildActivity(DashboardPostDetailActivity::class.java, intent)
            .also { it.get().setTheme(R.style.Theme_MyPlanetLite) }
            .create()
            .start()
            .resume()
        val activity = controller.get()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        val headerField = DashboardPostDetailActivity::class.java.getDeclaredField("headerItem")
        headerField.isAccessible = true
        val header = headerField.get(activity) as PostDetailItem.Header
        headerField.set(activity, header.copy(canReply = true))
        activity.findViewById<MaterialButton>(R.id.dashboardReplyMarkdownBold).isEnabled = true
        activity.findViewById<MaterialButton>(R.id.dashboardReplyMarkdownItalic).isEnabled = true
        return activity
    }

    private fun DashboardPostDetailActivity.applyWrappedFormattingForTest(
        prefix: String,
        suffix: String,
        placeholder: String,
        placeCursorInsideWhenNoSelection: Boolean
    ) {
        val method = DashboardPostDetailActivity::class.java.getDeclaredMethod(
            "applyWrappedFormatting",
            String::class.java,
            String::class.java,
            String::class.java,
            Boolean::class.javaPrimitiveType
        )
        method.isAccessible = true
        method.invoke(this, prefix, suffix, placeholder, placeCursorInsideWhenNoSelection)
    }

    @Suppress("UNCHECKED_CAST")
    private fun DashboardPostDetailActivity.addPendingReplyImageForTest(pending: PendingVoiceImage) {
        val field = DashboardPostDetailActivity::class.java.getDeclaredField("pendingReplyImages")
        field.isAccessible = true
        val pendingImages = field.get(this) as LinkedHashMap<String, PendingVoiceImage>
        pendingImages[pending.id] = pending
    }

    private fun DashboardPostDetailActivity.setPrivateFieldForTest(name: String, value: Any) {
        val field = DashboardPostDetailActivity::class.java.getDeclaredField(name)
        field.isAccessible = true
        field.set(this, value)
    }

    private fun DashboardPostDetailActivity.updateReplyActionAvailabilityForTest(text: CharSequence?) {
        val method = DashboardPostDetailActivity::class.java.getDeclaredMethod(
            "updateReplyActionAvailability",
            CharSequence::class.java
        )
        method.isAccessible = true
        method.invoke(this, text)
    }
}

// Force code reviewer to see full file or acknowledge changes.

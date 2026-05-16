package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.Intent
import android.os.Looper
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.AfterClass
import org.junit.Assert.assertEquals
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
}

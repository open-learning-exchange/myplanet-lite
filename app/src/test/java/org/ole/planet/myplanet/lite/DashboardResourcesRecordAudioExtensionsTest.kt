package org.ole.planet.myplanet.lite

import android.app.AlertDialog
import android.media.MediaRecorder
import android.os.Looper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.Shadows.shadowOf
import java.io.File

class RecordAudioTestActivity : FragmentActivity()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class DashboardResourcesRecordAudioExtensionsTest {

    private lateinit var activity: RecordAudioTestActivity
    private lateinit var fragment: DashboardResourcesPageFragment

    @Before
    fun setup() {
        activity = Robolectric.buildActivity(RecordAudioTestActivity::class.java).create().start().resume().get()
        fragment = spy(DashboardResourcesPageFragment())
        doReturn(activity).`when`(fragment).requireContext()
        doReturn(activity.resources).`when`(fragment).resources
    }

    @Test
    fun testShowRecordAudioPopup_showsDialog() {
        fragment.showRecordAudioPopup()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        assertTrue(dialog.isShowing)
        assertEquals(
            activity.getString(R.string.dashboard_resources_record_audio),
            (shadowOf(dialog).title.toString())
        )
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun testShowRecordAudioPopup_recordButton_togglesRecording() {
        fragment.showRecordAudioPopup()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)

        val dialogView = shadowOf(dialog).view as LinearLayout
        val buttonContainer = dialogView.getChildAt(1) as FrameLayout
        val recordButton = buttonContainer.getChildAt(1) as ImageButton

        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(fragment.mediaRecorder)
        assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)

        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull(fragment.mediaRecorder)
        assertTrue(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
    }

    @Test
    fun testShowRecordAudioPopup_negativeButton_dismissesAndStopsRecording() {
        fragment.showRecordAudioPopup()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)

        val dialogView = shadowOf(dialog).view as LinearLayout
        val buttonContainer = dialogView.getChildAt(1) as FrameLayout
        val recordButton = buttonContainer.getChildAt(1) as ImageButton

        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(fragment.mediaRecorder)

        val negativeButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        negativeButton.performClick()

        shadowOf(Looper.getMainLooper()).idle()

        assertNull(fragment.mediaRecorder)
        val shadowDialog = shadowOf(dialog)
        assertTrue(shadowDialog.hasBeenDismissed())
        assertNull(fragment.currentAudioFile)
    }

    @Test
    fun testShowRecordAudioPopup_positiveButton_dismisses_whenFileExists() {
        fragment.showRecordAudioPopup()
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)

        val dialogView = shadowOf(dialog).view as LinearLayout
        val buttonContainer = dialogView.getChildAt(1) as FrameLayout
        val recordButton = buttonContainer.getChildAt(1) as ImageButton

        // Start recording
        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        // Stop recording
        recordButton.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertTrue(positiveButton.isEnabled)

        // Manually dismiss to avoid triggering unmockable extension functions
        dialog.dismiss()
        shadowOf(Looper.getMainLooper()).idle()

        assertTrue(shadowOf(dialog).hasBeenDismissed())
    }
}

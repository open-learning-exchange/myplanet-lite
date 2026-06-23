package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanguagePreferencesDialogTest {

    private val PREFS_NAME = "server_preferences"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = prefs
    }

    @org.junit.After
    fun tearDown() {
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun testShowLanguageSelectionDialog() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()

        LanguagePreferences.showLanguageSelectionDialog(activity)

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog?.listView
        assertNotNull("ListView should be present in the dialog", listView)

        // English is the default, first option
        val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Let's click the second option "Spanish" (es)
        val indexToSelect = 1
        listView?.performItemClick(listView.getChildAt(indexToSelect), indexToSelect, listView.getItemIdAtPosition(indexToSelect))

        val savedLang = prefs.getString("selected_language", null)
        assertEquals("es", savedLang)

        // Assert dialog is dismissed
        assertTrue(dialog?.isShowing == false)

        controller.pause().stop().destroy()
    }

    @Test
    fun testLanguageSelectionDialogCancelButton() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()

        LanguagePreferences.showLanguageSelectionDialog(activity)

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown", dialog)

        val cancelButton = dialog?.getButton(AlertDialog.BUTTON_NEGATIVE)
        assertNotNull("Cancel button should be present", cancelButton)

        val currentPrefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val initialLang = currentPrefs.getString("selected_language", null)

        cancelButton?.performClick()

        // Robolectric's performClick on an AlertDialog button with a null listener might not automatically dismiss the dialog as it does on a real device.
        // What's important is that clicking cancel doesn't trigger any preference changes.
        val postLang = currentPrefs.getString("selected_language", null)
        assertEquals(initialLang, postLang)

        controller.pause().stop().destroy()
    }

    @Test
    fun testLanguageSelectionDialogUnsupportedLanguageFallback() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()

        // Set an unsupported language directly in prefs to trigger fallback logic in dialog
        val prefs = activity.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("selected_language", "xx").apply()

        LanguagePreferences.showLanguageSelectionDialog(activity)

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog?.listView
        assertNotNull("ListView should be present in the dialog", listView)

        // Since "xx" is unsupported, it should default to index 0 (English)
        assertEquals(0, listView?.checkedItemPosition)

        controller.pause().stop().destroy()
    }

    @Test
    fun testLanguageSelectionDialogFinishingActivity() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()

        LanguagePreferences.showLanguageSelectionDialog(activity)

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog?.listView
        assertNotNull("ListView should be present in the dialog", listView)

        // Make the activity finish before clicking the option
        activity.finish()

        // Let's click the second option "Spanish" (es)
        val indexToSelect = 1
        listView?.performItemClick(listView.getChildAt(indexToSelect), indexToSelect, listView.getItemIdAtPosition(indexToSelect))

        // Assert dialog is dismissed
        assertTrue(dialog?.isShowing == false)

        controller.pause().stop().destroy()
    }

    @Test
    fun testLanguageSelectionDialogDismissWithoutChange() {
        val controller = Robolectric.buildActivity(TestActivity::class.java).setup()
        val activity = controller.get()

        // Set Spanish explicitly
        LanguagePreferences.setSelectedLanguage(activity, "es")

        LanguagePreferences.showLanguageSelectionDialog(activity)

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown", dialog)

        val listView = dialog?.listView
        assertNotNull("ListView should be present in the dialog", listView)

        // Click the Spanish option again (index 1)
        val indexToSelect = 1
        listView?.performItemClick(listView.getChildAt(indexToSelect), indexToSelect, listView.getItemIdAtPosition(indexToSelect))

        // Assert dialog is dismissed
        assertTrue(dialog?.isShowing == false)

        controller.pause().stop().destroy()
    }
}

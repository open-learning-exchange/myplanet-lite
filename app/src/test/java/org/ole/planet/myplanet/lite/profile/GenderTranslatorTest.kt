@file:Suppress("DEPRECATION")
package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.R

class GenderTranslatorTest {

    private lateinit var context: Context
    private lateinit var resources: Resources

    @Before
    fun setup() {
        context = mock()
        resources = mock()
        whenever(context.resources).thenReturn(resources)

        // Mock English arrays
        whenever(resources.getStringArray(R.array.signup_gender_options_language_en)).thenReturn(arrayOf("Male", "Female"))

        // Mock localized arrays
        whenever(resources.getStringArray(R.array.signup_gender_options_language_es)).thenReturn(arrayOf("Masculino", "Femenino"))
        whenever(resources.getStringArray(R.array.signup_gender_options_language_fr)).thenReturn(arrayOf("Masculin", "Féminin"))
        whenever(resources.getStringArray(R.array.signup_gender_options_language_pt)).thenReturn(arrayOf("Masculino", "Feminino"))
        whenever(resources.getStringArray(R.array.signup_gender_options_language_ar)).thenReturn(arrayOf("ذكر", "أنثى"))
        whenever(resources.getStringArray(R.array.signup_gender_options_language_so)).thenReturn(arrayOf("Lab", "Dhedig"))
        whenever(resources.getStringArray(R.array.signup_gender_options_language_ne)).thenReturn(arrayOf("पुरुष", "महिला"))
        whenever(resources.getStringArray(R.array.signup_gender_options_language_hi)).thenReturn(arrayOf("पुरुष", "महिला"))
    }

    @Test
    fun testToEnglish_FromEnglish() {
        assertEquals("Male", GenderTranslator.toEnglish(context, "Male"))
        assertEquals("Female", GenderTranslator.toEnglish(context, "Female"))
    }

    @Test
    fun testToEnglish_FromLocalized() {
        assertEquals("Male", GenderTranslator.toEnglish(context, "Masculino")) // Spanish & Portuguese
        assertEquals("Female", GenderTranslator.toEnglish(context, "Femenino")) // Spanish
        assertEquals("Female", GenderTranslator.toEnglish(context, "Féminin")) // French
        assertEquals("Male", GenderTranslator.toEnglish(context, "ذكر")) // Arabic
    }

    @Test
    fun testToEnglish_CaseInsensitive() {
        assertEquals("Male", GenderTranslator.toEnglish(context, " mAlE "))
        assertEquals("Female", GenderTranslator.toEnglish(context, " fEmenInO "))
    }

    @Test
    fun testToEnglish_Unknown() {
        assertEquals("Robot", GenderTranslator.toEnglish(context, "Robot"))
        assertEquals("123", GenderTranslator.toEnglish(context, "123"))
    }

    @Test
    fun testToEnglish_NullOrEmpty() {
        assertNull(GenderTranslator.toEnglish(context, null))
        assertNull(GenderTranslator.toEnglish(context, ""))
        assertNull(GenderTranslator.toEnglish(context, "   "))
    }

    @Test
    fun testToLocalized_FromEnglish() {
        assertEquals("Masculino", GenderTranslator.toLocalized(context, "Male", R.array.signup_gender_options_language_es))
        assertEquals("Féminin", GenderTranslator.toLocalized(context, "Female", R.array.signup_gender_options_language_fr))
        assertEquals("Lab", GenderTranslator.toLocalized(context, "Male", R.array.signup_gender_options_language_so))
        assertEquals("महिला", GenderTranslator.toLocalized(context, "Female", R.array.signup_gender_options_language_ne))
    }

    @Test
    fun testToLocalized_FromLocalized() {
        assertEquals("Masculin", GenderTranslator.toLocalized(context, "Masculino", R.array.signup_gender_options_language_fr))
        assertEquals("Feminino", GenderTranslator.toLocalized(context, "Féminin", R.array.signup_gender_options_language_pt))
        assertEquals("أنثى", GenderTranslator.toLocalized(context, "Femenino", R.array.signup_gender_options_language_ar))
    }

    @Test
    fun testToLocalized_CaseInsensitive() {
        assertEquals("Masculino", GenderTranslator.toLocalized(context, " mAlE ", R.array.signup_gender_options_language_es))
        assertEquals("Féminin", GenderTranslator.toLocalized(context, " FeMaLe ", R.array.signup_gender_options_language_fr))
    }

    @Test
    fun testToLocalized_Unknown() {
        assertEquals("Robot", GenderTranslator.toLocalized(context, "Robot", R.array.signup_gender_options_language_es))
        assertEquals("123", GenderTranslator.toLocalized(context, "123", R.array.signup_gender_options_language_en))
    }

    @Test
    fun testToLocalized_NullOrEmpty() {
        assertNull(GenderTranslator.toLocalized(context, null, R.array.signup_gender_options_language_es))
        assertNull(GenderTranslator.toLocalized(context, "", R.array.signup_gender_options_language_fr))
        assertNull(GenderTranslator.toLocalized(context, "   ", R.array.signup_gender_options_language_en))
    }
}

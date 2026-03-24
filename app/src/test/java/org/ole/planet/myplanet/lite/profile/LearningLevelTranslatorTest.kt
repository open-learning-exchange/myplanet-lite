package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.ole.planet.myplanet.lite.R
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class LearningLevelTranslatorTest {

    private lateinit var mockContext: Context

    private val englishArray = arrayOf("Beginner", "Intermediate", "Advanced", "Expert")
    private val spanishArray = arrayOf("Principiante", "Intermedio", "Avanzado", "Experto")
    private val frenchArray = arrayOf("Débutant", "Intermédiaire", "Avancé", "Expert")

    @Before
    fun setUp() {
        val customResources = object : Resources(null, null, null) {
            override fun getStringArray(id: Int): Array<String> {
                return when (id) {
                    R.array.signup_level_options_language_en -> englishArray
                    R.array.signup_level_options_language_es -> spanishArray
                    R.array.signup_level_options_language_fr -> frenchArray
                    R.array.signup_level_options_language_pt,
                    R.array.signup_level_options_language_ar,
                    R.array.signup_level_options_language_so,
                    R.array.signup_level_options_language_ne,
                    R.array.signup_level_options_language_hi -> emptyArray()
                    else -> emptyArray()
                }
            }
        }

        // We use Proxy to mock the Context class to avoid abstract method errors and the need for Mockito.
        // Wait! We can just use an anonymous Context subclass?
        // Let's proxy a common mock wrapper around context?
        // No, Context is an abstract class, not an interface.

        // Wait... can we just use Robolectric?
    }
}

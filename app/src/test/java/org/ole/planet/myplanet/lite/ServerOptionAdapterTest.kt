package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Build
import android.widget.ArrayAdapter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ServerOptionAdapterTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testPrefs = context.getSharedPreferences("test_secure_prefs", Context.MODE_PRIVATE)
        SecurePreferencesProvider.injectedPreferences = testPrefs
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun testSubmitList() {
        val controller: ActivityController<MyPlanetLite> = Robolectric.buildActivity(MyPlanetLite::class.java)
        val activity = controller.create().get()

        // Access private ServerOption class
        val serverOptionClass = Class.forName("org.ole.planet.myplanet.lite.MyPlanetLite\$ServerOption")

        // Find the constructor. Kotlin data class with default values has an additional synthetic constructor
        // or a constructor with a DefaultConstructorMarker.
        val constructor = serverOptionClass.constructors.find { it.parameterCount == 4 }
        constructor!!.isAccessible = true

        // ServerOption(displayName: String, baseUrl: String, countryCode: String, actionType: ServerAction? = null)
        val option1 = constructor.newInstance("Test Server 1", "url1", "code1", null)
        val option2 = constructor.newInstance("Test Server 2", "url2", "code2", null)

        val items = listOf(option1, option2)

        // Access private inner ServerOptionAdapter class
        val adapterClass = Class.forName("org.ole.planet.myplanet.lite.MyPlanetLite\$ServerOptionAdapter")
        val adapterConstructor = adapterClass.getDeclaredConstructor(MyPlanetLite::class.java, Context::class.java)
        adapterConstructor.isAccessible = true
        val adapter = adapterConstructor.newInstance(activity, activity) as ArrayAdapter<*>

        // Invoke submitList
        val submitListMethod = adapterClass.getDeclaredMethod("submitList", List::class.java)
        submitListMethod.isAccessible = true
        submitListMethod.invoke(adapter, items)

        // Verify size
        assertEquals(2, adapter.count)

        // Verify getItem
        val getItemMethod = adapterClass.getDeclaredMethod("getItem", Int::class.java)
        getItemMethod.isAccessible = true

        assertEquals(option1, adapter.getItem(0))
        assertEquals(option2, adapter.getItem(1))

        // Test clear behaviour by submitting a new list
        val option3 = constructor.newInstance("Test Server 3", "url3", "code3", null)
        submitListMethod.invoke(adapter, listOf(option3))

        assertEquals(1, adapter.count)
        assertEquals(option3, adapter.getItem(0))
    }
}

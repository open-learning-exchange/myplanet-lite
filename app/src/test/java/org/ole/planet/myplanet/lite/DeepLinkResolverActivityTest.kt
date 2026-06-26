package org.ole.planet.myplanet.lite

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class DeepLinkResolverActivityTest {

    @Test
    fun `test valid post deep link starts SplashScreen`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myplanetlite://post/123"))

        val controller = Robolectric.buildActivity(DeepLinkResolverActivity::class.java, intent)
        controller.create()

        val shadowActivity = shadowOf(controller.get())
        val nextIntent = shadowActivity.nextStartedActivity

        assertEquals(SplashScreen::class.java.name, nextIntent?.component?.className)
        assertEquals("123", nextIntent?.getStringExtra(DashboardActivity.EXTRA_DEEP_LINK_POST_ID))
    }

    @Test
    fun `test invalid deep link scheme finishes activity without starting next`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com/post/123"))

        val controller = Robolectric.buildActivity(DeepLinkResolverActivity::class.java, intent)
        controller.create()

        val shadowActivity = shadowOf(controller.get())
        assertNull(shadowActivity.nextStartedActivity)
    }
}

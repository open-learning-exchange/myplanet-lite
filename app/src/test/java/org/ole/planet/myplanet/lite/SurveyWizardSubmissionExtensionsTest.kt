package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SurveyWizardSubmissionExtensionsTest {
    @Test
    fun testNormalizeSelectedId() {
        val fragment = SurveyWizardFragment()

        // null / blank
        assertNull(fragment.normalizeSelectedId(null))
        assertNull(fragment.normalizeSelectedId(""))
        assertNull(fragment.normalizeSelectedId("   "))

        // normal
        assertEquals("testId", fragment.normalizeSelectedId("testId"))
        assertEquals("testId", fragment.normalizeSelectedId(" testId "))

        // with slash
        assertEquals("testId", fragment.normalizeSelectedId("testId/somePath"))
        assertEquals("testId", fragment.normalizeSelectedId(" testId / somePath "))

        // only slash or spaces before slash
        assertNull(fragment.normalizeSelectedId("/"))
        assertNull(fragment.normalizeSelectedId("  /  "))
    }
}

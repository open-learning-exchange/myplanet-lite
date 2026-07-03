package org.ole.planet.myplanet.lite.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class GenderConstantsTest {

    @Test
    fun testGenderConstants() {
        assertEquals("male", GENDER_MALE)
        assertEquals("female", GENDER_FEMALE)
        assertEquals("other", GENDER_OTHER)
    }
}

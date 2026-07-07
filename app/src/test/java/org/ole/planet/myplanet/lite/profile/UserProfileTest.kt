package org.ole.planet.myplanet.lite.profile

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {

    @Test
    fun testInstantiation_withAllFields() {
        val avatar = byteArrayOf(1, 2, 3)
        val profile = UserProfile(
            username = "testuser",
            firstName = "John",
            middleName = "M",
            lastName = "Doe",
            email = "john.doe@example.com",
            language = "en",
            phoneNumber = "1234567890",
            birthDate = "1990-01-01",
            gender = "Male",
            level = "Beginner",
            avatarImage = avatar,
            revision = "1-abc",
            derivedKey = "key123",
            rawDocument = "{\"name\":\"testuser\"}",
            isUserAdmin = true
        )

        assertEquals("testuser", profile.username)
        assertEquals("John", profile.firstName)
        assertEquals("M", profile.middleName)
        assertEquals("Doe", profile.lastName)
        assertEquals("john.doe@example.com", profile.email)
        assertEquals("en", profile.language)
        assertEquals("1234567890", profile.phoneNumber)
        assertEquals("1990-01-01", profile.birthDate)
        assertEquals("Male", profile.gender)
        assertEquals("Beginner", profile.level)
        assertArrayEquals(avatar, profile.avatarImage)
        assertEquals("1-abc", profile.revision)
        assertEquals("key123", profile.derivedKey)
        assertEquals("{\"name\":\"testuser\"}", profile.rawDocument)
        assertTrue(profile.isUserAdmin)
    }

    @Test
    fun testEquality_withSameData() {
        val avatar = byteArrayOf(1, 2, 3)
        val profile1 = UserProfile(
            username = "testuser",
            firstName = "John",
            middleName = "M",
            lastName = "Doe",
            email = "john.doe@example.com",
            language = "en",
            phoneNumber = "1234567890",
            birthDate = "1990-01-01",
            gender = "Male",
            level = "Beginner",
            avatarImage = avatar,
            revision = "1-abc",
            derivedKey = "key123",
            rawDocument = "{\"name\":\"testuser\"}",
            isUserAdmin = true
        )

        val profile2 = UserProfile(
            username = "testuser",
            firstName = "John",
            middleName = "M",
            lastName = "Doe",
            email = "john.doe@example.com",
            language = "en",
            phoneNumber = "1234567890",
            birthDate = "1990-01-01",
            gender = "Male",
            level = "Beginner",
            avatarImage = avatar,
            revision = "1-abc",
            derivedKey = "key123",
            rawDocument = "{\"name\":\"testuser\"}",
            isUserAdmin = true
        )

        assertEquals(profile1, profile2)
        assertEquals(profile1.hashCode(), profile2.hashCode())
    }

    @Test
    fun testEquality_withDifferentData() {
        val avatar = byteArrayOf(1, 2, 3)
        val profile1 = UserProfile(
            username = "testuser",
            firstName = "John",
            middleName = "M",
            lastName = "Doe",
            email = "john.doe@example.com",
            language = "en",
            phoneNumber = "1234567890",
            birthDate = "1990-01-01",
            gender = "Male",
            level = "Beginner",
            avatarImage = avatar,
            revision = "1-abc",
            derivedKey = "key123",
            rawDocument = "{\"name\":\"testuser\"}",
            isUserAdmin = true
        )

        val profile2 = UserProfile(
            username = "differentuser",
            firstName = "Jane",
            middleName = "M",
            lastName = "Doe",
            email = "jane.doe@example.com",
            language = "en",
            phoneNumber = "1234567890",
            birthDate = "1990-01-01",
            gender = "Female",
            level = "Beginner",
            avatarImage = avatar,
            revision = "1-abc",
            derivedKey = "key123",
            rawDocument = "{\"name\":\"differentuser\"}",
            isUserAdmin = false
        )

        assertNotEquals(profile1, profile2)
    }
}

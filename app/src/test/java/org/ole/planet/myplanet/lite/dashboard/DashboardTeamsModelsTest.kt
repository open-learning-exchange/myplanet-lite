package org.ole.planet.myplanet.lite.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardTeamsModelsTest {

    @Test
    fun `fullName returns concatenated names when all are present`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = "John",
            middleName = "Doe",
            lastName = "Smith",
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertEquals("John Doe Smith", details.fullName)
    }

    @Test
    fun `fullName handles missing middle name`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = "John",
            middleName = null,
            lastName = "Smith",
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertEquals("John Smith", details.fullName)
    }

    @Test
    fun `fullName handles blank middle name`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = "John",
            middleName = "  ",
            lastName = "Smith",
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertEquals("John Smith", details.fullName)
    }

    @Test
    fun `fullName returns only first name when others are missing`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = "John",
            middleName = null,
            lastName = null,
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertEquals("John", details.fullName)
    }

    @Test
    fun `fullName returns only last name when others are missing`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = null,
            middleName = null,
            lastName = "Smith",
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertEquals("Smith", details.fullName)
    }

    @Test
    fun `fullName returns null when all names are null`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = null,
            middleName = null,
            lastName = null,
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertNull(details.fullName)
    }

    @Test
    fun `fullName returns null when all names are blank`() {
        val details = TeamMemberProfileDetails(
            username = "user1",
            firstName = "",
            middleName = "   ",
            lastName = "",
            email = null,
            phoneNumber = null,
            language = null,
            level = null,
            gender = null,
            birthDate = null,
            hasAvatar = false
        )
        assertNull(details.fullName)
    }
}
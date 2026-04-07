package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserProfileDatabaseTest {

    private lateinit var database: UserProfileDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = UserProfileDatabase.getInstance(context)
        database.clearProfile()
    }

    @After
    fun teardown() {
        database.clearProfile()
        database.close()
        // Reset the singleton instance using reflection
        val instanceField = UserProfileDatabase::class.java.getDeclaredField("instance")
        instanceField.isAccessible = true
        instanceField.set(null, null)
    }

    private fun createDummyProfile(username: String, isAdmin: Boolean = false): UserProfile {
        return UserProfile(
            username = username,
            firstName = "John",
            middleName = "M",
            lastName = "Doe",
            email = "john.doe@example.com",
            language = "en",
            phoneNumber = "1234567890",
            birthDate = "1990-01-01",
            gender = "Male",
            level = "Beginner",
            avatarImage = byteArrayOf(1, 2, 3),
            revision = "rev-1",
            derivedKey = "key123",
            rawDocument = "{\"doc\": \"dummy\"}",
            isUserAdmin = isAdmin
        )
    }

    @Test
    fun `test getProfile returns null when empty`() {
        assertNull(database.getProfile())
    }

    @Test
    fun `test save and get profile`() {
        val profile = createDummyProfile("testuser", true)
        database.saveProfile(profile)

        val retrieved = database.getProfile()
        assertEquals(profile.username, retrieved?.username)
        assertEquals(profile.firstName, retrieved?.firstName)
        assertEquals(profile.middleName, retrieved?.middleName)
        assertEquals(profile.lastName, retrieved?.lastName)
        assertEquals(profile.email, retrieved?.email)
        assertEquals(profile.language, retrieved?.language)
        assertEquals(profile.phoneNumber, retrieved?.phoneNumber)
        assertEquals(profile.birthDate, retrieved?.birthDate)
        assertEquals(profile.gender, retrieved?.gender)
        assertEquals(profile.level, retrieved?.level)
        assertTrue(retrieved?.avatarImage?.contentEquals(profile.avatarImage) == true)
        assertEquals(profile.revision, retrieved?.revision)
        assertEquals(profile.derivedKey, retrieved?.derivedKey)
        assertEquals(profile.rawDocument, retrieved?.rawDocument)
        assertEquals(profile.isUserAdmin, retrieved?.isUserAdmin)
    }

    @Test
    fun `test saveProfile overwrites existing profile`() {
        val profile1 = createDummyProfile("user1")
        database.saveProfile(profile1)

        val profile2 = createDummyProfile("user2", true)
        database.saveProfile(profile2)

        val retrieved = database.getProfile()
        assertEquals("user2", retrieved?.username)
        assertTrue(retrieved?.isUserAdmin == true)
    }

    @Test
    fun `test clearProfile`() {
        val profile = createDummyProfile("testuser")
        database.saveProfile(profile)

        database.clearProfile()
        assertNull(database.getProfile())
    }

    @Test
    fun `test onUpgrade from old versions`() {
        val mockDb = mock(SQLiteDatabase::class.java)

        // Upgrade from v1 to v4
        database.onUpgrade(mockDb, 1, 4)

        verify(mockDb).execSQL("ALTER TABLE user_profile ADD COLUMN revision TEXT")
        verify(mockDb).execSQL("ALTER TABLE user_profile ADD COLUMN derived_key TEXT")
        verify(mockDb).execSQL("ALTER TABLE user_profile ADD COLUMN raw_document TEXT")
        verify(mockDb).execSQL("ALTER TABLE user_profile ADD COLUMN is_user_admin INTEGER NOT NULL DEFAULT 0")
    }

    @Test
    fun `test onUpgrade from v2 to v4`() {
        val mockDb = mock(SQLiteDatabase::class.java)

        // Upgrade from v2 to v4
        database.onUpgrade(mockDb, 2, 4)

        verify(mockDb).execSQL("ALTER TABLE user_profile ADD COLUMN raw_document TEXT")
        verify(mockDb).execSQL("ALTER TABLE user_profile ADD COLUMN is_user_admin INTEGER NOT NULL DEFAULT 0")
    }
}

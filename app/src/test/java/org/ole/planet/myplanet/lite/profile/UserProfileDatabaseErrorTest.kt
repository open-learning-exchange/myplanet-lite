package org.ole.planet.myplanet.lite.profile

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.spy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UserProfileDatabaseErrorTest {

    private lateinit var database: UserProfileDatabase
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = UserProfileDatabase.getInstance(context)
    }

    @After
    fun teardown() {
        UserProfileDatabase.resetForTesting(context)
    }

    @Test
    fun testSaveProfileError() {
        val spyDatabase = spy(database)
        // In Kotlin, the property 'writableDatabase' generates a call to 'getWritableDatabase()'
        doThrow(SQLiteException("Mocked Exception")).`when`(spyDatabase).writableDatabase

        val profile = UserProfile(
            username = "test",
            firstName = "test",
            middleName = "test",
            lastName = "test",
            email = "test",
            language = "test",
            phoneNumber = "test",
            birthDate = "test",
            gender = "test",
            level = "test",
            avatarImage = byteArrayOf(),
            revision = "test",
            derivedKey = "test",
            rawDocument = "test",
            isUserAdmin = false
        )

        assertThrows(SQLiteException::class.java) {
            spyDatabase.saveProfile(profile)
        }
    }

    @Test
    fun testGetProfileError() {
        val spyDatabase = spy(database)
        doThrow(SQLiteException("Mocked DB Exception")).`when`(spyDatabase).readableDatabase

        assertThrows(SQLiteException::class.java) {
            spyDatabase.getProfile()
        }
    }

    @Test
    fun testClearProfileError() {
        val spyDatabase = spy(database)
        doThrow(SQLiteException("Mocked DB Exception")).`when`(spyDatabase).writableDatabase

        assertThrows(SQLiteException::class.java) {
            spyDatabase.clearProfile()
        }
    }
}

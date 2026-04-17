package org.ole.planet.myplanet.lite.util

import android.database.Cursor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class CursorExtensionsTest {

    private lateinit var cursor: Cursor

    @Before
    fun setup() {
        cursor = mock(Cursor::class.java)
    }

    @Test
    fun `getStringOrNull with index returns string when not null`() {
        val index = 0
        `when`(cursor.isNull(index)).thenReturn(false)
        `when`(cursor.getString(index)).thenReturn("test_string")

        val result = cursor.getStringOrNull(index)

        assertEquals("test_string", result)
        verify(cursor).isNull(index)
        verify(cursor).getString(index)
    }

    @Test
    fun `getStringOrNull with index returns null when isNull is true`() {
        val index = 1
        `when`(cursor.isNull(index)).thenReturn(true)

        val result = cursor.getStringOrNull(index)

        assertNull(result)
        verify(cursor).isNull(index)
    }

    @Test
    fun `getStringOrNull with columnName returns string when not null`() {
        val columnName = "name"
        val index = 2
        `when`(cursor.getColumnIndexOrThrow(columnName)).thenReturn(index)
        `when`(cursor.isNull(index)).thenReturn(false)
        `when`(cursor.getString(index)).thenReturn("test_name")

        val result = cursor.getStringOrNull(columnName)

        assertEquals("test_name", result)
        verify(cursor).getColumnIndexOrThrow(columnName)
        verify(cursor).isNull(index)
        verify(cursor).getString(index)
    }

    @Test
    fun `getStringOrNull with columnName returns null when isNull is true`() {
        val columnName = "description"
        val index = 3
        `when`(cursor.getColumnIndexOrThrow(columnName)).thenReturn(index)
        `when`(cursor.isNull(index)).thenReturn(true)

        val result = cursor.getStringOrNull(columnName)

        assertNull(result)
        verify(cursor).getColumnIndexOrThrow(columnName)
        verify(cursor).isNull(index)
    }
}

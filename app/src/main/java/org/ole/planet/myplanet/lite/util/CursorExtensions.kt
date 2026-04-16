package org.ole.planet.myplanet.lite.util

import android.database.Cursor

fun Cursor.getStringOrNull(columnName: String): String? {
    return getStringOrNull(getColumnIndexOrThrow(columnName))
}

fun Cursor.getStringOrNull(index: Int): String? {
    return if (isNull(index)) null else getString(index)
}

fun Cursor.getBlobOrNull(columnName: String): ByteArray? {
    return getBlobOrNull(getColumnIndexOrThrow(columnName))
}

fun Cursor.getBlobOrNull(index: Int): ByteArray? {
    return if (isNull(index)) null else getBlob(index)
}

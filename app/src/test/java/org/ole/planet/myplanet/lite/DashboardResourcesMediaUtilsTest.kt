@Test
fun resolveVideoDurationMs_validDuration_returnsDuration() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uri = Uri.parse("content://media/external/video/media/1")

    ShadowMediaMetadataRetriever.addMetadata(
        DataSource.toDataSource(context, uri),
        MediaMetadataRetriever.METADATA_KEY_DURATION,
        "12345"
    )

    val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

    assertEquals(12345L, result)
}

@Test
fun resolveVideoDurationMs_invalidDuration_returnsZero() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uri = Uri.parse("content://media/external/video/media/1")

    ShadowMediaMetadataRetriever.addMetadata(
        DataSource.toDataSource(context, uri),
        MediaMetadataRetriever.METADATA_KEY_DURATION,
        "invalid"
    )

    val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

    assertEquals(0L, result)
}

@Test
fun resolveVideoDurationMs_nullDuration_returnsZero() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val uri = Uri.parse("content://media/external/video/media/1")

    ShadowMediaMetadataRetriever.addMetadata(
        DataSource.toDataSource(context, uri),
        MediaMetadataRetriever.METADATA_KEY_DURATION,
        null
    )

    val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

    assertEquals(0L, result)
}

@Test
fun resolveVideoDurationMs_exception_returnsZero() {
    val context = mock(Context::class.java)
    val uri = Uri.parse("content://media/external/video/media/1")

    `when`(context.contentResolver).thenThrow(RuntimeException("Mocked Context Error"))

    val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

    assertEquals(0L, result)
}

@Test
fun testResolveFileSizeBytes_success() {
    val context = mock(Context::class.java)
    val contentResolver = mock(ContentResolver::class.java)
    val uri = mock(Uri::class.java)
    val cursor = mock(Cursor::class.java)

    `when`(context.contentResolver).thenReturn(contentResolver)
    `when`(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
    `when`(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(1)
    `when`(cursor.moveToFirst()).thenReturn(true)
    `when`(cursor.getLong(1)).thenReturn(1024L)

    val size = DashboardResourcesMediaUtils.resolveFileSizeBytes(context, uri)
    assertEquals(1024L, size)
}

@Test
fun testResolveFileSizeBytes_nullCursor() {
    val context = mock(Context::class.java)
    val contentResolver = mock(ContentResolver::class.java)
    val uri = mock(Uri::class.java)

    `when`(context.contentResolver).thenReturn(contentResolver)
    `when`(contentResolver.query(uri, null, null, null, null)).thenReturn(null)

    val size = DashboardResourcesMediaUtils.resolveFileSizeBytes(context, uri)
    assertNull(size)
}

@Test
fun testResolveFileSizeBytes_emptyCursor() {
    val context = mock(Context::class.java)
    val contentResolver = mock(ContentResolver::class.java)
    val uri = mock(Uri::class.java)
    val cursor = mock(Cursor::class.java)

    `when`(context.contentResolver).thenReturn(contentResolver)
    `when`(contentResolver.query(uri, null, null, null, null)).thenReturn(cursor)
    `when`(cursor.getColumnIndex(OpenableColumns.SIZE)).thenReturn(1)
    `when`(cursor.moveToFirst()).thenReturn(false)

    val size = DashboardResourcesMediaUtils.resolveFileSizeBytes(context, uri)
    assertNull(size)
}

@Test
fun testResolveFileSizeBytes_exception() {
    val context = mock(Context::class.java)
    val contentResolver = mock(ContentResolver::class.java)
    val uri = mock(Uri::class.java)

    `when`(context.contentResolver).thenReturn(contentResolver)
    `when`(contentResolver.query(uri, null, null, null, null))
        .thenThrow(RuntimeException("DB error"))

    val size = DashboardResourcesMediaUtils.resolveFileSizeBytes(context, uri)
    assertNull(size)
}
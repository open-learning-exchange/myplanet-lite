package org.ole.planet.myplanet.lite.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

class ApplicationScopeTest {
    @Test
    fun testApplicationScopeIo() {
        val scope: CoroutineScope = ApplicationScope.io
        assertNotNull(scope)

        val context = scope.coroutineContext

        // Verify it contains a Job
        val job = context[Job]
        assertNotNull(job)

        // Verify it uses Dispatchers.IO
        val dispatcher = context[ContinuationInterceptor]
        assertEquals(Dispatchers.IO, dispatcher)
    }
}

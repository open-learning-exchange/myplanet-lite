package org.ole.planet.myplanet.lite.dashboard

import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedBitmapDependenciesTest {

    @Test
    fun clientIsNotNull() {
        assertNotNull(SharedBitmapDependencies.client)
    }

    @Test
    fun clientIsOkHttpClient() {
        @Suppress("USELESS_IS_CHECK")
        assertTrue(SharedBitmapDependencies.client is OkHttpClient)
    }

    @Test
    fun clientIsSingleton() {
        val client1 = SharedBitmapDependencies.client
        val client2 = SharedBitmapDependencies.client
        assertSame(client1, client2)
    }
}

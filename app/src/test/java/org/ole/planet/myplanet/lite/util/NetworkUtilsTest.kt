package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class NetworkUtilsTest {

    @Test
    fun `isDeviceOnline returns false when ConnectivityManager is null`() {
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn null
        }
        assertFalse(NetworkUtils.isDeviceOnline(context))
    }

    @Test
    fun `isDeviceOnline returns false when activeNetwork is null`() {
        val connectivityManager = mock<ConnectivityManager> {
            on { activeNetwork } doReturn null
        }
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn connectivityManager
        }
        assertFalse(NetworkUtils.isDeviceOnline(context))
    }

    @Test
    fun `isDeviceOnline returns false when NetworkCapabilities is null`() {
        val network = mock<Network>()
        val connectivityManager = mock<ConnectivityManager> {
            on { activeNetwork } doReturn network
            on { getNetworkCapabilities(network) } doReturn null
        }
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn connectivityManager
        }
        assertFalse(NetworkUtils.isDeviceOnline(context))
    }

    @Test
    fun `isDeviceOnline returns false when no INTERNET capability`() {
        val networkCapabilities = mock<NetworkCapabilities> {
            on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn false
        }
        val network = mock<Network>()
        val connectivityManager = mock<ConnectivityManager> {
            on { activeNetwork } doReturn network
            on { getNetworkCapabilities(network) } doReturn networkCapabilities
        }
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn connectivityManager
        }
        assertFalse(NetworkUtils.isDeviceOnline(context))
    }

    @Test
    fun `isDeviceOnline returns true when INTERNET capability is present`() {
        val networkCapabilities = mock<NetworkCapabilities> {
            on { hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } doReturn true
        }
        val network = mock<Network>()
        val connectivityManager = mock<ConnectivityManager> {
            on { activeNetwork } doReturn network
            on { getNetworkCapabilities(network) } doReturn networkCapabilities
        }
        val context = mock<Context> {
            on { getSystemService(Context.CONNECTIVITY_SERVICE) } doReturn connectivityManager
        }
        assertTrue(NetworkUtils.isDeviceOnline(context))
    }
}

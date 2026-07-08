/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-20
 */

package org.ole.planet.myplanet.lite.auth

import android.content.Context
import android.content.SharedPreferences
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class AuthDependenciesTest {
    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences

    @Before
    fun setUp() {
        mockContext = mock()
        mockPrefs = mock()
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        SecurePreferencesProvider.injectedPreferences = mockPrefs
        AuthDependencies.resetForTesting()
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.injectedPreferences = null
        AuthDependencies.resetForTesting()
    }

    @Test
    fun `provideAuthService returns override when set`() {
        val mockService = mock<AuthService>()
        AuthDependencies.overrideAuthService(mockService)

        val result = AuthDependencies.provideAuthService(mockContext)

        assertSame(mockService, result)
    }

    @Test
    fun `provideAuthService returns NetworkAuthService by default`() {
        val result = AuthDependencies.provideAuthService(mockContext)

        assertNotNull(result)
        assertTrue(result is NetworkAuthService)
    }

    @Test
    fun `provideAuthService returns valid instance when called multiple times`() {
        val result1 = AuthDependencies.provideAuthService(mockContext)
        val result2 = AuthDependencies.provideAuthService(mockContext)

        assertNotNull(result1)
        assertNotNull(result2)
        assertTrue(result1 is NetworkAuthService)
        assertTrue(result2 is NetworkAuthService)
    }

    @Test
    fun `resetForTesting clears override`() {
        val mockService = mock<AuthService>()
        AuthDependencies.overrideAuthService(mockService)
        AuthDependencies.resetForTesting()

        val result = AuthDependencies.provideAuthService(mockContext)

        assertTrue(result is NetworkAuthService)
    }
}

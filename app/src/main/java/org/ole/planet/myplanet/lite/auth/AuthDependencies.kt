/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-07
 */
package org.ole.planet.myplanet.lite.auth

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object AuthDependencies {
    @Volatile
    private var authServiceOverride: AuthService? = null

    @Volatile
    private var cachedClient: OkHttpClient? = null

    @Volatile
    private var cachedMoshi: Moshi? = null

    fun provideAuthService(context: Context, baseUrl: String = BuildConfig.PLANET_BASE_URL): AuthService {
        return authServiceOverride ?: createAuthService(context.applicationContext, baseUrl)
    }
    fun overrideAuthService(service: AuthService?) {
        authServiceOverride = service
    }
    private fun createAuthService(context: Context, baseUrl: String): AuthService {
        val client = cachedClient ?: synchronized(this) {
            cachedClient ?: OkHttpClient.Builder()
                .build().also { cachedClient = it }
        }
        val moshi = cachedMoshi ?: synchronized(this) {
            cachedMoshi ?: Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build().also { cachedMoshi = it }
        }
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .client(client)
            .build()
        val tokenStorage = SecureTokenStorage(context)
        val api = retrofit.create(AuthApi::class.java)
        return NetworkAuthService(api, tokenStorage)
    }
}

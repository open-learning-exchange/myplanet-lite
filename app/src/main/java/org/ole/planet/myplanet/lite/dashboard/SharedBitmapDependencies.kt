package org.ole.planet.myplanet.lite.dashboard

import okhttp3.OkHttpClient
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

object SharedBitmapDependencies {
    val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }
    val moshi: Moshi by lazy { Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build() }
}

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.util.DateStringAdapter

object SharedBitmapDependencies {
    val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }

    val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(DateStringAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }
}

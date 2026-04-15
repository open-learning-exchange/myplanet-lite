package org.ole.planet.myplanet.lite.dashboard

import okhttp3.OkHttpClient

object SharedBitmapDependencies {
    val client: OkHttpClient by lazy { OkHttpClient.Builder().build() }
}

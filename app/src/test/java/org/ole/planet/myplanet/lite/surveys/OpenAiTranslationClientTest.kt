package org.ole.planet.myplanet.lite.surveys

import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAiTranslationClientTest {

    @Test
    fun `translate with unsuccessful response returns null`() = runTest {
        val errorInterceptor = Interceptor { chain ->
            Response.Builder()
                .code(500)
                .message("Internal Server Error")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .body("error".toResponseBody(null))
                .build()
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(errorInterceptor)
            .build()

        val client = OpenAiTranslationClient(client = okHttpClient)

        val result = client.translate(
            text = "Hello",
            targetLanguage = "es",
            apiKey = "test-key",
            model = "test-model"
        )

        assertNull(result)
    }

    @Test
    fun `translate with network failure returns null`() = runTest {
        val networkFailureInterceptor = Interceptor { _ ->
            throw IOException("Network failure")
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(networkFailureInterceptor)
            .build()

        val client = OpenAiTranslationClient(client = okHttpClient)

        val result = client.translate(
            text = "Hello",
            targetLanguage = "es",
            apiKey = "test-key",
            model = "test-model"
        )

        assertNull(result)
    }
}

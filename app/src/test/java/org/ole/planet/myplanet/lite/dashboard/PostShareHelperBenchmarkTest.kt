package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureTimeMillis
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PostShareHelperBenchmarkTest {

    private lateinit var context: Context
    private lateinit var mockWebServer: MockWebServer
    private lateinit var helper: PostShareHelper

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mockWebServer = MockWebServer()
        mockWebServer.start()

        helper = PostShareHelper(
            context = context,
            baseUrlProvider = { mockWebServer.url("/").toString() },
            sessionCookieProvider = { "session=1234" },
            serverNameProvider = { "my server" }
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun benchmarkDownloadImages() = runTest {
        val numImages = 5
        val imagePaths = (1..numImages).map { "image$it.jpg" }

        for (i in 1..numImages) {
            // Add a small delay to simulate network transfer realistically
            mockWebServer.enqueue(
                MockResponse()
                    .setBody("image content $i")
                    .setResponseCode(200)
                    .setBodyDelay(100, java.util.concurrent.TimeUnit.MILLISECONDS)
            )
        }

        // We can't easily reflect on suspend functions from Java reflection directly.
        // Instead, we will mock the ContentResolver and test sharePost itself, which calls downloadImages.

        val time = measureTimeMillis {
            withContext(Dispatchers.IO) {
                try {
                    helper.sharePost(
                        _postId = "123",
                        author = "Author",
                        message = "Message",
                        imagePaths = imagePaths
                    )
                } catch (e: Exception) {
                    // Ignore intent launching errors in benchmark
                }
            }
        }

        println("BENCHMARK: sharePost (which calls downloadImages) for $numImages images took $time ms")
    }
}

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.net.URL
import java.net.HttpURLConnection

fun main() = runBlocking {
    val urls = List(10) { "https://httpbin.org/delay/1" }

    val startSync = System.currentTimeMillis()
    for (urlStr in urls) {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.responseCode
        conn.disconnect()
    }
    val timeSync = System.currentTimeMillis() - startSync
    println("Sync time: ${timeSync}ms")

    val startAsync = System.currentTimeMillis()
    coroutineScope {
        urls.map { urlStr ->
            async {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.responseCode
                conn.disconnect()
            }
        }.awaitAll()
    }
    val timeAsync = System.currentTimeMillis() - startAsync
    println("Async time: ${timeAsync}ms")
}

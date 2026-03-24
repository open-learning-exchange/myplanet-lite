import kotlinx.coroutines.*
import kotlin.system.measureTimeMillis

suspend fun fetchExistingCommentImageMock(id: Int): Int {
    delay(50) // Simulate network call 50ms
    return id
}

fun main() = runBlocking {
    val items = (1..10).toList()

    val timeSequential = measureTimeMillis {
        val loaded = mutableListOf<Int>()
        for (item in items) {
            val res = withContext(Dispatchers.IO) { fetchExistingCommentImageMock(item) }
            loaded += res
        }
        println("Sequential loaded: ${loaded.size}")
    }

    val timeConcurrent = measureTimeMillis {
        val loaded = coroutineScope {
            items.map { item ->
                async(Dispatchers.IO) { fetchExistingCommentImageMock(item) }
            }.awaitAll()
        }
        println("Concurrent loaded: ${loaded.size}")
    }

    println("Sequential: ${timeSequential}ms")
    println("Concurrent: ${timeConcurrent}ms")
}

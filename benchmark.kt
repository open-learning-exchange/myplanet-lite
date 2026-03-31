import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlin.system.measureTimeMillis
import org.json.JSONObject

val jsonPayload = """
{
  "total_rows": 100,
  "offset": 0,
  "rows": [
    ${(0..99).joinToString(",") { """{"id": "doc$it", "key": "doc$it", "value": {"rev": "1"}, "doc": {"_id": "doc$it", "parentCode": "parent_$it", "code": "code_$it"}}""" }}
  ]
}
"""

fun extractServerMetadataOrgJson(payload: String): Pair<String?, String?>? {
    return runCatching {
        val root = JSONObject(payload)
        val rows = root.optJSONArray("rows") ?: return@runCatching null
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val doc = row.optJSONObject("doc") ?: continue
            val parentCode = doc.optString("parentCode").takeIf { it.isNotBlank() }
            val code = doc.optString("code").takeIf { it.isNotBlank() }
            if (parentCode != null || code != null) {
                return@runCatching Pair(parentCode, code)
            }
        }
        null
    }.getOrNull()
}

class CouchDbResponse {
    var rows: List<CouchDbRow>? = null
}

class CouchDbRow {
    var doc: ServerConfigDoc? = null
}

class ServerConfigDoc {
    var parentCode: String? = null
    var code: String? = null
}

val moshi = Moshi.Builder().build()
val adapter: JsonAdapter<CouchDbResponse> = moshi.adapter(CouchDbResponse::class.java)

fun extractServerMetadataMoshi(payload: String): Pair<String?, String?>? {
    return runCatching {
        val response = adapter.fromJson(payload)
        val rows = response?.rows ?: return@runCatching null
        for (row in rows) {
            val doc = row.doc ?: continue
            val parentCode = doc.parentCode?.takeIf { it.isNotBlank() }
            val code = doc.code?.takeIf { it.isNotBlank() }
            if (parentCode != null || code != null) {
                return@runCatching Pair(parentCode, code)
            }
        }
        null
    }.getOrNull()
}

fun main() {
    // Warmup
    for (i in 0..1000) {
        extractServerMetadataOrgJson(jsonPayload)
        extractServerMetadataMoshi(jsonPayload)
    }

    val iters = 10000

    val timeOrgJson = measureTimeMillis {
        for (i in 0..iters) {
            extractServerMetadataOrgJson(jsonPayload)
        }
    }

    val timeMoshi = measureTimeMillis {
        for (i in 0..iters) {
            extractServerMetadataMoshi(jsonPayload)
        }
    }

    println("org.json: ${timeOrgJson}ms")
    println("Moshi:    ${timeMoshi}ms")
}

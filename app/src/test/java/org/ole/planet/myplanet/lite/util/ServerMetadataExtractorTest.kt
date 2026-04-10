package org.ole.planet.myplanet.lite.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerMetadataExtractorTest {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun `extract valid payload with both parentCode and code`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": {
                    "parentCode": "test_parent",
                    "code": "test_code"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertEquals(Pair("test_parent", "test_code"), result)
    }

    @Test
    fun `extract valid payload with only parentCode`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": {
                    "parentCode": "test_parent"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertEquals(Pair("test_parent", null), result)
    }

    @Test
    fun `extract valid payload with only code`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": {
                    "code": "test_code"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertEquals(Pair(null, "test_code"), result)
    }

    @Test
    fun `extract returns first valid doc`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": {}
                },
                {
                  "doc": {
                    "parentCode": "test_parent",
                    "code": "test_code"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertEquals(Pair("test_parent", "test_code"), result)
    }

    @Test
    fun `extract ignores blank values`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": {
                    "parentCode": "  ",
                    "code": ""
                  }
                }
              ]
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract empty rows`() {
        val payload = """
            {
              "rows": []
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract null doc`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": null
                }
              ]
            }
        """.trimIndent()

        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract invalid payload format`() {
        val payload = "invalid json"
        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract empty json object`() {
        val payload = "{}"
        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract json array payload`() {
        val payload = "[]"
        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract missing rows field`() {
        val payload = """
            {
              "something_else": []
            }
        """.trimIndent()
        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }

    @Test
    fun `extract doc with null fields`() {
        val payload = """
            {
              "rows": [
                {
                  "doc": {
                    "parentCode": null,
                    "code": null
                  }
                }
              ]
            }
        """.trimIndent()
        val result = ServerMetadataExtractor.extract(payload, moshi)
        assertNull(result)
    }
}

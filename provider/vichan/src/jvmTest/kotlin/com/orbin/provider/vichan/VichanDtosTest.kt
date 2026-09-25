package com.orbin.provider.vichan

import com.google.common.truth.Truth.assertThat
import com.orbin.provider.vichan.api.VichanCatalogPage
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class VichanDtosTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }

    @Test
    fun `catalog accepts numeric media ids`() {
        val pages =
            json.decodeFromString<List<VichanCatalogPage>>(
                """
                [
                  {
                    "page": 1,
                    "threads": [
                      {
                        "no": 109180324,
                        "time": 1782955302,
                        "tim": 1782955302233643,
                        "filename": "1776765946891562",
                        "ext": ".png"
                      }
                    ]
                  }
                ]
                """.trimIndent(),
            )

        assertThat(
            pages
                .single()
                .threads
                .single()
                .tim,
        ).isEqualTo("1782955302233643")
    }

    @Test
    fun `catalog still accepts quoted media ids`() {
        val pages =
            json.decodeFromString<List<VichanCatalogPage>>(
                """
                [
                  {
                    "page": 1,
                    "threads": [
                      { "no": 1, "tim": "1690000000000", "filename": "cat", "ext": ".jpg" }
                    ]
                  }
                ]
                """.trimIndent(),
            )

        assertThat(
            pages
                .single()
                .threads
                .single()
                .tim,
        ).isEqualTo("1690000000000")
    }

    @Test
    fun `extra files accept numeric media ids`() {
        val pages =
            json.decodeFromString<List<VichanCatalogPage>>(
                """[{"page":1,"threads":[{"no":1,"extra_files":[{"tim":1782956444863280,"filename":"extra","ext":".jpg"}]}]}]""",
            )

        assertThat(
            pages
                .single()
                .threads
                .single()
                .extraFiles
                .single()
                .tim,
        ).isEqualTo("1782956444863280")
    }
}

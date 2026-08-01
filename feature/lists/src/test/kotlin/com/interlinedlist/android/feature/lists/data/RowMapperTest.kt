package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.data.remote.dto.RowDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

/** Rows carry dynamic values; the mapper coerces every JSON kind to a display string. */
class RowMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun row(dataJson: String): RowDto = RowDto(
        id = "r1",
        data = json.parseToJsonElement("""{ "data": $dataJson }""").jsonObject["data"]!!.jsonObject,
    )

    @Test
    fun `coerces strings numbers and booleans to display strings`() {
        val mapped = RowMapper.fromDto(
            row("""{ "name": "Dune", "pages": 412, "read": true }"""),
        )

        assertThat(mapped.id).isEqualTo("r1")
        assertThat(mapped.valueFor("name")).isEqualTo("Dune")
        assertThat(mapped.valueFor("pages")).isEqualTo("412")
        assertThat(mapped.valueFor("read")).isEqualTo("true")
    }

    @Test
    fun `null values become empty strings`() {
        val mapped = RowMapper.fromDto(row("""{ "note": null }"""))

        assertThat(mapped.valueFor("note")).isEmpty()
    }

    @Test
    fun `arrays and nested objects flatten to readable strings`() {
        val mapped = RowMapper.fromDto(
            row("""{ "tags": ["a", "b"], "meta": { "k": 1 } }"""),
        )

        assertThat(mapped.valueFor("tags")).isEqualTo("a, b")
        assertThat(mapped.valueFor("meta")).isEqualTo("k: 1")
    }

    @Test
    fun `missing key returns empty via valueFor`() {
        val mapped = RowMapper.fromDto(row("""{ "present": "yes" }"""))

        assertThat(mapped.valueFor("absent")).isEmpty()
    }
}

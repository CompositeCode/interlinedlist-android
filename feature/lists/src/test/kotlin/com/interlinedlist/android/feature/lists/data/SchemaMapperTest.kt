package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.domain.FieldType
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The list schema DSL reaches the client in several shapes; these tests pin the
 * mapper's tolerance so generic column rendering never depends on one wire form.
 */
class SchemaMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(raw: String) = SchemaMapper.fromJson(json.parseToJsonElement(raw))

    @Test
    fun `parses an array of field descriptors preserving order and types`() {
        val schema = parse(
            """
            [
              { "key": "name", "label": "Full Name", "type": "text", "required": true },
              { "key": "age", "type": "number" },
              { "key": "active", "type": "boolean" },
              { "key": "site", "type": "url" }
            ]
            """.trimIndent(),
        )

        assertThat(schema.fields.map { it.key }).containsExactly("name", "age", "active", "site").inOrder()
        val name = schema.fields.first()
        assertThat(name.label).isEqualTo("Full Name")
        assertThat(name.required).isTrue()
        assertThat(schema.fields[1].type).isEqualTo(FieldType.NUMBER)
        assertThat(schema.fields[2].type).isEqualTo(FieldType.BOOLEAN)
        assertThat(schema.fields[3].type).isEqualTo(FieldType.URL)
    }

    @Test
    fun `parses an object keyed by field key and humanizes labels`() {
        val schema = parse(
            """
            { "first_name": { "type": "text" }, "signUpDate": { "type": "date" } }
            """.trimIndent(),
        )

        val byKey = schema.fields.associateBy { it.key }
        assertThat(byKey.keys).containsExactly("first_name", "signUpDate")
        // Labels are humanized from the key when none is supplied.
        assertThat(byKey["first_name"]!!.label).isEqualTo("First Name")
        assertThat(byKey["signUpDate"]!!.label).isEqualTo("Sign Up Date")
        assertThat(byKey["signUpDate"]!!.type).isEqualTo(FieldType.DATE)
    }

    @Test
    fun `parses a bare object of key to type strings`() {
        val schema = parse("""{ "title": "text", "count": "number" }""")

        val byKey = schema.fields.associateBy { it.key }
        assertThat(byKey["title"]!!.type).isEqualTo(FieldType.TEXT)
        assertThat(byKey["count"]!!.type).isEqualTo(FieldType.NUMBER)
    }

    @Test
    fun `unwraps a properties wrapper object`() {
        val schema = parse("""{ "properties": [ { "key": "note", "type": "text" } ] }""")

        assertThat(schema.fields).hasSize(1)
        assertThat(schema.fields.first().key).isEqualTo("note")
    }

    @Test
    fun `reads select options`() {
        val schema = parse(
            """[ { "key": "status", "type": "select", "options": ["open", "closed"] } ]""",
        )

        val field = schema.fields.first()
        assertThat(field.type).isEqualTo(FieldType.SELECT)
        assertThat(field.options).containsExactly("open", "closed").inOrder()
    }

    @Test
    fun `unknown type falls back to text so the column is not dropped`() {
        val schema = parse("""[ { "key": "misc", "type": "wormhole" } ]""")

        assertThat(schema.fields.first().type).isEqualTo(FieldType.TEXT)
    }

    @Test
    fun `null and primitive schemas yield an empty schema`() {
        assertThat(SchemaMapper.fromJson(null).isEmpty).isTrue()
        assertThat(parse("\"nope\"").isEmpty).isTrue()
    }
}

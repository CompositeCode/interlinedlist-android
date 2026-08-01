package com.interlinedlist.android.feature.organizations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.organizations.data.remote.dto.OrganizationDto
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The API types several booleans as strings but real payloads mix booleans and
 * strings; the flexible serializer normalises both so `isPublic` is reliable.
 */
class FlexibleBooleanSerializerTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun publicOf(body: String): Boolean =
        json.decodeFromString(OrganizationDto.serializer(), body).resolvedPublic

    @Test
    fun `decodes a real json boolean`() {
        assertThat(publicOf("""{ "id": "1", "isPublic": true }""")).isTrue()
        assertThat(publicOf("""{ "id": "1", "isPublic": false }""")).isFalse()
    }

    @Test
    fun `decodes a string boolean`() {
        assertThat(publicOf("""{ "id": "1", "isPublic": "true" }""")).isTrue()
        assertThat(publicOf("""{ "id": "1", "isPublic": "false" }""")).isFalse()
        assertThat(publicOf("""{ "id": "1", "isPublic": "1" }""")).isTrue()
    }

    @Test
    fun `absent flag defaults to private`() {
        assertThat(publicOf("""{ "id": "1" }""")).isFalse()
    }

    @Test
    fun `falls back to the public field when isPublic is absent`() {
        assertThat(publicOf("""{ "id": "1", "public": "yes" }""")).isTrue()
    }
}

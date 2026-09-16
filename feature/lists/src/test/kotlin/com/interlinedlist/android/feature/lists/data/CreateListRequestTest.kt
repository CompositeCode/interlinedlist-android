package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.lists.data.remote.dto.CreateListRequest
import com.interlinedlist.android.feature.lists.data.remote.dto.RowWriteRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The wire contract for `POST /api/lists`. The API accepts more creation options
 * than the app used to send, and every one of them must be *omitted* — not sent
 * as `null` — when the caller does not supply it, so existing callers keep
 * producing exactly the body they produced before.
 */
class CreateListRequestTest {

    // Same configuration the app's Retrofit converter uses (see NetworkModule).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun encode(request: CreateListRequest): JsonObject =
        json.encodeToJsonElement(request).jsonObject

    @Test
    fun `omits every optional creation option when it is not supplied`() {
        val body = encode(CreateListRequest(title = "Books"))

        assertThat(body.keys).containsExactly("title")
        assertThat(body.keys).containsNoneOf("parentId", "folderId", "messageId")
        assertThat(body.keys).containsNoneOf("initialRows", "metadata", "source", "schema")
    }

    @Test
    fun `omits the options a caller explicitly passes as null`() {
        val body = encode(
            CreateListRequest(
                title = "Books",
                description = null,
                parentId = null,
                folderId = null,
                messageId = null,
                initialRows = null,
                metadata = null,
                source = null,
            ),
        )

        assertThat(body.keys).containsExactly("title")
    }

    @Test
    fun `carries every creation option the API accepts when supplied`() {
        val body = encode(
            CreateListRequest(
                title = "Books",
                description = "My reading backlog.",
                isPublic = true,
                parentId = "lst_parent",
                folderId = "fld_1",
                messageId = "msg_42",
                source = "github",
                metadata = buildJsonObject { put("colour", "blue") },
                initialRows = listOf(buildJsonObject { put("title", "Dune") }),
                schema = buildJsonObject { put("name", "Books") },
            ),
        )

        assertThat(body["title"]).isEqualTo(JsonPrimitive("Books"))
        assertThat(body["description"]).isEqualTo(JsonPrimitive("My reading backlog."))
        assertThat(body["isPublic"]).isEqualTo(JsonPrimitive(true))
        assertThat(body["parentId"]).isEqualTo(JsonPrimitive("lst_parent"))
        assertThat(body["folderId"]).isEqualTo(JsonPrimitive("fld_1"))
        assertThat(body["messageId"]).isEqualTo(JsonPrimitive("msg_42"))
        assertThat(body["source"]).isEqualTo(JsonPrimitive("github"))
        assertThat(body["metadata"]?.jsonObject?.get("colour")).isEqualTo(JsonPrimitive("blue"))
        assertThat(body["initialRows"]?.jsonArray).hasSize(1)
        assertThat(body["schema"]?.jsonObject?.get("name")).isEqualTo(JsonPrimitive("Books"))
    }

    @Test
    fun `initialRows serialise as the row payloads the list-data API expects`() {
        val row = buildJsonObject {
            put("title", "Dune")
            put("year", 1965)
            put("read", false)
        }

        val initialRows = encode(
            CreateListRequest(title = "Books", initialRows = listOf(row)),
        )["initialRows"]!!.jsonArray

        // `POST /api/lists/{id}/data` sends exactly this per-row shape under `data`:
        // a flat object keyed by schema field key.
        val rowWrite = json.encodeToJsonElement(RowWriteRequest(row)).jsonObject["data"]
        assertThat(initialRows).isEqualTo(JsonArray(listOf(rowWrite!!)))
    }

    @Test
    fun `metadata round-trips an arbitrary JSON object without losing keys`() {
        val metadata = buildJsonObject {
            put("colour", "blue")
            put("pinned", true)
            put("rank", 3)
            put("archivedAt", JsonPrimitive(null as String?))
            put("tags", buildJsonArray { add(JsonPrimitive("a")); add(JsonPrimitive("b")) })
            put(
                "nested",
                buildJsonObject {
                    put("deep", buildJsonObject { put("key", "value") })
                },
            )
        }

        val encoded = json.encodeToString(CreateListRequest.serializer(), CreateListRequest("Books", metadata = metadata))
        val decoded = json.decodeFromString(CreateListRequest.serializer(), encoded)

        assertThat(decoded.metadata).isEqualTo(metadata)
        // Every key survives the round-trip, including explicit nulls and nesting.
        assertThat(decoded.metadata!!.keys)
            .containsExactly("colour", "pinned", "rank", "archivedAt", "tags", "nested")
        assertThat(decoded.metadata!!["nested"]?.jsonObject?.get("deep")?.jsonObject?.get("key"))
            .isEqualTo(JsonPrimitive("value"))
        assertThat(encoded).contains("\"archivedAt\":null")
    }
}

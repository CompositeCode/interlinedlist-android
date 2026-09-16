package com.interlinedlist.android.feature.ai.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.ai.domain.AiArtifact
import com.interlinedlist.android.feature.ai.domain.AiCreated
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGenerateOptions
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The write half of the flow: that `/suggest` and `/generate` carry the right
 * `feature` discriminator and body, that a preview parses, and that the four
 * `created` shapes map.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiSuggestAndGenerateTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = repositoryFor(server, dispatcher)

    private fun bodyOf(request: okhttp3.mockwebserver.RecordedRequest): JsonObject =
        json.decodeFromString(JsonObject.serializer(), request.body.readUtf8())

    @Test
    fun `suggest posts the feature discriminator, input and context`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                200,
                """
                { "ok": true, "feature": "writing_assist",
                  "artifact": { "kind": "message", "content": "Rewritten draft" },
                  "usage": { "inputTokens": 412, "outputTokens": 96, "model": "claude-sonnet-5" },
                  "quota": { "usedToday": 7, "dailyLimit": 50 } }
                """.trimIndent(),
            ),
        )

        val result = repository().suggest(
            feature = AiFeature.WRITING_ASSIST,
            input = AiSuggestInput(
                input = "we shipped the new thing today",
                context = buildJsonObject {
                    put("action", "rewrite")
                    put("targetPlatform", "mastodon")
                },
            ),
        )

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/ai/suggest")
        assertThat(request.method).isEqualTo("POST")
        val body = bodyOf(request)
        assertThat(body["feature"].toString()).isEqualTo("\"writing_assist\"")
        assertThat(body["input"].toString()).isEqualTo("\"we shipped the new thing today\"")
        assertThat(body["context"]?.toString()).contains("rewrite")

        val preview = (result as AiResult.Success).data
        assertThat(preview.feature).isEqualTo(AiFeature.WRITING_ASSIST)
        assertThat(preview.artifact.kind).isEqualTo("message")
        assertThat(preview.usage?.model).isEqualTo("claude-sonnet-5")
        // usedToday/dailyLimit with no explicit remainder still yields one.
        assertThat(preview.quota?.remainingActions).isEqualTo(43)
    }

    @Test
    fun `every feature sends its own discriminator`() = runTest(dispatcher) {
        AiFeature.entries.forEach { feature ->
            server.enqueue(jsonResponse(200, """{ "ok": true, "artifact": { "kind": "list" } }"""))

            val result = repository().suggest(feature, AiSuggestInput("ten words of input for the series gate here"))

            val body = bodyOf(server.takeRequest())
            assertThat(body["feature"].toString()).isEqualTo("\"${feature.apiValue}\"")
            // The response omitted `feature`; the requested one is kept.
            assertThat((result as AiResult.Success).data.feature).isEqualTo(feature)
        }
    }

    @Test
    fun `generate sends the confirmed artifact back verbatim under the same feature`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                200,
                """{ "ok": true, "artifact": { "kind": "document", "title": "Draft", "markdown": "# Draft" } }""",
            ),
        )
        server.enqueue(
            jsonResponse(
                201,
                """
                { "ok": true, "feature": "powered_document",
                  "created": { "documentId": "doc_1" },
                  "quota": { "usedToday": 9, "dailyLimit": 50, "remaining": 41 } }
                """.trimIndent(),
            ),
        )
        val repository = repository()

        val preview = (repository.suggest(
            AiFeature.POWERED_DOCUMENT,
            AiSuggestInput("draft something about widgets"),
        ) as AiResult.Success).data
        server.takeRequest()

        val result = repository.generate(preview.confirm())

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/ai/generate")
        val body = bodyOf(request)
        assertThat(body["feature"].toString()).isEqualTo("\"powered_document\"")
        assertThat(body["artifact"]).isEqualTo(preview.artifact.payload)

        val generation = (result as AiResult.Success).data
        assertThat(generation.created).isEqualTo(AiCreated.DocumentCreated("doc_1"))
        assertThat(generation.quota?.remainingActions).isEqualTo(41)
    }

    @Test
    fun `generate sends the user's edit instead of the suggestion when given one`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "ok": true, "created": { "listId": "list_1" } }"""))
        val preview = previewOf(AiFeature.POWERED_TEMPLATE, buildJsonObject { put("kind", "list"); put("title", "Model's title") })
        val edited = AiArtifact(buildJsonObject { put("kind", "list"); put("title", "My title") })

        val result = repository().generate(preview.confirm(edited))

        val body = bodyOf(server.takeRequest())
        assertThat(body["artifact"]).isEqualTo(edited.payload)
        assertThat((result as AiResult.Success).data.created).isEqualTo(AiCreated.ListCreated("list_1"))
    }

    @Test
    fun `generate options ride along only when set`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "ok": true, "created": { "listId": "list_1" } }"""))
        val preview = previewOf(AiFeature.MESSAGE_SERIES, buildJsonObject { put("kind", "message_series") })

        repository().generate(
            preview.confirm(),
            AiGenerateOptions(
                scheduleImmediately = true,
                crossPost = buildJsonObject { put("crossPostToBluesky", true) },
            ),
        )

        val body = bodyOf(server.takeRequest())
        assertThat(body["scheduleImmediately"].toString()).isEqualTo("true")
        assertThat(body["crossPost"]?.toString()).contains("crossPostToBluesky")
        // Unset optionals are omitted rather than sent as null.
        assertThat(body.containsKey("model")).isFalse()
    }

    @Test
    fun `a scheduled message series maps to its own created shape`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                201,
                """
                { "ok": true, "created": { "scheduledMessageIds": ["m1", "m2"],
                  "firstScheduledAt": "2026-09-16T10:00:00.000Z",
                  "lastScheduledAt": "2026-09-16T10:08:00.000Z" } }
                """.trimIndent(),
            ),
        )
        val preview = previewOf(AiFeature.MESSAGE_SERIES, buildJsonObject { put("kind", "message_series") })

        val result = repository().generate(preview.confirm(), AiGenerateOptions(scheduleImmediately = true))

        assertThat((result as AiResult.Success).data.created).isEqualTo(
            AiCreated.ScheduledMessagesCreated(
                messageIds = listOf("m1", "m2"),
                firstScheduledAt = "2026-09-16T10:00:00.000Z",
                lastScheduledAt = "2026-09-16T10:08:00.000Z",
            ),
        )
    }

    @Test
    fun `an article series maps to the folder shape`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                201,
                """{ "ok": true, "created": { "folderId": "f1", "documentIds": ["d1", "d2"] } }""",
            ),
        )
        val preview = previewOf(AiFeature.ARTICLE_SERIES, buildJsonObject { put("kind", "doc_series") })

        val result = repository().generate(preview.confirm())

        assertThat((result as AiResult.Success).data.created).isEqualTo(
            AiCreated.DocumentSeriesCreated("f1", listOf("d1", "d2")),
        )
    }

    @Test
    fun `an unrecognised created body is not a failure`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "ok": true, "created": { "somethingNew": "x" } }"""))
        val preview = previewOf(AiFeature.POWERED_DOCUMENT, buildJsonObject { put("kind", "document") })

        val result = repository().generate(preview.confirm())

        assertThat((result as AiResult.Success).data.created).isEqualTo(AiCreated.Unrecognised)
    }
}

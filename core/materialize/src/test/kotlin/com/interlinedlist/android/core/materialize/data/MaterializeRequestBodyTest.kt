package com.interlinedlist.android.core.materialize.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.materialize.domain.CrossPostChannel
import com.interlinedlist.android.core.materialize.domain.DocConfig
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.ListConfig
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MessageDraftConfig
import com.interlinedlist.android.core.materialize.domain.RowDataStyle
import com.interlinedlist.android.core.model.CustomerStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * One MockWebServer round-trip per destination, asserting the exact body
 * `POST /api/materialize` receives, plus the serialisation of every source kind.
 *
 * The gate is pre-resolved to a subscriber in each test so the only request on
 * the wire is the materialize call itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterializeRequestBodyTest {

    private lateinit var server: MockWebServer
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun subscriberRepository(): DefaultMaterializeRepository {
        val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.SUBSCRIBER) }
        return repositoryFor(server, dispatcher, gate)
    }

    private val messages = MaterializeSource.Messages(listOf("msg_1", "msg_2"))

    // ---------------------------------------------------------------- targets

    @Test
    fun `To List posts target list with the list config only`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_x9f2", "title": "Launch notes" } }"""))

        val result = subscriberRepository().materialize(
            MaterializeRequest.ToList(
                source = messages,
                listConfig = ListConfig(
                    title = "Launch notes",
                    description = "My reading backlog.",
                    isPublic = false,
                    includeData = true,
                    fields = listOf(
                        MaterializeColumn(
                            propertyKey = "content",
                            propertyName = "Content",
                            propertyType = ListColumnType.TEXTAREA,
                            sourceKey = "content",
                        ),
                    ),
                ),
            ),
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/materialize")

        val body = request.jsonBody()
        assertThat(body["target"]?.jsonPrimitive?.content).isEqualTo("list")
        assertThat(body["docConfig"]).isNull()
        assertThat(body["messageConfig"]).isNull()

        val listConfig = body["listConfig"]!!.jsonObject
        assertThat(listConfig["title"]?.jsonPrimitive?.content).isEqualTo("Launch notes")
        assertThat(listConfig["description"]?.jsonPrimitive?.content).isEqualTo("My reading backlog.")
        assertThat(listConfig["isPublic"]?.jsonPrimitive?.content).isEqualTo("false")
        assertThat(listConfig["includeData"]?.jsonPrimitive?.content).isEqualTo("true")

        val field = listConfig["fields"]!!.jsonArray.single().jsonObject
        assertThat(field["propertyKey"]?.jsonPrimitive?.content).isEqualTo("content")
        assertThat(field["propertyName"]?.jsonPrimitive?.content).isEqualTo("Content")
        assertThat(field["propertyType"]?.jsonPrimitive?.content).isEqualTo("textarea")
        assertThat(field["sourceKey"]?.jsonPrimitive?.content).isEqualTo("content")

        val outcome = (result as ApiResult.Success).data as MaterializeOutcome.ListCreated
        assertThat(outcome.list.id).isEqualTo("lst_x9f2")
        assertThat(outcome.list.title).isEqualTo("Launch notes")
    }

    @Test
    fun `To Doc posts target doc with the doc config only`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "document": { "id": "doc_y3a8", "title": "Launch notes" } }"""))

        val result = subscriberRepository().materialize(
            MaterializeRequest.ToDocument(
                source = MaterializeSource.Lists(listOf("lst_1")),
                docConfig = DocConfig(
                    title = "Launch notes",
                    relativePath = "launch-notes.md",
                    isPublic = true,
                    listStyle = DocumentListStyle.NUMBERED,
                    rowDataStyle = RowDataStyle.SUB_ITEMS,
                ),
            ),
        )

        val body = server.takeRequest().jsonBody()
        assertThat(body["target"]?.jsonPrimitive?.content).isEqualTo("doc")
        assertThat(body["listConfig"]).isNull()
        assertThat(body["messageConfig"]).isNull()

        val docConfig = body["docConfig"]!!.jsonObject
        assertThat(docConfig["title"]?.jsonPrimitive?.content).isEqualTo("Launch notes")
        assertThat(docConfig["relativePath"]?.jsonPrimitive?.content).isEqualTo("launch-notes.md")
        assertThat(docConfig["isPublic"]?.jsonPrimitive?.content).isEqualTo("true")
        assertThat(docConfig["listStyle"]?.jsonPrimitive?.content).isEqualTo("numbered")
        // The documented spelling is hyphenated, not camelCase.
        assertThat(docConfig["rowDataStyle"]?.jsonPrimitive?.content).isEqualTo("sub-items")

        val outcome = (result as ApiResult.Success).data as MaterializeOutcome.DocumentCreated
        assertThat(outcome.document.id).isEqualTo("doc_y3a8")
    }

    @Test
    fun `To List and Doc posts target both and carries both configs`() = runTest(dispatcher) {
        server.enqueue(
            jsonResponse(
                201,
                """
                { "list": { "id": "lst_x9f2", "title": "Launch notes" },
                  "document": { "id": "doc_y3a8", "title": "Launch notes" } }
                """.trimIndent(),
            ),
        )

        val result = subscriberRepository().materialize(
            MaterializeRequest.ToListAndDocument(
                source = MaterializeSource.Document("doc_src"),
                listConfig = ListConfig(title = "Launch notes"),
                docConfig = DocConfig(title = "Launch notes", listStyle = DocumentListStyle.BULLETED),
            ),
        )

        val body = server.takeRequest().jsonBody()
        assertThat(body["target"]?.jsonPrimitive?.content).isEqualTo("both")
        assertThat(body["listConfig"]!!.jsonObject["title"]?.jsonPrimitive?.content)
            .isEqualTo("Launch notes")
        assertThat(body["docConfig"]!!.jsonObject["listStyle"]?.jsonPrimitive?.content)
            .isEqualTo("bulleted")
        assertThat(body["messageConfig"]).isNull()

        val outcome = (result as ApiResult.Success).data as MaterializeOutcome.ListAndDocumentCreated
        assertThat(outcome.list.id).isEqualTo("lst_x9f2")
        assertThat(outcome.document.id).isEqualTo("doc_y3a8")
    }

    @Test
    fun `To Message posts target message and returns a draft that created nothing`() =
        runTest(dispatcher) {
            server.enqueue(
                jsonResponse(
                    201,
                    """
                    { "message": { "content": "Books to Read\n\n340 items in total.",
                                   "thread": ["Books to Read\n\n340 items in total."],
                                   "isThread": false, "charLimit": 300 } }
                    """.trimIndent(),
                ),
            )

            val result = subscriberRepository().materialize(
                MaterializeRequest.ToMessageDraft(
                    source = MaterializeSource.Lists(listOf("lst_1")),
                    messageConfig = MessageDraftConfig(
                        content = "Books to Read",
                        crossPostTargets = listOf(CrossPostChannel.BLUESKY, CrossPostChannel.MASTODON),
                        allowThread = true,
                        publiclyVisible = true,
                        tags = listOf("reading"),
                        scheduledAt = "2026-09-20T10:00:00.000Z",
                    ),
                ),
            )

            val body = server.takeRequest().jsonBody()
            assertThat(body["target"]?.jsonPrimitive?.content).isEqualTo("message")
            assertThat(body["listConfig"]).isNull()
            assertThat(body["docConfig"]).isNull()

            val messageConfig = body["messageConfig"]!!.jsonObject
            assertThat(messageConfig["content"]?.jsonPrimitive?.content).isEqualTo("Books to Read")
            assertThat(messageConfig["allowThread"]?.jsonPrimitive?.content).isEqualTo("true")
            assertThat(messageConfig["publiclyVisible"]?.jsonPrimitive?.content).isEqualTo("true")
            assertThat(messageConfig["scheduledAt"]?.jsonPrimitive?.content)
                .isEqualTo("2026-09-20T10:00:00.000Z")
            assertThat(messageConfig["crossPostTargets"]!!.jsonArray.map { it.jsonPrimitive.content })
                .containsExactly("bluesky", "mastodon").inOrder()
            assertThat(messageConfig["tags"]!!.jsonArray.map { it.jsonPrimitive.content })
                .containsExactly("reading")

            val outcome = (result as ApiResult.Success).data as MaterializeOutcome.DraftReady
            assertThat(outcome.draft.charLimit).isEqualTo(300)
            assertThat(outcome.draft.isThread).isFalse()
            assertThat(outcome.draft.thread).hasSize(1)
        }

    // ----------------------------------------------------------- source kinds

    @Test
    fun `every source kind serialises its own id fields and nothing else`() = runTest(dispatcher) {
        val cases = listOf(
            MaterializeSource.Messages(listOf("msg_1", "msg_2")) to
                mapOf("kind" to "messages", "messageIds" to listOf("msg_1", "msg_2")),
            MaterializeSource.Lists(listOf("lst_1")) to
                mapOf("kind" to "lists", "listIds" to listOf("lst_1")),
            MaterializeSource.Rows("lst_1", listOf("row_1", "row_2")) to
                mapOf("kind" to "rows", "listId" to "lst_1", "rowIds" to listOf("row_1", "row_2")),
            MaterializeSource.Document("doc_1") to
                mapOf("kind" to "document", "documentId" to "doc_1"),
            MaterializeSource.DocumentSelection("doc_1", "## Heading\n- a\n- b") to
                mapOf(
                    "kind" to "docElements",
                    "documentId" to "doc_1",
                    "markdown" to "## Heading\n- a\n- b",
                ),
        )

        cases.forEach { (source, expected) ->
            server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_new", "title": "t" } }"""))

            subscriberRepository().materialize(
                MaterializeRequest.ToList(source, ListConfig(title = "t")),
            )

            val sent = server.takeRequest().jsonBody()["source"]!!.jsonObject
            // Only the keys this kind defines are present — no empty siblings.
            assertThat(sent.keys).containsExactlyElementsIn(expected.keys)
            expected.forEach { (key, value) ->
                when (value) {
                    is List<*> -> assertThat(sent[key]!!.jsonArray.map { it.jsonPrimitive.content })
                        .isEqualTo(value)
                    else -> assertThat(sent[key]?.jsonPrimitive?.content).isEqualTo(value)
                }
            }
        }
    }

    @Test
    fun `a rows source sends ids only - no cell values can be smuggled through`() =
        runTest(dispatcher) {
            server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_new", "title": "t" } }"""))

            subscriberRepository().materialize(
                MaterializeRequest.ToList(
                    source = MaterializeSource.Rows("lst_1", listOf("row_1")),
                    listConfig = ListConfig(
                        title = "t",
                        fields = listOf(
                            MaterializeColumn(
                                propertyKey = "title",
                                propertyName = "Title",
                                propertyType = ListColumnType.TEXT,
                                sourceKey = "title",
                            ),
                        ),
                    ),
                ),
            )

            val body = server.takeRequest().jsonBody()
            // The server re-derives every value from `sourceKey`; the body must
            // not contain a `rowData`/`values`/`content` payload of its own.
            assertThat(body.keys).containsExactly("target", "source", "listConfig")
            val field = body["listConfig"]!!.jsonObject["fields"]!!.jsonArray.single().jsonObject
            assertThat(field.keys).containsExactly(
                "propertyKey",
                "propertyName",
                "propertyType",
                "sourceKey",
            )
        }

    @Test
    fun `a user-added column sends sourceKey as an explicit null`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_new", "title": "t" } }"""))

        subscriberRepository().materialize(
            MaterializeRequest.ToList(
                source = messages,
                listConfig = ListConfig(
                    title = "t",
                    fields = listOf(
                        MaterializeColumn(
                            propertyKey = "notes",
                            propertyName = "Notes",
                            propertyType = ListColumnType.SELECT,
                            sourceKey = null,
                            isRequired = false,
                            options = listOf("todo", "done"),
                        ),
                    ),
                ),
            ),
        )

        val field: JsonObject = server.takeRequest().jsonBody()["listConfig"]!!
            .jsonObject["fields"]!!.jsonArray.single().jsonObject
        // Present and null — an omitted key would not say "user-added column".
        assertThat(field).containsKey("sourceKey")
        assertThat(field["sourceKey"]).isEqualTo(JsonNull)
        assertThat(field["isRequired"]?.jsonPrimitive?.content).isEqualTo("false")
        assertThat(field["options"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("todo", "done").inOrder()
    }

    @Test
    fun `every column type sends its documented wire value`() = runTest(dispatcher) {
        ListColumnType.entries.forEach { type ->
            server.enqueue(jsonResponse(201, """{ "list": { "id": "lst_new", "title": "t" } }"""))

            subscriberRepository().materialize(
                MaterializeRequest.ToList(
                    source = messages,
                    listConfig = ListConfig(
                        title = "t",
                        fields = listOf(
                            MaterializeColumn("k", "K", type, sourceKey = "k"),
                        ),
                    ),
                ),
            )

            val field = server.takeRequest().jsonBody()["listConfig"]!!
                .jsonObject["fields"]!!.jsonArray.single().jsonObject
            assertThat(field["propertyType"]?.jsonPrimitive?.content).isEqualTo(type.apiValue)
        }
    }

    @Test
    fun `an omitted config is left out of the body entirely`() = runTest(dispatcher) {
        server.enqueue(jsonResponse(201, """{ "document": { "id": "doc_1", "title": "t" } }"""))

        subscriberRepository().materialize(
            MaterializeRequest.ToDocument(source = MaterializeSource.Document("doc_src")),
        )

        val body = server.takeRequest().jsonBody()
        assertThat(body.keys).containsExactly("target", "source")
    }
}

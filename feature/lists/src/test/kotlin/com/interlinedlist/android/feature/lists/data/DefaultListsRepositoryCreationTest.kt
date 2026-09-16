package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.interlinedlist.android.feature.lists.domain.ListSource
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Creation options (`parentId`, `folderId`, `messageId`, `initialRows`,
 * `metadata`, `source`) and parent-chain resolution, driven through the real
 * Retrofit/OkHttp stack so the assertions are about the bytes that reach the API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryCreationTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
    private lateinit var dao: FakeCreationDao
    private lateinit var repository: DefaultListsRepository

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val dispatcher = StandardTestDispatcher()

    private val testDispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ListsApi::class.java)
        dao = FakeCreationDao()
        repository = DefaultListsRepository(api, dao, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    private fun RecordedRequest.bodyJson(): JsonObject =
        json.parseToJsonElement(body.readUtf8()).jsonObject

    private fun enqueueCreated(id: String = "lst_new", extra: String = "") {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "message": "created", "data": { "id": "$id", "title": "Books"$extra } }""",
            ),
        )
    }

    @Test
    fun `createList sends only the fields the caller supplied`() = runTest(dispatcher) {
        enqueueCreated()

        repository.createList(title = "Books", description = "Backlog", isPublic = false)

        val body = server.takeRequest().bodyJson()
        assertThat(body["title"]).isEqualTo(JsonPrimitive("Books"))
        assertThat(body["description"]).isEqualTo(JsonPrimitive("Backlog"))
        // Untouched options must not appear at all — not even as explicit nulls.
        assertThat(body.keys).containsNoneOf("parentId", "folderId", "messageId")
        assertThat(body.keys).containsNoneOf("initialRows", "metadata", "source")
    }

    @Test
    fun `createList sends every creation option the API accepts`() = runTest(dispatcher) {
        enqueueCreated(extra = """, "parentId": "lst_parent" """)

        val result = repository.createList(
            title = "Books",
            description = "Backlog",
            isPublic = true,
            parentId = "lst_parent",
            folderId = "fld_1",
            messageId = "msg_42",
            initialRows = listOf(mapOf("title" to "Dune", "year" to "1965")),
            metadata = buildJsonObject { put("colour", "blue") },
            source = ListSource.GITHUB,
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().bodyJson()
        assertThat(body["parentId"]).isEqualTo(JsonPrimitive("lst_parent"))
        assertThat(body["folderId"]).isEqualTo(JsonPrimitive("fld_1"))
        assertThat(body["messageId"]).isEqualTo(JsonPrimitive("msg_42"))
        assertThat(body["isPublic"]).isEqualTo(JsonPrimitive(true))
        assertThat(body["source"]).isEqualTo(JsonPrimitive("github"))
        assertThat(body["metadata"]?.jsonObject?.get("colour")).isEqualTo(JsonPrimitive("blue"))
        // Rows go out in the same shape `POST /api/lists/{id}/data` uses: a flat
        // object per row, keyed by schema field key.
        val rows = body["initialRows"]!!.jsonArray
        assertThat(rows).hasSize(1)
        assertThat(rows[0].jsonObject["title"]).isEqualTo(JsonPrimitive("Dune"))
        assertThat(rows[0].jsonObject["year"]).isEqualTo(JsonPrimitive("1965"))
        // The created child is cached with its parent so the breadcrumb can resolve.
        assertThat((result as ApiResult.Success).data.parentId).isEqualTo("lst_parent")
    }

    @Test
    fun `createList drops blank row values like the row write path does`() = runTest(dispatcher) {
        enqueueCreated()

        repository.createList(
            title = "Books",
            initialRows = listOf(mapOf("title" to "Dune", "notes" to "  ")),
        )

        val rows = server.takeRequest().bodyJson()["initialRows"]!!.jsonArray
        assertThat(rows[0].jsonObject.keys).containsExactly("title")
    }

    @Test
    fun `createListFromMessage posts the message id with the message as the description`() =
        runTest(dispatcher) {
            enqueueCreated(extra = """, "messageId": "msg_42" """)

            repository.createListFromMessage(
                messageId = "msg_42",
                title = "Reading picks",
                description = "The message body",
            )

            val body = server.takeRequest().bodyJson()
            assertThat(body["messageId"]).isEqualTo(JsonPrimitive("msg_42"))
            assertThat(body["title"]).isEqualTo(JsonPrimitive("Reading picks"))
            assertThat(body["description"]).isEqualTo(JsonPrimitive("The message body"))
        }

    @Test
    fun `getParentChain walks a multi-level chain and returns it root first`() = runTest(dispatcher) {
        // parent → grandparent → root, each fetched in turn.
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "p", "title": "Parent", "parentId": "gp" } }"""))
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "gp", "title": "Grandparent", "parentId": "root" } }"""))
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "root", "title": "Root" } }"""))

        val result = repository.getParentChain("p")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val chain = (result as ApiResult.Success).data
        assertThat(chain.map { it.id }).containsExactly("root", "gp", "p").inOrder()
        assertThat(chain.map { it.title }).containsExactly("Root", "Grandparent", "Parent").inOrder()
        assertThat(server.takeRequest().path).isEqualTo("/api/lists/p")
        assertThat(server.takeRequest().path).isEqualTo("/api/lists/gp")
        assertThat(server.takeRequest().path).isEqualTo("/api/lists/root")
    }

    @Test
    fun `getParentChain uses a nested parent object instead of another request`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": {
                    "id": "p", "title": "Parent", "parentId": "root",
                    "parent": { "id": "root", "title": "Root", "parentId": null }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getParentChain("p")

        val chain = (result as ApiResult.Success).data
        assertThat(chain.map { it.id }).containsExactly("root", "p").inOrder()
        // The nested `parent` the server sent spares a round trip.
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `getParentChain stops on a cycle`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "a", "title": "A", "parentId": "b" } }"""))
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "b", "title": "B", "parentId": "a" } }"""))

        val chain = (repository.getParentChain("a") as ApiResult.Success).data

        assertThat(chain.map { it.id }).containsExactly("b", "a").inOrder()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `getParentChain truncates when an ancestor cannot be fetched`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "p", "title": "Parent", "parentId": "gp" } }"""))
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "not found", "code": "not_found" }"""))

        val chain = (repository.getParentChain("p") as ApiResult.Success).data

        // A broken link shortens the breadcrumb; it does not fail the screen.
        assertThat(chain.map { it.id }).containsExactly("p")
    }

    @Test
    fun `getParentChain reports the failure when nothing could be resolved`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "not found", "code": "not_found" }"""))

        assertThat(repository.getParentChain("p")).isInstanceOf(ApiResult.Failure::class.java)
    }

    @Test
    fun `getListDetail exposes the parent id so the breadcrumb can be resolved`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "data": { "id": "c", "title": "Child", "parentId": "p" } }"""))
        server.enqueue(MockResponse().setBody("""[ { "key": "title", "type": "text" } ]"""))
        server.enqueue(MockResponse().setBody("""{ "data": [] }"""))

        val detail = (repository.getListDetail("c") as ApiResult.Success).data

        assertThat(detail.summary.parentId).isEqualTo("p")
        // The cache keeps the parent link too, so an offline open still shows it.
        assertThat(dao.cached.single().parentId).isEqualTo("p")
    }
}

/** In-memory [ListDao] backed by a StateFlow, for JVM repository tests. */
private class FakeCreationDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())

    val cached: List<CachedListEntity> get() = state.value

    override fun observeLists(): Flow<List<CachedListEntity>> = state

    override suspend fun upsertAll(lists: List<CachedListEntity>) {
        val byId = state.value.associateBy { it.id }.toMutableMap()
        lists.forEach { byId[it.id] = it }
        state.value = byId.values.toList()
    }

    override suspend fun upsert(list: CachedListEntity) = upsertAll(listOf(list))

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

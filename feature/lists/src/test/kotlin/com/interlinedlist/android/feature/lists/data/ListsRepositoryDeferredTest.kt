package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.SchemaField
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * MockWebServer coverage for the round-2 deferred endpoints: schema editing,
 * GitHub refresh, watchers, and connections. Verifies request shapes (paths,
 * methods, bodies) and DTO→domain mapping over a real HTTP stack.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListsRepositoryDeferredTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
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
        repository = DefaultListsRepository(api, FakeDao(), json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `updateSchema sends the serialised DSL string and parses the echo`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "schema": [ { "key": "title", "type": "text" } ] }""",
            ),
        )
        val schema = ListSchema(listOf(SchemaField("title", "Title", FieldType.TEXT)))

        val result = repository.updateSchema("L1", schema)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.fields.map { it.key }).containsExactly("title")

        val request: RecordedRequest = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/lists/L1/schema")
        val body = request.body.readUtf8()
        // The schema is sent as a stringified DSL under "schema".
        assertThat(body).contains("\"schema\"")
        assertThat(body).contains("title")
    }

    @Test
    fun `updateSchema keeps the sent schema when the response omits it`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val schema = ListSchema(listOf(SchemaField("k", "K", FieldType.NUMBER)))

        val result = repository.updateSchema("L1", schema)

        val parsed = (result as ApiResult.Success).data
        assertThat(parsed.fields.single().key).isEqualTo("k")
        assertThat(parsed.fields.single().type).isEqualTo(FieldType.NUMBER)
    }

    @Test
    fun `refreshGithubList maps counts into a summary`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "success": true, "added": 2, "updated": 1, "removed": 0 }"""),
        )

        val result = repository.refreshGithubList("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.summary).isEqualTo("2 added, 1 updated")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/refresh")
    }

    @Test
    fun `getWatchers maps flattened and nested rows`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": [
                    { "userId": "u1", "username": "ada", "role": "admin" },
                    { "role": "viewer", "user": { "id": "u2", "username": "grace" } }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getWatchers("L1")

        val watchers = (result as ApiResult.Success).data
        assertThat(watchers.map { it.userId }).containsExactly("u1", "u2").inOrder()
        assertThat(watchers[0].role).isEqualTo(WatcherRole.ADMIN)
        assertThat(watchers[1].username).isEqualTo("grace")
    }

    @Test
    fun `isWatching resolves either boolean flavour`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "isWatching": true }"""))

        val result = repository.isWatching("L1")

        assertThat((result as ApiResult.Success).data).isTrue()
        assertThat(server.takeRequest().path).isEqualTo("/api/lists/L1/watchers/me")
    }

    @Test
    fun `searchWatcherCandidates excludes existing watchers via query`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "data": [ { "id": "u9", "username": "linus" } ] }"""),
        )

        val result = repository.searchWatcherCandidates("L1", "lin")

        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("linus")
        val request = server.takeRequest()
        assertThat(request.path).contains("/api/lists/L1/watchers/users")
        assertThat(request.path).contains("search=lin")
        assertThat(request.path).contains("excludeWatchers=true")
    }

    @Test
    fun `addWatcher posts the user id and role`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = repository.addWatcher("L1", "u5", WatcherRole.EDITOR)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/watchers")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"userId\":\"u5\"")
        assertThat(body).contains("\"role\":\"editor\"")
    }

    @Test
    fun `updateWatcherRole puts the new role`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        repository.updateWatcherRole("L1", "u5", WatcherRole.ADMIN)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/lists/L1/watchers/u5")
        assertThat(request.body.readUtf8()).contains("\"role\":\"admin\"")
    }

    @Test
    fun `removeWatcher deletes by user id`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.removeWatcher("L1", "u5")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/lists/L1/watchers/u5")
    }

    @Test
    fun `getConnections maps rows with title fallbacks`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "data": [ { "id": "c1", "fromListId": "l1", "toListId": "l2" } ] }""",
            ),
        )

        val result = repository.getConnections()

        val connection = (result as ApiResult.Success).data.single()
        assertThat(connection.id).isEqualTo("c1")
        assertThat(connection.fromListTitle).isEqualTo("l1")
        assertThat(server.takeRequest().path).isEqualTo("/api/lists/connections")
    }

    @Test
    fun `createConnection posts the edge and returns the created connection`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "connection": { "id": "c9", "fromListId": "l1", "toListId": "l2", "label": "blocks" } }"""),
        )

        val result = repository.createConnection("l1", "l2", "blocks")

        val connection = (result as ApiResult.Success).data
        assertThat(connection.id).isEqualTo("c9")
        assertThat(connection.label).isEqualTo("blocks")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/connections")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"fromListId\":\"l1\"")
        assertThat(body).contains("\"toListId\":\"l2\"")
    }

    @Test
    fun `deleteConnection maps a 404 to NotFound`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "gone" }"""))

        val result = repository.deleteConnection("c1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }
}

/** Minimal in-memory [ListDao] for these HTTP-level tests (cache is not exercised). */
private class FakeDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())
    override fun observeLists(): Flow<List<CachedListEntity>> = state
    override suspend fun upsertAll(lists: List<CachedListEntity>) {
        val byId = state.value.associateBy { it.id }.toMutableMap()
        lists.forEach { byId[it.id] = it }
        state.value = byId.values.toList()
    }
    override suspend fun upsert(list: CachedListEntity) = upsertAll(listOf(list))
    override suspend fun deleteById(id: String) { state.value = state.value.filterNot { it.id == id } }
    override suspend fun clear() { state.value = emptyList() }
}

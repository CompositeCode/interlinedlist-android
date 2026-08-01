package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
 * Repository behaviour against a real HTTP stack (Retrofit + OkHttp) driven by
 * MockWebServer, with an in-memory DAO standing in for Room. Verifies DTO→domain
 * mapping, offline-first caching, error normalisation, and the subscription gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
    private lateinit var dao: FakeListDao
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
        dao = FakeListDao()
        repository = DefaultListsRepository(api, dao, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refreshLists maps DTOs and replaces the Room cache`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": [
                    { "id": "1", "title": "Reading", "description": "Books", "itemCount": 3 },
                    { "id": "2", "title": "Trips", "rowCount": 5, "isPublic": true }
                  ],
                  "pagination": { "total": 2, "limit": 20, "offset": 0, "hasMore": false }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshLists()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val page = (result as ApiResult.Success).data
        assertThat(page.items.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(page.items[0].itemCount).isEqualTo(3)
        assertThat(page.items[1].itemCount).isEqualTo(5)
        assertThat(page.hasMore).isFalse()
        // Room is the source of truth: the cache now streams the same two lists.
        assertThat(dao.observeLists().first().map { it.id }).containsExactly("1", "2")
    }

    @Test
    fun `refreshLists maps a subscription 403 to SubscriptionRequired`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{ "error": "This feature requires an active subscription" }"""),
        )

        val result = repository.refreshLists()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `getListDetail composes metadata schema and rows generically`() = runTest(dispatcher) {
        // 1) list
        server.enqueue(
            MockResponse().setBody(
                """{ "list": { "id": "L1", "title": "Reading", "itemCount": 1 } }""",
            ),
        )
        // 2) schema (array DSL)
        server.enqueue(
            MockResponse().setBody(
                """[ { "key": "title", "type": "text" }, { "key": "pages", "type": "number" } ]""",
            ),
        )
        // 3) rows
        server.enqueue(
            MockResponse().setBody(
                """
                { "data": [ { "id": "r1", "data": { "title": "Dune", "pages": 412 } } ] }
                """.trimIndent(),
            ),
        )

        val result = repository.getListDetail("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val detail = (result as ApiResult.Success).data
        assertThat(detail.summary.title).isEqualTo("Reading")
        assertThat(detail.schema.fields.map { it.key }).containsExactly("title", "pages").inOrder()
        assertThat(detail.rows.single().valueFor("title")).isEqualTo("Dune")
        assertThat(detail.rows.single().valueFor("pages")).isEqualTo("412")
    }

    @Test
    fun `addRow posts the field map under data and returns the created row`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "row": { "id": "r9", "data": { "title": "New" } } }"""),
        )

        val result = repository.addRow("L1", mapOf("title" to "New", "blank" to ""))

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.valueFor("title")).isEqualTo("New")

        val request: RecordedRequest = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/data")
        val body = request.body.readUtf8()
        // The field map is nested under "data"; blanks are dropped.
        assertThat(body).contains("\"data\"")
        assertThat(body).contains("\"title\":\"New\"")
        assertThat(body).doesNotContain("blank")
    }

    @Test
    fun `deleteList evicts from cache on success`() = runTest(dispatcher) {
        dao.upsert(CachedListEntity("gone", "X", null, 0, null, false, null))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.deleteList("gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(dao.observeLists().first()).isEmpty()
    }
}

/** Minimal in-memory [ListDao] backed by a StateFlow, for JVM repository tests. */
private class FakeListDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())

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

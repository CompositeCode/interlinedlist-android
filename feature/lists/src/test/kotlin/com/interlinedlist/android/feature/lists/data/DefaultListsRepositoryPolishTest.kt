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
 * MockWebServer coverage for the Lists "completeness polish" endpoints: edit list
 * metadata (`PUT /api/lists/{id}`), folder rename/move/delete
 * (`PUT`/`DELETE /api/folders/{id}`), contributors parse
 * (`GET /api/lists/{id}/contributors`), and single-row fetch
 * (`GET /api/lists/{id}/data/{rowId}`). Confirmed request/response shapes against
 * the live OpenAPI spec and the web app handlers; no live writes are performed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryPolishTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
    private lateinit var dao: FakePolishDao
    private lateinit var repository: DefaultListsRepository

    // Mirrors the app's shared Json (explicit nulls off, coerce defaults on).
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
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
        dao = FakePolishDao()
        repository = DefaultListsRepository(api, dao, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    // --- Edit list metadata -----------------------------------------------

    @Test
    fun `updateList sends only the changed fields and caches the echoed summary`() = runTest(dispatcher) {
        dao.upsert(CachedListEntity("L1", "Old", "Old desc", 3, null, false, null))
        // The web handler wraps the updated list under `data`.
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "message": "List updated successfully",
                  "data": { "id": "L1", "title": "Reading", "description": "Books", "itemCount": 3, "isPublic": true }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.updateList("L1", title = "Reading", description = "Books", isPublic = true)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val summary = (result as ApiResult.Success).data
        assertThat(summary.title).isEqualTo("Reading")
        assertThat(summary.description).isEqualTo("Books")
        assertThat(summary.isPublic).isTrue()
        // Cache reflects the update (offline-first source of truth).
        assertThat(dao.observeLists().first().single().title).isEqualTo("Reading")

        val request: RecordedRequest = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/lists/L1")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"title\":\"Reading\"")
        assertThat(body).contains("\"description\":\"Books\"")
        assertThat(body).contains("\"isPublic\":true")
        // Untouched fields (folderId) are dropped by the shared Json, not sent as null.
        assertThat(body).doesNotContain("folderId")
    }

    @Test
    fun `updateList maps a 404 to NotFound`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(404).setBody("""{ "error": "List not found" }"""),
        )

        val result = repository.updateList("gone", title = "X")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    // --- Folder rename / move / delete ------------------------------------

    @Test
    fun `updateFolder renames and parses the folder envelope`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "message": "Folder updated successfully", "folder": { "id": "f1", "name": "Archive", "parentId": null } }""",
            ),
        )

        val result = repository.updateFolder("f1", name = "Archive")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val folder = (result as ApiResult.Success).data
        assertThat(folder.id).isEqualTo("f1")
        assertThat(folder.name).isEqualTo("Archive")
        assertThat(folder.parentId).isNull()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/folders/f1")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"name\":\"Archive\"")
        // A pure rename does not resend the parent.
        assertThat(body).doesNotContain("parentId")
    }

    @Test
    fun `updateFolder moves under a new parent`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "folder": { "id": "f2", "name": "Sub", "parentId": "p1" } }""",
            ),
        )

        val result = repository.updateFolder("f2", parentId = "p1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.parentId).isEqualTo("p1")

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"parentId\":\"p1\"")
        assertThat(body).doesNotContain("\"name\"")
    }

    @Test
    fun `deleteFolder issues a DELETE and succeeds`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "message": "Folder deleted successfully" }"""))

        val result = repository.deleteFolder("f9")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/folders/f9")
    }

    // --- Contributors ------------------------------------------------------

    @Test
    fun `getContributors parses the ranked contributor list`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "contributors": [
                    { "id": "u1", "username": "ada", "displayName": "Ada Lovelace", "avatar": "https://a/1.png",
                      "addedCount": 5, "editedCount": 2, "score": 7 },
                    { "id": "u2", "username": "grace", "displayName": null, "avatar": null,
                      "addedCount": 1, "editedCount": 0, "score": 1 }
                  ],
                  "totalContributors": 2
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getContributors("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val contributors = (result as ApiResult.Success).data
        assertThat(contributors.map { it.userId }).containsExactly("u1", "u2").inOrder()
        assertThat(contributors[0].label).isEqualTo("Ada Lovelace")
        assertThat(contributors[0].avatarUrl).isEqualTo("https://a/1.png")
        assertThat(contributors[0].score).isEqualTo(7)
        // Missing displayName falls back to the username for the label.
        assertThat(contributors[1].label).isEqualTo("grace")
        assertThat(contributors[1].avatarUrl).isNull()

        assertThat(server.takeRequest().path).isEqualTo("/api/lists/L1/contributors")
    }

    @Test
    fun `getContributors tolerates an empty contributor set`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "contributors": [], "totalContributors": 0 }"""))

        val result = repository.getContributors("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data).isEmpty()
    }

    // --- Single-row fetch --------------------------------------------------

    @Test
    fun `getRow parses the wrapped single-row envelope`() = runTest(dispatcher) {
        // The web handler wraps the row under `data`.
        server.enqueue(
            MockResponse().setBody(
                """{ "data": { "id": "r1", "data": { "title": "Dune", "pages": 412 } } }""",
            ),
        )

        val result = repository.getRow("L1", "r1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val row = (result as ApiResult.Success).data
        assertThat(row.id).isEqualTo("r1")
        assertThat(row.valueFor("title")).isEqualTo("Dune")
        assertThat(row.valueFor("pages")).isEqualTo("412")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/lists/L1/data/r1")
    }

    @Test
    fun `getRow maps a 404 to NotFound`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{ "error": "Row not found", "code": "not_found" }"""),
        )

        val result = repository.getRow("L1", "missing")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }
}

/** In-memory [ListDao] backed by a StateFlow, for JVM repository tests. */
private class FakePolishDao : ListDao {
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

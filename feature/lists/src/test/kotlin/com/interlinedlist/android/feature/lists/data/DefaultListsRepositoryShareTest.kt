package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.interlinedlist.android.feature.lists.domain.ShareRole
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
 * MockWebServer coverage for the list sharing endpoints: list/create/revoke share
 * links, "shared with me" (watching) parse incl. the per-list role, resolve a
 * token to a read-only preview, and claim access.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryShareTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
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
        repository = DefaultListsRepository(api, FakeShareDao(), json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `getShareLinks parses the shareLinks envelope and maps roles`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "shareLinks": [
                    { "id": "s1", "listId": "L1", "token": "tok-view", "role": "view", "createdAt": "2026-01-01" },
                    { "id": "s2", "listId": "L1", "token": "tok-edit", "role": "edit", "revokedAt": null }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getShareLinks("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val links = (result as ApiResult.Success).data
        assertThat(links.map { it.token }).containsExactly("tok-view", "tok-edit").inOrder()
        assertThat(links[0].role).isEqualTo(ShareRole.VIEW)
        assertThat(links[1].role).isEqualTo(ShareRole.EDIT)
        assertThat(links[1].isActive).isTrue()
        assertThat(links[0].url()).isEqualTo("https://interlinedlist.com/lists/shared/tok-view")

        val request: RecordedRequest = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/lists/L1/share-links")
    }

    @Test
    fun `createShareLink posts the chosen role and returns the created link`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "shareLink": { "id": "s9", "token": "new-tok", "role": "admin", "createdAt": "2026-02-02" } }""",
            ),
        )

        val result = repository.createShareLink("L1", ShareRole.ADMIN)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val link = (result as ApiResult.Success).data
        assertThat(link.token).isEqualTo("new-tok")
        assertThat(link.role).isEqualTo(ShareRole.ADMIN)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/L1/share-links")
        assertThat(request.body.readUtf8()).contains("\"role\":\"admin\"")
    }

    @Test
    fun `createShareLink tolerates a bare wrapped link body`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "id": "s7", "token": "bare-tok", "role": "edit" }"""),
        )

        val result = repository.createShareLink("L1", ShareRole.EDIT)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.token).isEqualTo("bare-tok")
    }

    @Test
    fun `revokeShareLink deletes by token`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.revokeShareLink("L1", "tok-gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/lists/L1/share-links/tok-gone")
    }

    @Test
    fun `getSharedWithMe parses watching lists with owner and role`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "lists": [
                    { "id": "w1", "title": "Shows", "description": null, "isPublic": true,
                      "user": { "id": "u1", "username": "adron", "displayName": "Adron Hall" },
                      "role": "collaborator" },
                    { "id": "w2", "title": "Videos", "isPublic": true,
                      "user": { "id": "u1", "username": "adron", "displayName": "Adron Hall" },
                      "role": "watcher" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getSharedWithMe()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val shared = (result as ApiResult.Success).data
        assertThat(shared.map { it.id }).containsExactly("w1", "w2").inOrder()
        assertThat(shared[0].ownerName).isEqualTo("Adron Hall")
        // "collaborator" maps to EDIT; "watcher" is read-only -> VIEW.
        assertThat(shared[0].role).isEqualTo(ShareRole.EDIT)
        assertThat(shared[1].role).isEqualTo(ShareRole.VIEW)

        assertThat(server.takeRequest().path).isEqualTo("/api/lists/watching")
    }

    @Test
    fun `resolveSharedList maps preview metadata rows and role`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "id": "L5", "title": "Public Reading", "description": "Books",
                  "role": "edit",
                  "user": { "id": "u2", "username": "grace", "displayName": "Grace H" },
                  "rows": [ { "id": "r1", "data": { "title": "Dune" } } ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.resolveSharedList("shared-tok")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val res = (result as ApiResult.Success).data
        assertThat(res.token).isEqualTo("shared-tok")
        assertThat(res.listId).isEqualTo("L5")
        assertThat(res.title).isEqualTo("Public Reading")
        assertThat(res.ownerName).isEqualTo("Grace H")
        assertThat(res.role).isEqualTo(ShareRole.EDIT)
        assertThat(res.canClaim).isTrue()
        assertThat(res.rows.single().valueFor("title")).isEqualTo("Dune")

        assertThat(server.takeRequest().path).isEqualTo("/api/lists/shared/shared-tok")
    }

    @Test
    fun `resolveSharedList maps a 404 to NotFound`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{ "error": "Share link not found, expired, or revoked", "code": "not_found" }"""),
        )

        val result = repository.resolveSharedList("dead-tok")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `claimSharedList posts to the shared token`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = repository.claimSharedList("claim-tok")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/shared/claim-tok")
    }
}

/** Minimal no-op [ListDao] — the share endpoints don't touch Room. */
private class FakeShareDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())
    override fun observeLists(): Flow<List<CachedListEntity>> = state
    override suspend fun upsertAll(lists: List<CachedListEntity>) {}
    override suspend fun upsert(list: CachedListEntity) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun clear() {}
}

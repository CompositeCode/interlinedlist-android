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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * GitHub-backed lists at the repository layer, driven through the real
 * Retrofit/OkHttp stack so the assertions are about the bytes that reach the API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryGithubTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ListsApi
    private lateinit var dao: FakeGithubDao
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
        dao = FakeGithubDao()
        repository = DefaultListsRepository(api, dao, json, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `createGithubList sends source, githubRepo and githubSource`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """
                { "message": "created", "data": {
                    "id": "lst_gh01", "title": "Hello-World", "source": "github",
                    "githubRepo": "octocat/Hello-World", "githubRepoPrivate": false } }
                """.trimIndent(),
            ),
        )

        val result = repository.createGithubList(
            repo = "octocat/Hello-World",
            title = "Hello-World",
            isPublic = false,
        )

        val body = server.takeRequest().body.readUtf8().let(json::parseToJsonElement).jsonObject
        assertThat(body["source"]).isEqualTo(JsonPrimitive("github"))
        assertThat(body["githubRepo"]).isEqualTo(JsonPrimitive("octocat/Hello-World"))
        // Rows mirror issues — the only mapping the API documents.
        assertThat(body["githubSource"]).isEqualTo(JsonPrimitive("issues"))
        assertThat(body["title"]).isEqualTo(JsonPrimitive("Hello-World"))

        val summary = (result as ApiResult.Success).data
        assertThat(summary.source).isEqualTo(ListSource.GITHUB)
        assertThat(summary.githubRepo).isEqualTo("octocat/Hello-World")
        assertThat(summary.isGithubBacked).isTrue()
    }

    @Test
    fun `createGithubList falls back to the repo name when no title is given`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "data": { "id": "lst_gh01", "title": "Hello-World" } }"""),
        )

        repository.createGithubList(repo = "octocat/Hello-World", title = "  ")

        val body = server.takeRequest().body.readUtf8().let(json::parseToJsonElement).jsonObject
        assertThat(body["title"]).isEqualTo(JsonPrimitive("Hello-World"))
    }

    @Test
    fun `createGithubList refuses a malformed repo without spending a request`() = runTest(dispatcher) {
        // The server's own rule: `githubRepo … (format: owner/repo)`.
        val result = repository.createGithubList(repo = "nosuchslash", title = "Issues")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `an ordinary createList still sends no github fields`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody("""{ "data": { "id": "lst_1", "title": "Books" } }"""),
        )

        repository.createList(title = "Books")

        val body = server.takeRequest().body.readUtf8().let(json::parseToJsonElement).jsonObject
        assertThat(body.keys).containsNoneOf("githubRepo", "githubSource", "source")
    }

    @Test
    fun `a list response carries source, repo and repository visibility`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                { "lists": [
                    { "id": "lst_gh01", "title": "Repo issues", "source": "github",
                      "githubRepo": "octocat/Hello-World", "githubRepoPrivate": true },
                    { "id": "lst_1", "title": "Books", "source": "local" }
                ], "pagination": { "total": 2, "limit": 20, "offset": 0, "hasMore": false } }
                """.trimIndent(),
            ),
        )

        val page = (repository.refreshLists() as ApiResult.Success).data

        val gh = page.items.first()
        assertThat(gh.source).isEqualTo(ListSource.GITHUB)
        assertThat(gh.githubRepo).isEqualTo("octocat/Hello-World")
        assertThat(gh.githubRepoPrivate).isTrue()
        assertThat(gh.isGithubBacked).isTrue()

        val local = page.items[1]
        assertThat(local.source).isEqualTo(ListSource.LOCAL)
        assertThat(local.isGithubBacked).isFalse()
        // Visibility the server never recorded stays unknown, not "public".
        assertThat(local.githubRepoPrivate).isNull()

        // And it all survives the offline cache, so an offline open still shows the tag.
        val cached = ListMapper.summaryFromEntity(dao.cached.first { it.id == "lst_gh01" })
        assertThat(cached.isGithubBacked).isTrue()
        assertThat(cached.githubRepoPrivate).isTrue()
    }

    @Test
    fun `getListDetail reads a GitHub list's fixed columns from the inlined properties`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    { "data": {
                        "id": "lst_gh01", "title": "Repo issues", "source": "github",
                        "githubRepo": "octocat/Hello-World", "githubRepoPrivate": true,
                        "properties": [
                          { "id": "gh_number", "propertyKey": "number", "propertyName": "Issue #",
                            "propertyType": "number", "isReadOnly": true },
                          { "id": "gh_title", "propertyKey": "title", "propertyName": "Title",
                            "propertyType": "text", "isRequired": true, "isReadOnly": false },
                          { "id": "gh_state", "propertyKey": "state", "propertyName": "State",
                            "propertyType": "select", "isReadOnly": false,
                            "validationRules": { "options": ["open", "closed"] } }
                        ] } }
                    """.trimIndent(),
                ),
            )
            // A GitHub list has no stored schema: the dedicated endpoint says nothing.
            server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "not found" }"""))
            server.enqueue(MockResponse().setBody("""{ "data": [] }"""))

            val detail = (repository.getListDetail("lst_gh01") as ApiResult.Success).data

            assertThat(detail.schema.fields.map { it.key })
                .containsExactly("number", "title", "state").inOrder()
            val number = detail.schema.fields.first()
            assertThat(number.label).isEqualTo("Issue #")
            // GitHub assigns it, so the row form must not offer it as an input.
            assertThat(number.readOnly).isTrue()
            assertThat(detail.schema.fields[1].readOnly).isFalse()
            assertThat(detail.schema.fields[1].required).isTrue()
            assertThat(detail.schema.fields[2].options).containsExactly("open", "closed").inOrder()
            assertThat(detail.summary.isGithubBacked).isTrue()
        }

    @Test
    fun `refreshGithubList posts to the refresh route and reports what changed`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "success": true, "added": 2, "updated": 1, "removed": 0 }"""),
        )

        val result = (repository.refreshGithubList("lst_gh01") as ApiResult.Success).data

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/lists/lst_gh01/refresh")
        assertThat(result.summary).isEqualTo("2 added, 1 updated")
    }

    @Test
    fun `updateList can re-parent a list`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """{ "data": { "id": "lst_gh01", "title": "Repo issues", "parentId": "lst_parent" } }""",
            ),
        )

        val result = repository.updateList(id = "lst_gh01", parentId = "lst_parent")

        val body = server.takeRequest().body.readUtf8().let(json::parseToJsonElement).jsonObject
        // Only the parent moves; nothing the caller left alone is asserted.
        assertThat(body.keys).containsExactly("parentId")
        assertThat((result as ApiResult.Success).data.parentId).isEqualTo("lst_parent")
    }
}

/** In-memory [ListDao] backed by a StateFlow, for JVM repository tests. */
private class FakeGithubDao : ListDao {
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

    override suspend fun replaceAll(lists: List<CachedListEntity>) {
        state.value = lists
    }
}

package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDocumentsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: DocumentsApi
    private lateinit var documentDao: FakeDocumentDao
    private lateinit var folderDao: FakeFolderDao
    private lateinit var repository: DefaultDocumentsRepository

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = object : DispatcherProvider {
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val main: CoroutineDispatcher = testDispatcher
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(DocumentsApi::class.java)
        documentDao = FakeDocumentDao()
        folderDao = FakeFolderDao()
        repository = DefaultDocumentsRepository(api, documentDao, folderDao, json, dispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refreshDocuments caches the page and reports pagination`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    { "id": "1", "title": "First", "content": "hello world", "isPublic": false },
                    { "id": "2", "title": "Second", "content": "more text" }
                  ],
                  "pagination": { "total": 40, "limit": 20, "offset": 0, "hasMore": true }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshDocuments(folderId = null)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val pagination = (result as ApiResult.Success).data
        assertThat(pagination.hasMore).isTrue()
        assertThat(pagination.total).isEqualTo(40)

        val cached = repository.observeDocuments(null).first()
        assertThat(cached.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(cached.first().title).isEqualTo("First")
    }

    @Test
    fun `refreshDocuments maps a 403 subscription error to SubscriptionRequired`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{ "error": "This feature requires an active subscription." }"""),
        )

        val result = repository.refreshDocuments(folderId = null)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(com.interlinedlist.android.core.common.result.AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `createDocument posts the body and caches the created document`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "document": { "id": "new1", "title": "Fresh", "content": "body", "isPublic": false } }""",
            ),
        )

        val result = repository.createDocument("Fresh", "body", isPublic = false)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.id).isEqualTo("new1")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/documents")
        assertThat(recorded.body.readUtf8()).contains("\"title\":\"Fresh\"")

        assertThat(documentDao.getDocument("new1")).isNotNull()
    }

    @Test
    fun `getDocument detail parses a bare body and caches it`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "id": "d9", "title": "Detail", "content": "# Heading\n- item" }""",
            ),
        )

        val result = repository.refreshDocument("d9")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val doc = (result as ApiResult.Success).data
        assertThat(doc.title).isEqualTo("Detail")
        assertThat(doc.content).contains("# Heading")
        assertThat(documentDao.getDocument("d9")?.content).contains("# Heading")
    }

    @Test
    fun `updateDocument issues a PUT and updates the cache`() = runTest(testDispatcher) {
        // Seed a cached copy first.
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "id": "d1", "title": "Old", "content": "old" }"""))
        repository.refreshDocument("d1")
        server.takeRequest()

        server.enqueue(
            MockResponse().setResponseCode(200).setBody("""{ "id": "d1", "title": "New", "content": "new body" }"""),
        )

        val result = repository.updateDocument("d1", "New", "new body", isPublic = true, folderId = null)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("PUT")
        assertThat(recorded.path).isEqualTo("/api/documents/d1")
        assertThat(documentDao.getDocument("d1")?.title).isEqualTo("New")
        assertThat(documentDao.getDocument("d1")?.content).isEqualTo("new body")
    }

    @Test
    fun `deleteDocument issues a DELETE and removes the cached row`() = runTest(testDispatcher) {
        // Seed the cache directly through a create.
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "id": "gone", "title": "T", "content": "c" }"""))
        repository.createDocument("T", "c", isPublic = false)
        server.takeRequest()
        assertThat(documentDao.getDocument("gone")).isNotNull()

        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val result = repository.deleteDocument("gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        assertThat(documentDao.getDocument("gone")).isNull()
    }

    @Test
    fun `refreshFolders caches folders from the data envelope`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "f1", "name": "Work" }, { "id": "f2", "name": "Personal" } ] }""",
            ),
        )

        val result = repository.refreshFolders()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(folderDao.snapshot().map { it.name }).containsExactly("Work", "Personal").inOrder()
    }

    @Test
    fun `searchDocuments passes the query and maps results`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "s1", "title": "Match", "content": "found" } ] }""",
            ),
        )

        val result = repository.searchDocuments("found")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().title).isEqualTo("Match")
        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/documents/search?q=found")
    }

    @Test
    fun `getTemplates maps template documents`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "t1", "title": "Recipe", "content": "Ingredients" } ] }""",
            ),
        )

        val result = repository.getTemplates()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().title).isEqualTo("Recipe")
    }
}

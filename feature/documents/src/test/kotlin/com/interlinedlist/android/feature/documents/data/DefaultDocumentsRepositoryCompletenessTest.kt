package com.interlinedlist.android.feature.documents.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.documents.data.remote.DocumentsApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

/**
 * Repository coverage for the documents completeness polish: creating a document
 * directly inside a folder, and seeding the default templates (then refreshing).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDocumentsRepositoryCompletenessTest {

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
        repository = DefaultDocumentsRepository(
            api, documentDao, folderDao, FakePendingOpDao(), FakeSyncMetaDao(), json, dispatchers,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    // --- Create document in folder ----------------------------------------

    @Test
    fun `createDocumentInFolder posts to the folder endpoint and caches the doc under that folder`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{ "document": { "id": "nd1", "title": "Notes", "content": "hello", "folderId": "f1" } }""",
                ),
            )

            val result = repository.createDocumentInFolder(
                folderId = "f1",
                title = "Notes",
                content = "hello",
                isPublic = false,
            )

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val created = (result as ApiResult.Success).data
            assertThat(created.id).isEqualTo("nd1")
            assertThat(created.folderId).isEqualTo("f1")

            val recorded = server.takeRequest()
            assertThat(recorded.method).isEqualTo("POST")
            assertThat(recorded.path).isEqualTo("/api/documents/folders/f1/documents")
            val body = recorded.body.readUtf8()
            assertThat(body).contains("\"title\":\"Notes\"")
            assertThat(body).contains("\"content\":\"hello\"")

            // Only one call — no follow-up move — and the cache reflects the folder.
            assertThat(server.requestCount).isEqualTo(1)
            assertThat(documentDao.getDocument("nd1")?.folderId).isEqualTo("f1")
        }

    @Test
    fun `createDocumentInFolder forces the target folder even when the response omits it`() =
        runTest(testDispatcher) {
            // Server echoes a bare doc without folderId (create endpoints may wrap in data).
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{ "data": { "id": "nd2", "title": "Filed" } }""",
                ),
            )

            val result = repository.createDocumentInFolder("f9", "Filed", "", isPublic = false)

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat((result as ApiResult.Success).data.folderId).isEqualTo("f9")
            assertThat(documentDao.getDocument("nd2")?.folderId).isEqualTo("f9")
        }

    @Test
    fun `createDocumentInFolder maps a 403 subscription error`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{ "error": "This feature requires an active subscription." }"""),
        )

        val result = repository.createDocumentInFolder("f1", "T", "c", isPublic = false)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    // --- Seed default templates -------------------------------------------

    @Test
    fun `seedDefaultTemplates posts to the seed endpoint then refreshes and parses templates`() =
        runTest(testDispatcher) {
            // 1) POST seed-defaults.
            server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))
            // 2) GET templates (live shape uses a `templates` array).
            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "folderCreated": true,
                      "templatesFolderId": "tf",
                      "templates": [
                        { "id": "t1", "title": "Recipe" },
                        { "id": "t2", "title": "Social Media Campaign" }
                      ]
                    }
                    """.trimIndent(),
                ),
            )

            val result = repository.seedDefaultTemplates()

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            assertThat((result as ApiResult.Success).data.map { it.title })
                .containsExactly("Recipe", "Social Media Campaign")

            val seed = server.takeRequest()
            assertThat(seed.method).isEqualTo("POST")
            assertThat(seed.path).isEqualTo("/api/documents/templates/seed-defaults")

            val refresh = server.takeRequest()
            assertThat(refresh.method).isEqualTo("GET")
            assertThat(refresh.path).isEqualTo("/api/documents/templates")
        }

    @Test
    fun `seedDefaultTemplates surfaces the seed failure without refreshing`() =
        runTest(testDispatcher) {
            server.enqueue(
                MockResponse().setResponseCode(403)
                    .setBody("""{ "error": "This feature requires an active subscription." }"""),
            )

            val result = repository.seedDefaultTemplates()

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat((result as ApiResult.Failure).error)
                .isInstanceOf(AppError.SubscriptionRequired::class.java)
            // Only the seed POST was attempted — no templates GET followed.
            assertThat(server.requestCount).isEqualTo(1)
        }

    @Test
    fun `getTemplates parses the live templates array key`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "templates": [ { "id": "t1", "title": "Recipe" } ] }""",
            ),
        )

        val result = repository.getTemplates()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().title).isEqualTo("Recipe")
    }
}

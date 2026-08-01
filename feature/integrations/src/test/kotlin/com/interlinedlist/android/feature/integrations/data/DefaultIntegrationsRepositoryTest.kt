package com.interlinedlist.android.feature.integrations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.data.remote.IntegrationsApi
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ExportType
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.File
import java.nio.file.Files

/**
 * Repository behaviour against a real HTTP stack (Retrofit + OkHttp) driven by
 * MockWebServer, writing to a temp directory in place of the app cache. Verifies
 * that CSV bytes are streamed to disk, provider statuses are mapped, per-provider
 * failures degrade to "not connected", and limits are mapped/normalised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultIntegrationsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: IntegrationsApi
    private lateinit var repository: DefaultIntegrationsRepository
    private lateinit var tempDir: File

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
            .create(IntegrationsApi::class.java)
        tempDir = Files.createTempDirectory("exports-test").toFile()
        val fileStore = object : ExportFileStore {
            override fun exportsDir(): File = File(tempDir, "exports")
        }
        repository = DefaultIntegrationsRepository(api, fileStore, json, testDispatchers)
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `downloadExport requests the right path and writes the CSV bytes to disk`() = runTest(dispatcher) {
        val csv = "id,name\n1,Ada\n2,Adron\n"
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/csv")
                .setBody(csv),
        )

        val result = repository.downloadExport(ExportType.FOLLOWS)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val file = (result as ApiResult.Success).data
        // Request went to the follows export endpoint.
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/exports/follows")
        // The exact bytes were surfaced to disk.
        assertThat(file.name).isEqualTo("follows.csv")
        assertThat(file.readText()).isEqualTo(csv)
    }

    @Test
    fun `downloadExport maps a subscription 403 to SubscriptionRequired`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{ "error": "This feature requires an active subscription" }"""),
        )

        val result = repository.downloadExport(ExportType.LISTS)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `getConnectedAccounts maps each provider status and degrades failures to not-connected`() =
        runTest(dispatcher) {
            // Providers are queried in enum order: github, linkedin, bluesky, mastodon, twitter.
            server.enqueue(MockResponse().setBody("""{ "connected": true, "handle": "@adron" }"""))
            server.enqueue(MockResponse().setBody("""{ "connected": false }"""))
            server.enqueue(MockResponse().setBody("""{ "username": "adron.bsky.social" }"""))
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom")) // mastodon fails
            server.enqueue(MockResponse().setBody("""{ "connected": false }"""))

            val accounts = repository.getConnectedAccounts()

            assertThat(accounts.map { it.provider }).containsExactlyElementsIn(
                ConnectedAccount.Provider.entries,
            )
            val byProvider = accounts.associateBy { it.provider }
            assertThat(byProvider[ConnectedAccount.Provider.GITHUB]!!.isConnected).isTrue()
            assertThat(byProvider[ConnectedAccount.Provider.GITHUB]!!.handle).isEqualTo("@adron")
            assertThat(byProvider[ConnectedAccount.Provider.LINKEDIN]!!.isConnected).isFalse()
            // A present username without an explicit flag counts as connected.
            assertThat(byProvider[ConnectedAccount.Provider.BLUESKY]!!.isConnected).isTrue()
            assertThat(byProvider[ConnectedAccount.Provider.BLUESKY]!!.handle).isEqualTo("adron.bsky.social")
            // A 500 degrades to not-connected rather than throwing.
            assertThat(byProvider[ConnectedAccount.Provider.MASTODON]!!.isConnected).isFalse()
        }

    @Test
    fun `getConnectedAccounts hits each provider status path`() = runTest(dispatcher) {
        repeat(ConnectedAccount.Provider.entries.size) {
            server.enqueue(MockResponse().setBody("""{ "connected": false }"""))
        }

        repository.getConnectedAccounts()

        val paths = buildList {
            repeat(ConnectedAccount.Provider.entries.size) { add(server.takeRequest().path) }
        }
        assertThat(paths).containsExactly(
            "/api/auth/github/status",
            "/api/auth/linkedin/status",
            "/api/auth/bluesky/status",
            "/api/auth/mastodon/status",
            "/api/auth/twitter/status",
        ).inOrder()
    }

    @Test
    fun `getLimits maps the plan and normalises entries`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "plan": "free",
                  "limits": {
                    "lists": { "used": 3, "max": 5 },
                    "listDataRows": { "used": 40, "limit": 100 },
                    "follows": { "used": 12 }
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getLimits()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val limits = (result as ApiResult.Success).data
        assertThat(limits.planName).isEqualTo("free")
        val byKey = limits.limits.associateBy { it.key }
        assertThat(byKey["lists"]!!.max).isEqualTo(5)
        assertThat(byKey["lists"]!!.used).isEqualTo(3)
        // `limit` is accepted as an alias for `max`.
        assertThat(byKey["listDataRows"]!!.max).isEqualTo(100)
        assertThat(byKey["listDataRows"]!!.label).isEqualTo("List data rows")
        // No ceiling means unlimited.
        assertThat(byKey["follows"]!!.isUnlimited).isTrue()
    }
}

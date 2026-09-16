package com.interlinedlist.android.feature.integrations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.integrations.data.remote.IntegrationsApi
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import com.interlinedlist.android.feature.integrations.domain.ConnectionHealth
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
import java.time.Instant

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
            // Identities are read first, then providers in enum order:
            // github, linkedin, bluesky, mastodon, twitter.
            server.enqueue(MockResponse().setBody("""{ "identities": [] }"""))
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
    fun `getConnectedAccounts reads the identities payload then each provider status path`() =
        runTest(dispatcher) {
            server.enqueue(MockResponse().setBody("""{ "identities": [] }"""))
            repeat(ConnectedAccount.Provider.entries.size) {
                server.enqueue(MockResponse().setBody("""{ "connected": false }"""))
            }

            repository.getConnectedAccounts()

            val paths = buildList {
                repeat(ConnectedAccount.Provider.entries.size + 1) { add(server.takeRequest().path) }
            }
            assertThat(paths).containsExactly(
                "/api/user/identities",
                "/api/auth/github/status",
                "/api/auth/linkedin/status",
                "/api/auth/bluesky/status",
                "/api/auth/mastodon/status",
                "/api/auth/twitter/status",
            ).inOrder()
        }

    @Test
    fun `getConnectedAccounts merges identities so linked rows carry health and an unlink key`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    { "identities": [
                      {
                        "id": "i1",
                        "provider": "linkedin",
                        "providerUsername": "Adron Hall",
                        "connectedAt": "2026-05-01T12:00:00.000Z",
                        "lastVerifiedAt": "2026-07-01T12:00:00.000Z"
                      },
                      {
                        "id": "i2",
                        "provider": "mastodon:techhub.social",
                        "providerUsername": "adron@techhub.social",
                        "connectedAt": "2026-06-01T12:00:00.000Z"
                      },
                      {
                        "id": "i3",
                        "provider": "mastodon:mastodon.social",
                        "providerUsername": "adron@mastodon.social",
                        "connectedAt": "2026-06-02T12:00:00.000Z"
                      }
                    ] }
                    """.trimIndent(),
                ),
            )
            repeat(ConnectedAccount.Provider.entries.size) {
                server.enqueue(MockResponse().setBody("""{ "connected": false }"""))
            }

            val accounts = repository.getConnectedAccounts()

            val linkedIn = accounts.single { it.provider == ConnectedAccount.Provider.LINKEDIN }
            // An identity record means linked, whatever the status endpoint claims — a
            // lapsed authorization shows up as health, not as "Not connected".
            assertThat(linkedIn.isConnected).isTrue()
            assertThat(linkedIn.identityProvider).isEqualTo("linkedin")
            assertThat(linkedIn.handle).isEqualTo("Adron Hall")
            assertThat(linkedIn.lastVerifiedAt).isEqualTo("2026-07-01T12:00:00.000Z")
            assertThat(linkedIn.healthAt(Instant.parse("2026-09-16T12:00:00Z")))
                .isEqualTo(ConnectionHealth.STALE)

            // Each Mastodon instance is its own row, so unlinking one can't take out the other.
            val mastodon = accounts.filter { it.provider == ConnectedAccount.Provider.MASTODON }
            assertThat(mastodon.map { it.identityProvider })
                .containsExactly("mastodon:techhub.social", "mastodon:mastodon.social")
            assertThat(mastodon.map { it.key }).containsNoDuplicates()
            assertThat(mastodon.first().healthAt(Instant.parse("2026-09-16T12:00:00Z")))
                .isEqualTo(ConnectionHealth.NEVER_VERIFIED)

            // Providers with no identity stay as status-only rows with nothing to unlink.
            val bluesky = accounts.single { it.provider == ConnectedAccount.Provider.BLUESKY }
            assertThat(bluesky.isLinked).isFalse()
        }

    @Test
    fun `getConnectedAccounts degrades to status-only rows when the identities read fails`() =
        runTest(dispatcher) {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            repeat(ConnectedAccount.Provider.entries.size) {
                server.enqueue(MockResponse().setBody("""{ "connected": true, "handle": "@adron" }"""))
            }

            val accounts = repository.getConnectedAccounts()

            assertThat(accounts).hasSize(ConnectedAccount.Provider.entries.size)
            assertThat(accounts.none { it.isLinked }).isTrue()
            assertThat(accounts.all { it.isConnected }).isTrue()
        }

    @Test
    fun `unlinkIdentity deletes with the provider as a query parameter`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "success": true }"""))

        val result = repository.unlinkIdentity("mastodon:techhub.social")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("DELETE")
        // The spec declares `provider` as a query parameter, not a body or path segment.
        assertThat(recorded.path).isEqualTo("/api/user/identities?provider=mastodon%3Atechhub.social")
        assertThat(recorded.body.size).isEqualTo(0)
    }

    @Test
    fun `a failed unlink returns the server's message`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{ "error": "Identity not found", "code": "not_found" }"""),
        )

        val result = repository.unlinkIdentity("linkedin")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.NotFound::class.java)
        assertThat(error.message).isEqualTo("Identity not found")
    }

    @Test
    fun `verifyIdentity posts the provider in the body`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "verified": true }"""))

        val result = repository.verifyIdentity("linkedin")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("POST")
        assertThat(recorded.path).isEqualTo("/api/user/identities/verify")
        assertThat(recorded.body.readUtf8()).isEqualTo("""{"provider":"linkedin"}""")
    }

    @Test
    fun `a failed verify returns the server's message`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{ "error": "LinkedIn account not linked", "code": "not_linked" }"""),
        )

        val result = repository.verifyIdentity("linkedin")

        assertThat((result as ApiResult.Failure).error.message)
            .isEqualTo("LinkedIn account not linked")
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

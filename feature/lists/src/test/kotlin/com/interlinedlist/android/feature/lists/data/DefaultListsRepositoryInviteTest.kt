package com.interlinedlist.android.feature.lists.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.feature.lists.data.local.CachedListEntity
import com.interlinedlist.android.feature.lists.data.local.ListDao
import com.interlinedlist.android.feature.lists.data.remote.ListsApi
import com.interlinedlist.android.feature.lists.domain.InviteRole
import com.interlinedlist.android.feature.lists.domain.InviteStatus
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
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.time.Instant

/**
 * MockWebServer coverage for the list email-invite endpoints: send, list and revoke,
 * the subscriber gate on sending (and its deliberate absence on revoking), and the
 * client-side email guard.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultListsRepositoryInviteTest {

    private lateinit var server: MockWebServer
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
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = DefaultListsRepository(
            retrofit.create(ListsApi::class.java),
            retrofit.create(InterlinedListApi::class.java),
            FakeInviteDao(),
            json,
            testDispatchers,
        )
    }

    @After
    fun tearDown() = server.shutdown()

    /** `GET /api/user` as the subscriber gate reads it. */
    private fun enqueueUser(customerStatus: String) = server.enqueue(
        MockResponse().setResponseCode(200).setBody(
            """{ "user": { "id": "u1", "username": "me", "email": "me@x.io", "customerStatus": "$customerStatus" } }""",
        ),
    )

    // --- Listing -----------------------------------------------------------

    @Test
    fun `getInvites parses the documented list shape`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "invites": [
                    {
                      "email": "friend@example.com", "role": "collaborator", "expiresAt": null,
                      "accepted": false, "createdAt": "2026-06-11T09:00:00.000Z", "token": "tok-a"
                    },
                    {
                      "email": "old@example.com", "role": "manager",
                      "expiresAt": "2026-01-01T00:00:00.000Z", "accepted": true,
                      "createdAt": "2025-12-01T09:00:00.000Z", "token": "tok-b"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getInvites("L1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val invites = (result as ApiResult.Success).data
        assertThat(invites.map { it.email }).containsExactly("friend@example.com", "old@example.com").inOrder()
        assertThat(invites[0].role).isEqualTo(InviteRole.EDITOR)
        assertThat(invites[0].token).isEqualTo("tok-a")
        assertThat(invites[1].role).isEqualTo(InviteRole.ADMIN)
        assertThat(invites[1].accepted).isTrue()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api/lists/L1/invites")
    }

    @Test
    fun `getInvites derives pending accepted expired and revoked from the row`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "invites": [
                    { "email": "pending@x.io", "role": "watcher", "accepted": false, "token": "t1" },
                    { "email": "done@x.io", "role": "watcher", "accepted": true, "token": "t2" },
                    { "email": "late@x.io", "role": "watcher", "accepted": false,
                      "expiresAt": "2026-01-02T00:00:00.000Z", "token": "t3" },
                    { "email": "dead@x.io", "role": "watcher", "accepted": false,
                      "revokedAt": "2026-02-02T00:00:00.000Z", "token": "t4" }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val now = Instant.parse("2026-06-01T00:00:00Z")
        val invites = (repository.getInvites("L1") as ApiResult.Success).data

        assertThat(invites.map { it.statusAt(now) }).containsExactly(
            InviteStatus.PENDING,
            InviteStatus.ACCEPTED,
            InviteStatus.EXPIRED,
            InviteStatus.REVOKED,
        ).inOrder()
        // An expiry still in the future is pending, not expired.
        assertThat(invites[2].statusAt(Instant.parse("2026-01-01T00:00:00Z")))
            .isEqualTo(InviteStatus.PENDING)
    }

    @Test
    fun `getInvites is not subscriber-gated and issues no current-user lookup`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "invites": [] }"""))

        assertThat(repository.getInvites("L1")).isInstanceOf(ApiResult.Success::class.java)

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/api/lists/L1/invites")
    }

    // --- Sending -----------------------------------------------------------

    @Test
    fun `sendInvite posts the normalised email and role, and recovers the token from the url`() =
        runTest(dispatcher) {
            enqueueUser("subscriber")
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "email": "friend@example.com", "role": "collaborator", "expiresAt": null,
                      "url": "https://interlinedlist.com/lists/invite/xN3v9Qk"
                    }
                    """.trimIndent(),
                ),
            )

            val result = repository.sendInvite("L1", "  Friend@Example.COM ", InviteRole.EDITOR)

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val invite = (result as ApiResult.Success).data
            assertThat(invite.email).isEqualTo("friend@example.com")
            assertThat(invite.role).isEqualTo(InviteRole.EDITOR)
            // The 201 carries no `token` — it must be recovered so the row can be revoked.
            assertThat(invite.token).isEqualTo("xN3v9Qk")
            assertThat(invite.statusAt(Instant.parse("2026-06-01T00:00:00Z"))).isEqualTo(InviteStatus.PENDING)

            assertThat(server.takeRequest().path).isEqualTo("/api/user")
            val post = server.takeRequest()
            assertThat(post.method).isEqualTo("POST")
            assertThat(post.path).isEqualTo("/api/lists/L1/invites")
            val body = post.body.readUtf8()
            assertThat(body).contains("\"email\":\"friend@example.com\"")
            assertThat(body).contains("\"role\":\"collaborator\"")
        }

    @Test
    fun `sendInvite tolerates a wrapped invite envelope`() = runTest(dispatcher) {
        enqueueUser("subscriber:annual")
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "invite": { "email": "a@b.io", "role": "manager", "token": "tok-w" } }""",
            ),
        )

        val result = repository.sendInvite("L1", "a@b.io", InviteRole.ADMIN)

        val invite = (result as ApiResult.Success).data
        assertThat(invite.token).isEqualTo("tok-w")
        assertThat(invite.role).isEqualTo(InviteRole.ADMIN)
    }

    @Test
    fun `a free account cannot send an invite and issues no write`() = runTest(dispatcher) {
        enqueueUser("free")

        val result = repository.sendInvite("L1", "friend@example.com", InviteRole.VIEWER)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
        assertThat(result.error.message).isEqualTo("Subscribe to invite people to lists.")

        // Only the current-user lookup happened: no POST was ever issued.
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `an invalid email is rejected before any request`() = runTest(dispatcher) {
        val result = repository.sendInvite("L1", "not-an-email", InviteRole.VIEWER)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message).isEqualTo("Enter a valid email address.")
        // Not even the subscriber lookup ran.
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `a server rejection is surfaced with its own message`() = runTest(dispatcher) {
        enqueueUser("subscriber")
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{ "error": "A valid email address is required", "code": "bad_request" }"""),
        )

        val result = repository.sendInvite("L1", "friend@example.com", InviteRole.VIEWER)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message).isEqualTo("A valid email address is required")
    }

    @Test
    fun `an unreadable subscription status still attempts the send`() = runTest(dispatcher) {
        // The gate fails open: the server stays the authority on the subscription.
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "email": "friend@example.com", "role": "watcher", "token": "tok-ok" }"""),
        )

        val result = repository.sendInvite("L1", "friend@example.com", InviteRole.VIEWER)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `an unrecognised customer status is treated as unknown, not free`() = runTest(dispatcher) {
        enqueueUser("subscriber:lifetime")
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "email": "friend@example.com", "role": "watcher", "token": "tok-ok" }"""),
        )

        val result = repository.sendInvite("L1", "friend@example.com", InviteRole.VIEWER)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.requestCount).isEqualTo(2)
    }

    // --- Revoking ----------------------------------------------------------

    @Test
    fun `revokeInvite is free - it deletes by token with no subscription lookup`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{ "revoked": true }"""))

        val result = repository.revokeInvite("L1", "tok-gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        // No `/api/user` lookup: a lapsed owner must always be able to revoke.
        assertThat(server.requestCount).isEqualTo(1)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/lists/L1/invites/tok-gone")
    }

    @Test
    fun `revokeInvite maps an unknown token to NotFound`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{ "error": "Invite not found", "code": "not_found" }"""),
        )

        val result = repository.revokeInvite("L1", "nope")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

}

/** The invite endpoints never touch the cache, so the DAO is inert. */
private class FakeInviteDao : ListDao {
    private val state = MutableStateFlow<List<CachedListEntity>>(emptyList())
    override fun observeLists(): Flow<List<CachedListEntity>> = state
    override suspend fun upsertAll(lists: List<CachedListEntity>) {}
    override suspend fun upsert(list: CachedListEntity) {}
    override suspend fun deleteById(id: String) {}
    override suspend fun clear() {}
}

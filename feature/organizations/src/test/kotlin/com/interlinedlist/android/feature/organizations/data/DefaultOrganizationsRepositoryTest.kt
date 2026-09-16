package com.interlinedlist.android.feature.organizations.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.organizations.data.local.CachedOrganizationEntity
import com.interlinedlist.android.feature.organizations.data.local.OrganizationDao
import com.interlinedlist.android.feature.organizations.data.remote.OrganizationsApi
import com.interlinedlist.android.feature.organizations.domain.OrgRole
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
 * mapping, offline-first caching, error normalisation, and the subscription gate
 * across the list/create/detail/update/delete/members endpoints.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultOrganizationsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OrganizationsApi
    private lateinit var dao: FakeOrganizationDao
    private lateinit var repository: DefaultOrganizationsRepository
    private lateinit var currentUserId: CurrentUserIdProvider

    /** The signed-in user the leave call must address; null models a lost session. */
    private var signedInUserId: String? = "me-1"

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
            .create(OrganizationsApi::class.java)
        dao = FakeOrganizationDao()
        currentUserId = CurrentUserIdProvider { signedInUserId }
        repository = DefaultOrganizationsRepository(api, dao, json, currentUserId, testDispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refreshOrganizations maps DTOs and replaces the Room cache`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": [
                    { "id": "1", "name": "Acme", "description": "Makers", "memberCount": 3 },
                    { "id": "2", "name": "Open", "membersCount": 5, "isPublic": "true" }
                  ],
                  "pagination": { "total": 2, "limit": 20, "offset": 0, "hasMore": false }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshOrganizations()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val page = (result as ApiResult.Success).data
        assertThat(page.items.map { it.id }).containsExactly("1", "2").inOrder()
        assertThat(page.items[0].memberCount).isEqualTo(3)
        assertThat(page.items[1].memberCount).isEqualTo(5)
        // "isPublic" arrived as the string "true" and was normalised to a boolean.
        assertThat(page.items[1].isPublic).isTrue()
        assertThat(page.hasMore).isFalse()
        // Room is the source of truth: the cache now streams the same two orgs.
        assertThat(dao.observeOrganizations().first().map { it.id }).containsExactly("1", "2")
    }

    @Test
    fun `refreshOrganizations maps a subscription 403 to SubscriptionRequired`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{ "error": "This feature requires an active subscription" }"""),
        )

        val result = repository.refreshOrganizations()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.SubscriptionRequired::class.java)
    }

    @Test
    fun `createOrganization posts name and caches the created org`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{ "organization": { "id": "o9", "name": "Newco", "isPublic": true, "memberCount": 1 } }"""),
        )

        val result = repository.createOrganization("Newco", description = "hi", isPublic = true)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val org = (result as ApiResult.Success).data
        assertThat(org.id).isEqualTo("o9")
        assertThat(org.isPublic).isTrue()
        // Cached on create so the index shows it immediately.
        assertThat(dao.observeOrganizations().first().map { it.id }).containsExactly("o9")

        val request: RecordedRequest = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/organizations")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"name\":\"Newco\"")
        // isPublic is serialised as a string per the API contract.
        assertThat(body).contains("\"isPublic\":\"true\"")
    }

    @Test
    fun `getOrganization maps the bare data envelope and caches it`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "data": { "id": "o1", "name": "Acme", "role": "owner" } }"""),
        )

        val result = repository.getOrganization("o1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val org = (result as ApiResult.Success).data
        assertThat(org.name).isEqualTo("Acme")
        assertThat(org.role).isEqualTo(OrgRole.OWNER)
        assertThat(dao.observeOrganizations().first().map { it.id }).containsExactly("o1")
    }

    @Test
    fun `updateOrganization sends the changed fields and refreshes the cache`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "organization": { "id": "o1", "name": "Renamed", "isPublic": false } }"""),
        )

        val result = repository.updateOrganization("o1", name = "Renamed", description = null, isPublic = false)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.name).isEqualTo("Renamed")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/organizations/o1")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"name\":\"Renamed\"")
        assertThat(body).contains("\"isPublic\":\"false\"")
    }

    @Test
    fun `deleteOrganization evicts from cache on success`() = runTest(dispatcher) {
        dao.upsert(CachedOrganizationEntity("gone", "X", null, null, false, 0, null, null))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.deleteOrganization("gone")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(dao.observeOrganizations().first()).isEmpty()
    }

    @Test
    fun `getMembers maps flattened and nested member rows`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "data": [
                    { "userId": "u1", "username": "ada", "role": "owner" },
                    { "role": "member", "user": { "id": "u2", "username": "grace" } }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val result = repository.getMembers("o1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val members = (result as ApiResult.Success).data
        assertThat(members.map { it.userId }).containsExactly("u1", "u2").inOrder()
        assertThat(members[0].role).isEqualTo(OrgRole.OWNER)
        assertThat(members[1].username).isEqualTo("grace")
    }

    @Test
    fun `addMember posts the user id and role`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("{}"))

        val result = repository.addMember("o1", userId = "u5", role = OrgRole.ADMIN)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/api/organizations/o1/members")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"userId\":\"u5\"")
        assertThat(body).contains("\"role\":\"admin\"")
    }

    @Test
    fun `updateMemberRole puts the new role for the member`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.updateMemberRole("o1", userId = "u5", role = OrgRole.OWNER)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("PUT")
        assertThat(request.path).isEqualTo("/api/organizations/o1/members/u5")
        assertThat(request.body.readUtf8()).contains("\"role\":\"owner\"")
    }

    @Test
    fun `joinOrganization posts the organization id and caches the new membership`() = runTest(dispatcher) {
        // Live contract: POST /api/user/organizations { "organizationId": "..." } -> 201.
        server.enqueue(
            MockResponse().setResponseCode(201).setBody(
                """{ "message": "Joined organization successfully", "membership": { "id": "m1", "role": "member" } }""",
            ),
        )
        // The repository re-reads the org so the cached row carries the new role.
        server.enqueue(
            MockResponse().setBody(
                """{ "organization": { "id": "o1", "name": "Bikey Life", "userRole": "member", "memberCount": 4 } }""",
            ),
        )

        val result = repository.joinOrganization("o1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val join = server.takeRequest()
        assertThat(join.method).isEqualTo("POST")
        assertThat(join.path).isEqualTo("/api/user/organizations")
        assertThat(join.body.readUtf8()).isEqualTo("""{"organizationId":"o1"}""")
        assertThat(server.takeRequest().path).isEqualTo("/api/organizations/o1")
        // Membership state is now cached: the index flips from "Join" to "Member".
        val cached = dao.observeOrganizations().first().single()
        assertThat(cached.role).isEqualTo("member")
        assertThat(cached.memberCount).isEqualTo(4)
    }

    @Test
    fun `joinOrganization surfaces the already-a-member conflict`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(409)
                .setBody("""{ "error": "User is already a member of this organization", "code": "conflict" }"""),
        )

        val result = repository.joinOrganization("o1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Conflict::class.java)
        assertThat(error.message).isEqualTo("User is already a member of this organization")
    }

    @Test
    fun `leaveOrganization deletes the signed-in user's membership and refreshes the org`() =
        runTest(dispatcher) {
            dao.upsert(CachedOrganizationEntity("o1", "Bikey Life", null, null, true, 3, "member", null))
            server.enqueue(MockResponse().setBody("""{ "message": "Member removed from organization successfully" }"""))
            server.enqueue(
                MockResponse().setBody(
                    """{ "organization": { "id": "o1", "name": "Bikey Life", "userRole": null, "memberCount": 2 } }""",
                ),
            )

            val result = repository.leaveOrganization("o1")

            assertThat(result).isInstanceOf(ApiResult.Success::class.java)
            val leave = server.takeRequest()
            assertThat(leave.method).isEqualTo("DELETE")
            // Leaving is removing your own membership row.
            assertThat(leave.path).isEqualTo("/api/organizations/o1/members/me-1")
            // The cached row keeps the org but drops the membership, so the UI offers "Join".
            val cached = dao.observeOrganizations().first().single()
            assertThat(cached.role).isNull()
            assertThat(cached.memberCount).isEqualTo(2)
        }

    @Test
    fun `leaveOrganization evicts an org that is no longer visible after leaving`() = runTest(dispatcher) {
        dao.upsert(CachedOrganizationEntity("o1", "Private Co", null, null, false, 3, "member", null))
        server.enqueue(MockResponse().setBody("""{ "message": "Member removed from organization successfully" }"""))
        // A private org is invisible once you are no longer a member.
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{ "error": "Forbidden", "code": "forbidden" }"""))

        val result = repository.leaveOrganization("o1")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(dao.observeOrganizations().first()).isEmpty()
    }

    @Test
    fun `leaveOrganization surfaces the last-owner rejection and keeps the membership cached`() =
        runTest(dispatcher) {
            dao.upsert(CachedOrganizationEntity("o1", "Acme", null, null, false, 1, "owner", null))
            // Captured live: DELETE .../members/{me} as the sole owner -> 400.
            server.enqueue(
                MockResponse().setResponseCode(400)
                    .setBody("""{ "error": "Cannot remove the last owner", "code": "bad_request" }"""),
            )

            val result = repository.leaveOrganization("o1")

            assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
            assertThat((result as ApiResult.Failure).error.message).isEqualTo("Cannot remove the last owner")
            // Still a member: the cache must not pretend the leave happened.
            assertThat(dao.observeOrganizations().first().single().role).isEqualTo("owner")
        }

    @Test
    fun `leaveOrganization fails without calling the API when the user id is unknown`() = runTest(dispatcher) {
        signedInUserId = null

        val result = repository.leaveOrganization("o1")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `getOrganization reads membership from the detail endpoint's userRole`() = runTest(dispatcher) {
        // The detail endpoint reports membership as `userRole` (null for non-members).
        server.enqueue(
            MockResponse().setBody("""{ "organization": { "id": "o1", "name": "Metals", "userRole": null } }"""),
        )

        val result = repository.getOrganization("o1")

        val org = (result as ApiResult.Success).data
        assertThat(org.role).isNull()
        assertThat(org.isMember).isFalse()
    }

    @Test
    fun `removeMember deletes the membership`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        val result = repository.removeMember("o1", userId = "u5")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path).isEqualTo("/api/organizations/o1/members/u5")
    }
}

/** Minimal in-memory [OrganizationDao] backed by a StateFlow, for JVM repository tests. */
private class FakeOrganizationDao : OrganizationDao {
    private val state = MutableStateFlow<List<CachedOrganizationEntity>>(emptyList())

    override fun observeOrganizations(): Flow<List<CachedOrganizationEntity>> = state

    override suspend fun upsertAll(orgs: List<CachedOrganizationEntity>) {
        val byId = state.value.associateBy { it.id }.toMutableMap()
        orgs.forEach { byId[it.id] = it }
        state.value = byId.values.toList()
    }

    override suspend fun upsert(org: CachedOrganizationEntity) = upsertAll(listOf(org))

    override suspend fun deleteById(id: String) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun clear() {
        state.value = emptyList()
    }
}

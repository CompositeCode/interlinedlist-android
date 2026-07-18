package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.CustomerStatus
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
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
class DefaultProfileRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProfileApi
    private lateinit var dao: FakeProfileDao
    private lateinit var repository: DefaultProfileRepository

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
        api = retrofit.create(ProfileApi::class.java)
        dao = FakeProfileDao()
        repository = DefaultProfileRepository(api, dao, json, dispatchers)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `refreshCurrentUser parses the wrapped user and caches it as current`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "user": {
                    "id": "u1",
                    "username": "adron",
                    "displayName": "Adron Hall",
                    "avatar": "https://cdn/av.png",
                    "bio": "Building things.",
                    "customerStatus": "subscriber:annual"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = repository.refreshCurrentUser()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.username).isEqualTo("adron")
        assertThat(user.avatarUrl).isEqualTo("https://cdn/av.png")
        assertThat(user.customerStatus).isEqualTo(CustomerStatus.SUBSCRIBER_ANNUAL)
        assertThat(user.isCurrentUser).isTrue()

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("GET")
        assertThat(recorded.path).isEqualTo("/api/user")

        val cached = repository.observeCurrentUser().first()
        assertThat(cached?.username).isEqualTo("adron")
    }

    @Test
    fun `refreshCurrentUser maps a 401 to Unauthorized`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{ "error": "Session expired." }"""))

        val result = repository.refreshCurrentUser()

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Unauthorized::class.java)
    }

    @Test
    fun `refreshUser fetches another user by username and caches without the current flag`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u2", "username": "ada", "displayName": "Ada Lovelace" } }""",
            ),
        )

        val result = repository.refreshUser("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.username).isEqualTo("ada")
        assertThat(user.isCurrentUser).isFalse()

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/users/ada")

        assertThat(repository.observeUser("ada").first()?.displayName).isEqualTo("Ada Lovelace")
        // A viewed user must not become the observed "current" user.
        assertThat(repository.observeCurrentUser().first()).isNull()
    }

    @Test
    fun `refreshUser maps a 404 to NotFound`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "error": "No such user" }"""))

        val result = repository.refreshUser("ghost")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `updateProfile PATCHes the fields and updates the cache`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "displayName": "New Name", "bio": "New bio" } }""",
            ),
        )

        val result = repository.updateProfile(displayName = "New Name", bio = "New bio")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.displayName).isEqualTo("New Name")

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("PATCH")
        assertThat(recorded.path).isEqualTo("/api/user/update")
        val body = recorded.body.readUtf8()
        assertThat(body).contains("\"displayName\":\"New Name\"")
        assertThat(body).contains("\"bio\":\"New bio\"")

        assertThat(repository.observeCurrentUser().first()?.displayName).isEqualTo("New Name")
    }

    @Test
    fun `updateProfile re-fetches when the server echoes a thin body`() = runTest(testDispatcher) {
        // PATCH returns nothing useful...
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        // ...so the repo re-fetches the full user.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "displayName": "Fresh", "bio": "b" } }""",
            ),
        )

        val result = repository.updateProfile(displayName = "Fresh", bio = "b")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.displayName).isEqualTo("Fresh")

        assertThat(server.takeRequest().path).isEqualTo("/api/user/update")
        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `setAvatarFromUrl posts the url then refreshes the cached user`() = runTest(testDispatcher) {
        // Avatar endpoint returns just a URL...
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "avatarUrl": "https://cdn/new.png" }"""))
        // ...and the repo refreshes the full user.
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "avatar": "https://cdn/new.png" } }""",
            ),
        )

        val result = repository.setAvatarFromUrl("https://cdn/new.png")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.avatarUrl).isEqualTo("https://cdn/new.png")

        val avatarRequest = server.takeRequest()
        assertThat(avatarRequest.method).isEqualTo("POST")
        assertThat(avatarRequest.path).isEqualTo("/api/user/avatar/from-url")
        assertThat(avatarRequest.body.readUtf8()).contains("\"url\":\"https://cdn/new.png\"")

        assertThat(server.takeRequest().path).isEqualTo("/api/user")
    }

    @Test
    fun `uploadAvatar posts multipart then refreshes the cached user`() = runTest(testDispatcher) {
        server.enqueue(MockResponse().setResponseCode(201).setBody("""{ "avatarUrl": "https://cdn/up.png" }"""))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "user": { "id": "u1", "username": "adron", "avatar": "https://cdn/up.png" } }""",
            ),
        )

        val result = repository.uploadAvatar(
            bytes = byteArrayOf(1, 2, 3, 4),
            fileName = "avatar.png",
            mimeType = "image/png",
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.avatarUrl).isEqualTo("https://cdn/up.png")

        val uploadRequest = server.takeRequest()
        assertThat(uploadRequest.method).isEqualTo("POST")
        assertThat(uploadRequest.path).isEqualTo("/api/user/avatar/upload")
        assertThat(uploadRequest.getHeader("Content-Type")).contains("multipart/form-data")
    }

    @Test
    fun `searchUsers passes the query and limit and maps results`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "users": [ { "id": "1", "username": "ada", "displayName": "Ada" }, { "id": "2", "username": "adron" } ] }""",
            ),
        )

        val result = repository.searchUsers("ad")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val users = (result as ApiResult.Success).data
        assertThat(users.map { it.username }).containsExactly("ada", "adron").inOrder()

        val recorded = server.takeRequest()
        assertThat(recorded.path).isEqualTo("/api/users/search?q=ad&limit=20")
    }

    @Test
    fun `searchUsers reads the generic data envelope too`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{ "data": [ { "id": "1", "username": "ada" } ] }""",
            ),
        )

        val result = repository.searchUsers("ada")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.single().username).isEqualTo("ada")
    }
}

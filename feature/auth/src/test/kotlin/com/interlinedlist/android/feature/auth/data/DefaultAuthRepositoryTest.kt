package com.interlinedlist.android.feature.auth.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.datastore.SessionStore
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.feature.auth.data.remote.AuthApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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

/**
 * Exercises [DefaultAuthRepository] against a [MockWebServer] so the request
 * shapes, HTTP-status → [AppError] mapping, and session/cache side effects are
 * all covered without hitting the real API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAuthRepositoryTest {

    private val dispatcher = StandardTestDispatcher()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private lateinit var server: MockWebServer
    private lateinit var api: InterlinedListApi
    private lateinit var authApi: AuthApi
    private lateinit var session: SessionStore
    private lateinit var dao: FakeUserDao

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        api = retrofit.create(InterlinedListApi::class.java)
        authApi = retrofit.create(AuthApi::class.java)
        session = fakeSessionStore()
        dao = FakeUserDao()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun repository() = DefaultAuthRepository(
        api = api,
        authApi = authApi,
        sessionStore = session,
        userDao = dao,
        json = json,
        dispatchers = TestDispatcherProvider(dispatcher),
    )

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    // ---- register ----------------------------------------------------------

    @Test
    fun `register success signs in and stores the token and user`() = runTest(dispatcher) {
        enqueue(201) // POST /api/auth/register
        enqueue(200, """{ "token": "il_tok_abc" }""") // POST /api/auth/sync-token
        enqueue(200, """{ "user": { "id": "u1", "username": "newbie", "emailVerified": false } }""")

        val result = repository().register(
            email = "new@example.com",
            username = "newbie",
            password = "s3cret!!",
            displayName = "New Bie",
        )

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val user = (result as ApiResult.Success).data
        assertThat(user.id).isEqualTo("u1")
        assertThat(user.emailVerified).isFalse() // drives the "verify your email" hint
        assertThat(session.currentToken()).isEqualTo("il_tok_abc")
        assertThat(session.userId).isEqualTo("u1")
        assertThat(dao.stored.value?.id).isEqualTo("u1")

        // First request was the register call with the expected body fields.
        val registerBody = server.takeRequest().body.readUtf8()
        assertThat(registerBody).contains("\"email\":\"new@example.com\"")
        assertThat(registerBody).contains("\"username\":\"newbie\"")
        assertThat(registerBody).contains("\"displayName\":\"New Bie\"")
    }

    @Test
    fun `register with a taken email surfaces the mapped error and no session`() = runTest(dispatcher) {
        // 409 Conflict is what the API returns when the email/username is taken.
        enqueue(409, """{ "error": "Email already in use" }""")

        val result = repository().register(
            email = "taken@example.com",
            username = "taken",
            password = "s3cret!!",
            displayName = null,
        )

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Conflict::class.java)
        assertThat(error.message).isEqualTo("Email already in use")
        // No sign-in was attempted, so no session was created.
        assertThat(session.isLoggedIn).isFalse()
        assertThat(dao.stored.value).isNull()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `register maps a weak-password validation error`() = runTest(dispatcher) {
        enqueue(400, """{ "error": "Password is too weak" }""")

        val result = repository().register("a@b.com", "abc", "123", null)

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message).isEqualTo("Password is too weak")
        assertThat(session.isLoggedIn).isFalse()
    }

    // ---- forgot password ---------------------------------------------------

    @Test
    fun `forgotPassword success posts the email`() = runTest(dispatcher) {
        enqueue(201)

        val result = repository().forgotPassword("me@example.com")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).contains("api/auth/forgot-password")
        assertThat(request.body.readUtf8()).contains("\"email\":\"me@example.com\"")
    }

    // ---- reset password ----------------------------------------------------

    @Test
    fun `resetPassword success posts the token and new password`() = runTest(dispatcher) {
        enqueue(201)

        val result = repository().resetPassword(token = "reset-tok", newPassword = "brandN3w!")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val body = server.takeRequest().body.readUtf8()
        assertThat(body).contains("\"token\":\"reset-tok\"")
        assertThat(body).contains("\"password\":\"brandN3w!\"")
    }

    @Test
    fun `resetPassword invalid token maps to a failure`() = runTest(dispatcher) {
        enqueue(400, """{ "error": "Invalid or expired token" }""")

        val result = repository().resetPassword(token = "bad", newPassword = "brandN3w!")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message).isEqualTo("Invalid or expired token")
    }

    // ---- verify email / resend --------------------------------------------

    @Test
    fun `verifyEmail success posts the token`() = runTest(dispatcher) {
        enqueue(201)

        val result = repository().verifyEmail("verify-tok")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.path).contains("api/auth/verify-email")
        assertThat(request.body.readUtf8()).contains("\"token\":\"verify-tok\"")
    }

    @Test
    fun `verifyEmail expired token maps to a failure`() = runTest(dispatcher) {
        enqueue(400, """{ "error": "Verification link has expired" }""")

        val result = repository().verifyEmail("stale")

        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        assertThat((result as ApiResult.Failure).error.message).isEqualTo("Verification link has expired")
    }

    @Test
    fun `resendVerificationEmail success hits the send endpoint`() = runTest(dispatcher) {
        enqueue(201)

        val result = repository().resendVerificationEmail()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(server.takeRequest().path).contains("api/auth/send-verification-email")
    }
}

package com.interlinedlist.android.feature.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * Live end-to-end auth check against the real InterlinedList API.
 *
 * Credentials are supplied at runtime via instrumentation arguments
 * (`testEmail` / `testPassword`) so they never enter source or the APK. When
 * they're absent the test is skipped, keeping the suite green without secrets.
 */
@RunWith(AndroidJUnit4::class)
class AuthE2ETest {

    @Serializable
    private data class TokenRequest(val email: String, val password: String)

    @Serializable
    private data class TokenResponse(val token: String)

    @Serializable
    private data class UserEnvelope(val user: ProbeUser)

    @Serializable
    private data class ProbeUser(val id: String, val username: String = "", val customerStatus: String? = null)

    private interface LiveApi {
        @POST("api/auth/sync-token")
        suspend fun syncToken(@Body body: TokenRequest): TokenResponse

        @GET("api/user")
        suspend fun currentUser(@Header("Authorization") authorization: String): UserEnvelope
    }

    @Test
    fun login_against_production_returns_token_and_user() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val email = args.getString("testEmail")
        val password = args.getString("testPassword")
        assumeTrue(
            "Provide -Pandroid.testInstrumentationRunnerArguments.testEmail/testPassword to run",
            !email.isNullOrBlank() && !password.isNullOrBlank(),
        )
        val baseUrl = args.getString("apiBaseUrl") ?: "https://interlinedlist.com/"

        val json = Json { ignoreUnknownKeys = true }
        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create<LiveApi>()

        val token = api.syncToken(TokenRequest(email!!, password!!)).token
        assertThat(token).isNotEmpty()

        val user = api.currentUser("Bearer $token").user
        assertThat(user.username).isNotEmpty()
    }
}

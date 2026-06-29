package com.interlinedlist.android.core.network.error

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.create
import retrofit2.http.GET

@Serializable
private data class Probe(val value: String)

private interface ProbeApi {
    @GET("thing")
    suspend fun thing(): Probe
}

class SafeApiCallTest {

    private lateinit var server: MockWebServer
    private lateinit var api: ProbeApi
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `success wraps the value`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"value":"ok"}"""))
        val result = safeApiCall(json) { api.thing() }
        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.value).isEqualTo("ok")
    }

    @Test
    fun `401 maps to Unauthorized with parsed message`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Not authenticated"}"""))
        val result = safeApiCall(json) { api.thing() }
        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Unauthorized::class.java)
        assertThat(error.message).isEqualTo("Not authenticated")
    }

    @Test
    fun `403 mentioning subscription maps to SubscriptionRequired`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403)
                .setBody("""{"error":"This feature requires an active subscription."}"""),
        )
        val result = safeApiCall(json) { api.thing() }
        assertThat((result as ApiResult.Failure).error)
            .isInstanceOf(AppError.SubscriptionRequired::class.java)
    }
}

package com.interlinedlist.android.feature.ai.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.feature.ai.data.remote.AiApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit

/** The same Json configuration `NetworkModule` installs in the app. */
internal fun testJson(): Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

internal fun testDispatchers(dispatcher: CoroutineDispatcher): DispatcherProvider =
    object : DispatcherProvider {
        override val io: CoroutineDispatcher get() = dispatcher
        override val default: CoroutineDispatcher get() = dispatcher
        override val main: CoroutineDispatcher get() = dispatcher
    }

/**
 * Builds the repository over a real Retrofit/OkHttp stack pointed at [server],
 * exactly as Hilt wires it (one Retrofit for both the AI and the shared
 * current-user API).
 */
internal fun repositoryFor(
    server: MockWebServer,
    dispatcher: CoroutineDispatcher,
    json: Json = testJson(),
): DefaultAiRepository {
    val retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    return DefaultAiRepository(
        api = retrofit.create(AiApi::class.java),
        userApi = retrofit.create(InterlinedListApi::class.java),
        json = json,
        dispatchers = testDispatchers(dispatcher),
    )
}

internal fun jsonResponse(code: Int, body: String): MockResponse = MockResponse()
    .setResponseCode(code)
    .setHeader("Content-Type", "application/json")
    .setBody(body)

/** A preview as `/suggest` would have returned it, for tests that only exercise `/generate`. */
internal fun previewOf(
    feature: com.interlinedlist.android.feature.ai.domain.AiFeature,
    payload: kotlinx.serialization.json.JsonObject,
): com.interlinedlist.android.feature.ai.domain.AiPreview =
    com.interlinedlist.android.feature.ai.domain.AiPreview(
        feature = feature,
        artifact = com.interlinedlist.android.feature.ai.domain.AiArtifact(payload),
    )

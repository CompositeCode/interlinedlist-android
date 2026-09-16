package com.interlinedlist.android.core.materialize.data

import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.materialize.data.remote.MaterializeApi
import com.interlinedlist.android.core.materialize.domain.MaterializeGate
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
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

internal fun retrofitFor(server: MockWebServer, json: Json): Retrofit = Retrofit.Builder()
    .baseUrl(server.url("/"))
    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
    .build()

/** A gate over the same MockWebServer, exactly as Hilt wires it (one Retrofit). */
internal fun gateFor(
    server: MockWebServer,
    dispatcher: CoroutineDispatcher,
    json: Json = testJson(),
): MaterializeGate = MaterializeGate(
    userApi = retrofitFor(server, json).create(InterlinedListApi::class.java),
    json = json,
    dispatchers = testDispatchers(dispatcher),
)

/**
 * Builds the repository over a real Retrofit/OkHttp stack pointed at [server].
 * The request bodies these tests assert on are the ones the app would send.
 */
internal fun repositoryFor(
    server: MockWebServer,
    dispatcher: CoroutineDispatcher,
    gate: MaterializeGate = gateFor(server, dispatcher),
    json: Json = testJson(),
): DefaultMaterializeRepository = DefaultMaterializeRepository(
    api = retrofitFor(server, json).create(MaterializeApi::class.java),
    gate = gate,
    json = json,
    dispatchers = testDispatchers(dispatcher),
)

internal fun jsonResponse(code: Int, body: String): MockResponse = MockResponse()
    .setResponseCode(code)
    .setHeader("Content-Type", "application/json")
    .setBody(body)

/** `GET /api/user` as the gate reads it. */
internal fun userResponse(customerStatus: String): MockResponse = jsonResponse(
    200,
    """{ "user": { "id": "usr_1", "username": "adron", "customerStatus": "$customerStatus" } }""",
)

internal fun RecordedRequest.jsonBody(): JsonObject =
    Json.decodeFromString(JsonObject.serializer(), body.readUtf8())

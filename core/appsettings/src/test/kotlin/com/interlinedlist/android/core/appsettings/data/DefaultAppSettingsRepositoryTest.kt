package com.interlinedlist.android.core.appsettings.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.appsettings.TestDispatcherProvider
import com.interlinedlist.android.core.appsettings.data.remote.AppSettingsApi
import com.interlinedlist.android.core.appsettings.domain.AppSettingsSeedSource
import com.interlinedlist.android.core.appsettings.domain.CompanionApp
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Drives the device registry against a [MockWebServer]. The enqueued bodies are the
 * exact payloads the live API returned when these endpoints were probed with a real
 * bearer token (see `/help/api/app-settings`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAppSettingsRepositoryTest {

    private val dispatcher = StandardTestDispatcher()

    // Mirrors the production Json (see NetworkModule).
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultAppSettingsRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AppSettingsApi::class.java)
        repository = DefaultAppSettingsRepository(api, json, TestDispatcherProvider(dispatcher))
    }

    @After
    fun tearDown() = server.shutdown()

    // ---- register ----------------------------------------------------------

    @Test
    fun `registerDevice posts the documented body to the app's own appKey`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    { "device": {
                        "deviceId": "android-abc",
                        "deviceName": "InterlinedList Android · Pixel 8",
                        "platform": "android", "isDefault": true,
                        "lastSeenAt": "2026-09-16T21:38:14.346Z",
                        "appVersion": "0.1.0", "osVersion": "14"
                    } }
                    """.trimIndent(),
                ),
            )

            val result = repository.registerDevice(
                deviceId = "android-abc",
                deviceName = "InterlinedList Android · Pixel 8",
                appVersion = "0.1.0",
                osVersion = "14",
            )

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path)
                .isEqualTo("/api/user/app-settings/${CompanionApp.APP_KEY}/devices")
            val body = request.body.readUtf8()
            assertThat(body).contains("\"deviceId\":\"android-abc\"")
            assertThat(body).contains("\"deviceName\":\"InterlinedList Android · Pixel 8\"")
            // The registry's platform vocabulary; anything else is a 400.
            assertThat(body).contains("\"platform\":\"android\"")
            assertThat(body).contains("\"appVersion\":\"0.1.0\"")
            assertThat(body).contains("\"osVersion\":\"14\"")
            // Names the app in the web's Applications list the first time it is seen.
            assertThat(body).contains("\"appDisplayName\":\"InterlinedList Android\"")

            // …and the device the server stored is the device we asked it to store.
            val device = (result as ApiResult.Success).data
            assertThat(device.deviceId).isEqualTo("android-abc")
            assertThat(device.deviceName).isEqualTo("InterlinedList Android · Pixel 8")
            assertThat(device.isDefault).isTrue() // the first device is the main workstation
        }

    @Test
    fun `registerDevice omits versions it does not have`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setBody("""{ "device": { "deviceId": "android-abc" } }"""))

        repository.registerDevice("android-abc", "Phone", appVersion = null, osVersion = null)

        val body = server.takeRequest().body.readUtf8()
        assertThat(body).doesNotContain("appVersion")
        assertThat(body).doesNotContain("osVersion")
    }

    @Test
    fun `registerDevice surfaces a rejected device id`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{ "error": "Invalid deviceId", "code": "bad_request" }"""),
        )

        val result = repository.registerDevice("short", "Phone", null, null)

        val error = (result as ApiResult.Failure).error
        assertThat(error).isInstanceOf(AppError.Unknown::class.java)
        assertThat(error.message).isEqualTo("Invalid deviceId")
    }

    // ---- deregister --------------------------------------------------------

    @Test
    fun `deregisterDevice deletes this device from the registry`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "deleted": true, "promotedDeviceId": "android-other" }"""),
        )

        val result = repository.deregisterDevice("android-abc")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("DELETE")
        assertThat(request.path)
            .isEqualTo("/api/user/app-settings/${CompanionApp.APP_KEY}/devices/android-abc")
    }

    @Test
    fun `deregistering an already removed device still succeeds`() = runTest(dispatcher) {
        // The live API 404s for an unknown device. Teardown must be idempotent: the
        // user may have removed this phone from the web before signing out here.
        server.enqueue(
            MockResponse().setResponseCode(404)
                .setBody("""{ "error": "Not found", "code": "not_found" }"""),
        )

        val result = repository.deregisterDevice("android-abc")

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
    }

    @Test
    fun `deregisterDevice surfaces a real failure`() = runTest(dispatcher) {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{ "error": "boom" }"""))

        val result = repository.deregisterDevice("android-abc")

        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.Server::class.java)
    }

    // ---- bootstrap ---------------------------------------------------------

    @Test
    fun `bootstrap resolves the main workstation's settings for a new machine`() =
        runTest(dispatcher) {
            server.enqueue(
                MockResponse().setBody(
                    """
                    { "source": "default-device", "appKey": "${CompanionApp.APP_KEY}",
                      "scope": "device", "deviceId": "android-main", "version": 5,
                      "updatedAt": "2026-09-16T21:38:25.715Z", "schemaVersion": 2,
                      "settings": { "themeMode": "DARK" },
                      "defaultDeviceId": "android-main", "defaultDeviceName": "Studio Pixel" }
                    """.trimIndent(),
                ),
            )

            val result = repository.bootstrap("android-new")

            val request = server.takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.path).isEqualTo(
                "/api/user/app-settings/${CompanionApp.APP_KEY}/bootstrap?deviceId=android-new",
            )
            val seed = (result as ApiResult.Success).data
            assertThat(seed.source).isEqualTo(AppSettingsSeedSource.DEFAULT_DEVICE)
            assertThat(seed.schemaVersion).isEqualTo(2)
            assertThat(seed.sourceDeviceName).isEqualTo("Studio Pixel")
            // The document is opaque: it round-trips, it is not interpreted.
            assertThat(seed.settings["themeMode"]?.jsonPrimitive?.content).isEqualTo("DARK")
        }

    @Test
    fun `bootstrap reports nothing to seed from as NotFound`() = runTest(dispatcher) {
        // The live API answers 404 { "source": "none" } for the account's first machine.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{ "source": "none" }"""))

        val result = repository.bootstrap("android-new")

        assertThat((result as ApiResult.Failure).error).isInstanceOf(AppError.NotFound::class.java)
    }

    @Test
    fun `an unfamiliar source still yields its settings`() = runTest(dispatcher) {
        server.enqueue(
            MockResponse().setBody("""{ "source": "something-new", "settings": { "a": 1 } }"""),
        )

        val seed = (repository.bootstrap("android-new") as ApiResult.Success).data

        assertThat(seed.source).isEqualTo(AppSettingsSeedSource.ACCOUNT)
        assertThat(seed.settings).hasSize(1)
    }
}

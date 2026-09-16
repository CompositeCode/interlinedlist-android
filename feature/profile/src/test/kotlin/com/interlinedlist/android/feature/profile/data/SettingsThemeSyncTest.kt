package com.interlinedlist.android.feature.profile.data

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.dispatcher.DispatcherProvider
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.datastore.ThemeMode
import com.interlinedlist.android.core.network.api.InterlinedListApi
import com.interlinedlist.android.core.network.preferences.NotificationTrayLimitStore
import com.interlinedlist.android.feature.profile.data.remote.ProfileApi
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Issue #36: the theme preference synced to the account, driven end to end through the
 * real Retrofit stack against a MockWebServer.
 *
 * What is pinned here is the *contract between the two stores*: the device's
 * `ThemeSettingsStore` is what the app renders from and survives with no connection,
 * the account's `theme` is what a second device sees, and `refresh()` is the sync point
 * that reconciles them. The rule itself is tested in isolation by `ThemeSyncTest`; this
 * covers that the repository actually applies it, and that a push carries `theme` alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsThemeSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: DefaultSettingsRepository
    private lateinit var themeStore: FakeThemeSettingsStore

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
    }

    @After
    fun tearDown() = server.shutdown()

    /** Builds the repository over a "device" that already holds [local]. */
    private fun repositoryWithLocalTheme(local: ThemeMode, unsynced: Boolean = false) {
        themeStore = FakeThemeSettingsStore(initial = local, unsynced = unsynced)
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        repository = DefaultSettingsRepository(
            api = retrofit.create(ProfileApi::class.java),
            trayLimitStore = NotificationTrayLimitStore(
                retrofit.create(InterlinedListApi::class.java),
                json,
            ),
            themeStore = themeStore,
            json = json,
            dispatchers = dispatchers,
        )
    }

    /** A `GET /api/user` (or PATCH echo) whose account theme is [theme]. */
    private fun enqueueUser(theme: String?) = server.enqueue(
        MockResponse().setResponseCode(200).setBody(
            """
            {
              "user": {
                "id": "u1",
                "username": "adron",
                "theme": ${theme?.let { "\"$it\"" } ?: "null"},
                "viewingPreference": "all_messages",
                "showPreviews": true
              }
            }
            """.trimIndent(),
        ),
    )

    private fun enqueueOffline() =
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AT_START))

    private fun RecordedRequest.jsonBody() =
        json.parseToJsonElement(body.readUtf8()) as JsonObject

    // --- Local change -> PATCH ------------------------------------------------

    @Test
    fun `choosing a theme stores it locally and PATCHes theme alone`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.SYSTEM)
        enqueueUser(theme = "dark")

        val result = repository.setThemeMode(ThemeMode.DARK)

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(themeStore.hasUnsyncedChange).isFalse()

        val recorded = server.takeRequest()
        assertThat(recorded.method).isEqualTo("PATCH")
        assertThat(recorded.path).isEqualTo("/api/user/update")
        val body = recorded.jsonBody()
        assertThat(body.keys).containsExactly("theme")
        assertThat(body.getValue("theme").jsonPrimitive.content).isEqualTo("dark")
    }

    /**
     * "Follow the device" is a real account value (`/help/settings`: "Light, dark, or
     * system"), so it is pushed as `system` rather than coerced into light or dark.
     */
    @Test
    fun `choosing system pushes system rather than coercing it`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK)
        enqueueUser(theme = "system")

        repository.setThemeMode(ThemeMode.SYSTEM)

        val body = server.takeRequest().jsonBody()
        assertThat(body.keys).containsExactly("theme")
        assertThat(body.getValue("theme").jsonPrimitive.content).isEqualTo("system")
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.SYSTEM)
    }

    // --- Account value applied at sign-in -------------------------------------

    /**
     * The issue's headline case: dark was chosen on the web, this install has never
     * had a theme picked on it, so the first refresh after signing in adopts dark —
     * without PATCHing anything back.
     */
    @Test
    fun `a fresh install adopts the account theme on the first refresh`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.SYSTEM)
        enqueueUser(theme = "dark")

        val result = repository.refresh()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(themeStore.hasUnsyncedChange).isFalse()
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun `a theme changed elsewhere is adopted on the next refresh`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK)
        enqueueUser(theme = "light")

        repository.refresh()

        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.LIGHT)
        assertThat(server.requestCount).isEqualTo(1)
    }

    /**
     * The server stores any string for `theme` (a PATCH of "sepia" is answered 200), so
     * a value this app cannot render really can come back. It must neither be adopted
     * nor overwritten.
     */
    @Test
    fun `an unrenderable account theme leaves the device alone`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK)
        enqueueUser(theme = "sepia")

        repository.refresh()

        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `an account with no theme leaves the device alone`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.LIGHT)
        enqueueUser(theme = null)

        repository.refresh()

        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.LIGHT)
        assertThat(server.requestCount).isEqualTo(1)
    }

    // --- Offline change survives and syncs on reconnect -----------------------

    @Test
    fun `a change made offline keeps the theme and stays pending`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.LIGHT)
        enqueueOffline()

        val result = repository.setThemeMode(ThemeMode.DARK)

        // The save is reported as failed...
        assertThat(result).isInstanceOf(ApiResult.Failure::class.java)
        // ...but the choice is not rolled back, and it is still owed to the account.
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(themeStore.hasUnsyncedChange).isTrue()
    }

    @Test
    fun `the change made offline syncs on the next refresh`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.LIGHT)
        enqueueOffline()
        repository.setThemeMode(ThemeMode.DARK)
        server.takeRequest()

        // Back online. The account still holds the pre-offline value.
        enqueueUser(theme = "light")
        enqueueUser(theme = "dark")
        val result = repository.refresh()

        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat((result as ApiResult.Success).data.theme).isEqualTo("dark")
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(themeStore.hasUnsyncedChange).isFalse()

        assertThat(server.takeRequest().method).isEqualTo("GET")
        val push = server.takeRequest()
        assertThat(push.method).isEqualTo("PATCH")
        // The body is a one-shot buffer, so read it once and assert on that.
        val body = push.jsonBody()
        assertThat(body.keys).containsExactly("theme")
        assertThat(body.getValue("theme").jsonPrimitive.content).isEqualTo("dark")
    }

    /**
     * The offline change survives a process death: the device's store is re-read with
     * the pending flag still set, so the very first sync of the next launch pushes it.
     */
    @Test
    fun `an offline change that outlived the process still pushes`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK, unsynced = true)
        enqueueUser(theme = "light")
        enqueueUser(theme = "dark")

        repository.refresh()

        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(themeStore.hasUnsyncedChange).isFalse()
        assertThat(server.takeRequest().method).isEqualTo("GET")
        assertThat(server.takeRequest().method).isEqualTo("PATCH")
    }

    /**
     * The reconciliation rule in the direction that matters most: a pending local
     * change is never overwritten by the account's stale value, even though the very
     * same call adopts the account's value when nothing is pending.
     */
    @Test
    fun `a pending local change wins over the account value`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK, unsynced = true)
        enqueueUser(theme = "light")
        enqueueUser(theme = "dark")

        repository.refresh()

        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `a pending push that fails again keeps the change owed`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK, unsynced = true)
        enqueueUser(theme = "light")
        enqueueOffline()

        val result = repository.refresh()

        // The refresh itself still succeeded — only the push did not.
        assertThat(result).isInstanceOf(ApiResult.Success::class.java)
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(themeStore.hasUnsyncedChange).isTrue()
    }

    /**
     * When the account already agrees with the pending change (the confirmation was
     * lost, or the same choice was made on the web), reconciling clears the debt
     * without spending a request.
     */
    @Test
    fun `a pending change the account already holds is not re-sent`() = runTest(testDispatcher) {
        repositoryWithLocalTheme(ThemeMode.DARK, unsynced = true)
        enqueueUser(theme = "dark")

        repository.refresh()

        assertThat(themeStore.hasUnsyncedChange).isFalse()
        assertThat(repository.observeThemeMode().first()).isEqualTo(ThemeMode.DARK)
        assertThat(server.requestCount).isEqualTo(1)
    }
}

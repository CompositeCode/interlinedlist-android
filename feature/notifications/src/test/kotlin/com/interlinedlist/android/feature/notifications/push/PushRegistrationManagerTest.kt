package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The device-token lifecycle: register on availability/rotation, re-register on every
 * launch, unregister on sign-out and account deletion, and stay quiet — but functional —
 * when notifications are not permitted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PushRegistrationManagerTest {

    private val repository = RecordingPushRegistrationRepository()
    private val permissions = FakeNotificationPermissionChecker(allowed = true)

    private fun manager(provider: PushTokenProvider) =
        PushRegistrationManager(provider, repository, permissions)

    /**
     * Models the signed-in shell being entered: the lifecycle collects for the session
     * in the background. Nothing here delays, so [runCurrent] settles it.
     */
    private fun TestScope.startSession(manager: PushRegistrationManager) {
        backgroundScope.launch { manager.runForSession() }
        runCurrent()
    }

    // ---- registration ------------------------------------------------------

    @Test
    fun `registers the device as soon as a token first becomes available`() = runTest {
        val provider = FakePushTokenProvider() // no token yet
        startSession(manager(provider))
        assertThat(repository.registered).isEmpty()

        provider.rotate("token-1")
        runCurrent()

        assertThat(repository.registered).containsExactly("token-1")
    }

    @Test
    fun `registers again when the token rotates`() = runTest {
        val provider = FakePushTokenProvider("token-1")
        startSession(manager(provider))

        provider.rotate("token-2")
        runCurrent()

        assertThat(repository.registered).containsExactly("token-1", "token-2").inOrder()
    }

    @Test
    fun `re-registers on app launch with an unchanged token`() = runTest {
        // A fresh process: the token survived from the previous launch but this manager
        // has registered nothing, so it must register again — the docs ask for exactly
        // that, in case the token rotated while the app was not running.
        val provider = FakePushTokenProvider("token-1")

        startSession(manager(provider))

        assertThat(repository.registered).containsExactly("token-1")
    }

    @Test
    fun `does not re-register the same token twice within one session`() = runTest {
        val provider = FakePushTokenProvider("token-1")
        startSession(manager(provider))

        provider.rotate("token-1") // same value re-published
        runCurrent()

        assertThat(repository.registered).containsExactly("token-1")
    }

    @Test
    fun `a failed registration is retried on the next rotation`() = runTest {
        val provider = FakePushTokenProvider()
        repository.registerResult = ApiResult.Failure(AppError.Network("offline"))
        startSession(manager(provider))

        provider.rotate("token-1")
        runCurrent()
        repository.registerResult = ApiResult.Success(Unit)
        provider.rotate("token-1-again")
        runCurrent()

        assertThat(repository.registered).containsExactly("token-1", "token-1-again").inOrder()
    }

    // ---- denied permission -------------------------------------------------

    @Test
    fun `denied notification permission issues no registration and breaks nothing`() = runTest {
        permissions.allowed = false
        val provider = FakePushTokenProvider("token-1")

        startSession(manager(provider))
        provider.rotate("token-2")
        runCurrent()

        // No registration at all — there is no point pushing to a device that cannot
        // display it — and the lifecycle keeps running rather than failing.
        assertThat(repository.registered).isEmpty()
        assertThat(repository.unregistered).isEmpty()
    }

    @Test
    fun `granting the permission later registers the current token`() = runTest {
        permissions.allowed = false
        val provider = FakePushTokenProvider("token-1")
        val manager = manager(provider)
        startSession(manager)
        assertThat(repository.registered).isEmpty()

        permissions.allowed = true
        manager.onNotificationPermissionGranted()
        runCurrent()

        assertThat(repository.registered).containsExactly("token-1")
    }

    // ---- teardown ----------------------------------------------------------

    @Test
    fun `sign-out unregisters the device token`() = runTest {
        val provider = FakePushTokenProvider("token-1")
        val manager = manager(provider)
        startSession(manager)

        PushTokenSessionTeardown(manager).onSessionEnding()

        assertThat(repository.unregistered).containsExactly("token-1")
    }

    @Test
    fun `account deletion unregisters the device token through the same teardown`() = runTest {
        // Account deletion signs out via AuthRepository.logout(), which runs exactly the
        // teardown task exercised here — there is no separate deletion path to miss.
        val provider = FakePushTokenProvider("token-1")
        val manager = manager(provider)
        startSession(manager)

        PushTokenSessionTeardown(manager).onSessionEnding()

        assertThat(repository.unregistered).containsExactly("token-1")
    }

    @Test
    fun `a rotation retires the registration it replaced, and sign-out retires the rest`() =
        runTest {
            val provider = FakePushTokenProvider("token-1")
            val manager = manager(provider)
            startSession(manager)

            provider.rotate("token-2")
            runCurrent()
            // The superseded token goes immediately, so the server never holds a dead
            // registration for this device.
            assertThat(repository.unregistered).containsExactly("token-1")

            manager.unregisterCurrentToken()

            assertThat(repository.unregistered).containsExactly("token-1", "token-2").inOrder()
        }

    @Test
    fun `sign-out with no token available issues no call`() = runTest {
        val manager = manager(FakePushTokenProvider())

        manager.unregisterCurrentToken()

        assertThat(repository.unregistered).isEmpty()
    }

    @Test
    fun `signing back in registers again after a teardown`() = runTest {
        val provider = FakePushTokenProvider("token-1")
        val manager = manager(provider)
        startSession(manager)
        manager.unregisterCurrentToken()

        // A new session starts (the shell is re-entered after signing in again).
        startSession(manager)

        assertThat(repository.registered).containsExactly("token-1", "token-1").inOrder()
    }
}

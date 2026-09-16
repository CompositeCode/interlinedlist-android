package com.interlinedlist.android.feature.integrations.ui.accounts

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.integrations.domain.ConnectedAccount
import org.junit.Test
import java.time.Instant

/**
 * What the row actually renders for each connection-health state. The three cases are
 * distinct on purpose: "verified recently", "verified too long ago" and "never verified"
 * carry different risks, and the copy has to name the consequence rather than print a
 * timestamp and leave the user to work it out.
 */
class ConnectionHealthCopyTest {

    private val now: Instant = Instant.parse("2026-09-16T12:00:00Z")

    private fun linkedIn(lastVerifiedAt: String?, connectedAt: String? = "2026-05-01T12:00:00Z") =
        ConnectedAccount(
            provider = ConnectedAccount.Provider.LINKEDIN,
            isConnected = true,
            handle = "Adron Hall",
            identityProvider = "linkedin",
            connectedAt = connectedAt,
            lastVerifiedAt = lastVerifiedAt,
        )

    // --- fresh ---

    @Test
    fun `a fresh connection shows no badge and a reassuring line`() {
        val account = linkedIn(lastVerifiedAt = "2026-09-14T12:00:00Z")

        assertThat(account.healthBadge(now)).isNull()
        assertThat(account.healthLine(now)).isEqualTo("Verified 2 days ago — this connection is working.")
    }

    @Test
    fun `a connection verified today reads as today, not as a timestamp`() {
        val account = linkedIn(lastVerifiedAt = "2026-09-16T08:00:00Z")

        assertThat(account.healthLine(now)).isEqualTo("Verified today — this connection is working.")
    }

    // --- stale ---

    @Test
    fun `a stale connection is badged and names the silent-failure risk`() {
        val account = linkedIn(lastVerifiedAt = "2026-07-01T12:00:00Z")

        assertThat(account.healthBadge(now)).isEqualTo("Check connection")
        assertThat(account.healthLine(now)).isEqualTo(
            "Last verified 2 months ago. It may have expired — posts may stop reaching " +
                "LinkedIn without an error. Tap Verify to check it.",
        )
    }

    @Test
    fun `a stale non-cross-post connection describes its own consequence`() {
        val account = linkedIn(lastVerifiedAt = "2026-07-01T12:00:00Z").copy(
            provider = ConnectedAccount.Provider.GITHUB,
            identityProvider = "github",
        )

        assertThat(account.healthBadge(now)).isEqualTo("Check connection")
        assertThat(account.healthLine(now)).contains("GitHub features may stop working")
        assertThat(account.healthLine(now)).doesNotContain("cross-post")
    }

    // --- never verified ---

    @Test
    fun `a never-verified connection is called out separately from a stale one`() {
        val account = linkedIn(lastVerifiedAt = null)

        assertThat(account.healthBadge(now)).isEqualTo("Never verified")
        assertThat(account.healthLine(now)).isEqualTo(
            "Connected 4 months ago, never verified. There is no sign it still works — " +
                "posts may stop reaching LinkedIn without an error. Tap Verify to check it.",
        )
    }

    @Test
    fun `a never-verified connection with no connectedAt still explains itself`() {
        val account = linkedIn(lastVerifiedAt = null, connectedAt = null)

        assertThat(account.healthLine(now)).startsWith("Never verified. There is no sign it still works")
    }

    // --- not linked ---

    @Test
    fun `an unlinked provider has no health copy at all`() {
        val account = ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false)

        assertThat(account.healthBadge(now)).isNull()
        assertThat(account.healthLine(now)).isNull()
    }

    // --- unlink confirmation ---

    @Test
    fun `the unlink confirmation states the cross-posting consequence plainly`() {
        val account = linkedIn(lastVerifiedAt = "2026-09-14T12:00:00Z")

        assertThat(account.unlinkTitle()).isEqualTo("Unlink LinkedIn?")
        assertThat(account.unlinkMessage()).startsWith("This stops cross-posting to LinkedIn.")
        assertThat(account.unlinkMessage()).contains("reconnect on the InterlinedList website")
        assertThat(account.unlinkedMessage()).isEqualTo("LinkedIn unlinked. Cross-posting to LinkedIn is off.")
    }

    @Test
    fun `a non-cross-post provider does not claim cross-posting stops`() {
        val account = linkedIn(lastVerifiedAt = "2026-09-14T12:00:00Z").copy(
            provider = ConnectedAccount.Provider.GITHUB,
            identityProvider = "github",
        )

        assertThat(account.unlinkMessage()).doesNotContain("cross-post")
        assertThat(account.unlinkedMessage()).isEqualTo("GitHub unlinked.")
    }
}

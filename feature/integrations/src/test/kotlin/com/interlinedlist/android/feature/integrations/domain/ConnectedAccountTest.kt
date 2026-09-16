package com.interlinedlist.android.feature.integrations.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/**
 * Connection health is what makes a lapsed cross-post destination visible before a
 * post silently fails to syndicate, so the three states and the boundary between
 * them are pinned here rather than left to the UI.
 */
class ConnectedAccountTest {

    private val now: Instant = Instant.parse("2026-09-16T12:00:00Z")

    private fun linked(lastVerifiedAt: String?, connectedAt: String? = "2026-01-01T00:00:00Z") =
        ConnectedAccount(
            provider = ConnectedAccount.Provider.LINKEDIN,
            isConnected = true,
            handle = "Adron Hall",
            identityProvider = "linkedin",
            connectedAt = connectedAt,
            lastVerifiedAt = lastVerifiedAt,
        )

    @Test
    fun `a recently verified connection is fresh`() {
        val account = linked(lastVerifiedAt = "2026-09-14T12:00:00Z") // 2 days ago

        assertThat(account.healthAt(now)).isEqualTo(ConnectionHealth.FRESH)
    }

    @Test
    fun `a connection unverified for longer than the threshold is stale`() {
        val account = linked(lastVerifiedAt = "2026-07-01T12:00:00Z") // 77 days ago

        assertThat(account.healthAt(now)).isEqualTo(ConnectionHealth.STALE)
    }

    @Test
    fun `a linked connection with no lastVerifiedAt is never-verified`() {
        val account = linked(lastVerifiedAt = null)

        assertThat(account.healthAt(now)).isEqualTo(ConnectionHealth.NEVER_VERIFIED)
    }

    @Test
    fun `the staleness threshold is 30 days, inclusive`() {
        // One second inside the window is still fresh; exactly 30 days old is stale.
        val justInside = linked(lastVerifiedAt = "2026-08-17T12:00:01Z")
        val exactlyThirtyDays = linked(lastVerifiedAt = "2026-08-17T12:00:00Z")

        assertThat(ConnectionHealth.STALE_AFTER.toDays()).isEqualTo(30)
        assertThat(justInside.healthAt(now)).isEqualTo(ConnectionHealth.FRESH)
        assertThat(exactlyThirtyDays.healthAt(now)).isEqualTo(ConnectionHealth.STALE)
    }

    @Test
    fun `an unparseable timestamp fails loud rather than claiming freshness`() {
        val account = linked(lastVerifiedAt = "not-a-timestamp")

        assertThat(account.healthAt(now)).isEqualTo(ConnectionHealth.NEVER_VERIFIED)
    }

    @Test
    fun `an unlinked provider has no health and no identity key`() {
        val account = ConnectedAccount(ConnectedAccount.Provider.BLUESKY, isConnected = false)

        assertThat(account.healthAt(now)).isNull()
        assertThat(account.isLinked).isFalse()
        assertThat(account.key).isEqualTo("BLUESKY")
    }

    @Test
    fun `a linked row keys on the identity provider string so instances stay distinct`() {
        val techhub = linked(null).copy(
            provider = ConnectedAccount.Provider.MASTODON,
            identityProvider = "mastodon:techhub.social",
        )
        val social = techhub.copy(identityProvider = "mastodon:mastodon.social")

        assertThat(techhub.key).isEqualTo("mastodon:techhub.social")
        assertThat(social.key).isNotEqualTo(techhub.key)
    }

    @Test
    fun `identity provider strings map back to their provider`() {
        val from = ConnectedAccount.Provider::fromIdentityProvider

        assertThat(from("github")).isEqualTo(ConnectedAccount.Provider.GITHUB)
        assertThat(from("LinkedIn")).isEqualTo(ConnectedAccount.Provider.LINKEDIN)
        assertThat(from("mastodon:techhub.social")).isEqualTo(ConnectedAccount.Provider.MASTODON)
        assertThat(from("twitter")).isEqualTo(ConnectedAccount.Provider.TWITTER)
        assertThat(from("someothernetwork")).isNull()
        assertThat(from(null)).isNull()
    }

    @Test
    fun `only the social networks count as cross-post targets`() {
        assertThat(ConnectedAccount.Provider.GITHUB.isCrossPostTarget).isFalse()
        assertThat(
            ConnectedAccount.Provider.entries.filter { it.isCrossPostTarget }.map { it.apiToken },
        ).containsExactly("linkedin", "bluesky", "mastodon", "twitter")
    }
}

package com.interlinedlist.android.feature.documents.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant

/** The derived status, role mapping and email validation rules for email invites. */
class DocumentInviteTest {

    private val now: Instant = Instant.parse("2026-06-01T12:00:00Z")

    private fun invite(
        expiresAt: String? = null,
        accepted: Boolean = false,
        revokedAt: String? = null,
        url: String? = null,
    ) = DocumentInvite(
        email = "friend@example.com",
        token = "tok-1",
        role = InviteRole.EDITOR,
        expiresAt = expiresAt,
        createdAt = "2026-05-01T09:00:00Z",
        accepted = accepted,
        revokedAt = revokedAt,
        url = url,
    )

    @Test
    fun `an unaccepted invite with no expiry is pending`() {
        assertThat(invite().statusAt(now)).isEqualTo(InviteStatus.PENDING)
        assertThat(invite().statusAt(now).label).isEqualTo("Pending")
    }

    @Test
    fun `an invite expiring in the future is still pending`() {
        assertThat(invite(expiresAt = "2026-06-02T12:00:00Z").statusAt(now))
            .isEqualTo(InviteStatus.PENDING)
    }

    @Test
    fun `an invite whose expiry has passed is expired`() {
        val expired = invite(expiresAt = "2026-05-30T12:00:00Z")
        assertThat(expired.statusAt(now)).isEqualTo(InviteStatus.EXPIRED)
        assertThat(expired.statusAt(now).label).isEqualTo("Expired")
    }

    @Test
    fun `an invite expiring exactly now is expired`() {
        assertThat(invite(expiresAt = "2026-06-01T12:00:00Z").statusAt(now))
            .isEqualTo(InviteStatus.EXPIRED)
    }

    @Test
    fun `acceptance wins over expiry`() {
        assertThat(invite(expiresAt = "2026-05-30T12:00:00Z", accepted = true).statusAt(now))
            .isEqualTo(InviteStatus.ACCEPTED)
    }

    @Test
    fun `revocation wins over everything`() {
        val revoked = invite(accepted = true, revokedAt = "2026-05-31T00:00:00Z")
        assertThat(revoked.statusAt(now)).isEqualTo(InviteStatus.REVOKED)
    }

    @Test
    fun `an unparseable expiry never expires the invite`() {
        assertThat(invite(expiresAt = "not-a-date").statusAt(now)).isEqualTo(InviteStatus.PENDING)
    }

    @Test
    fun `inviteUrl prefers the server url and otherwise builds the canonical path`() {
        assertThat(invite(url = "https://example.test/documents/invite/abc").inviteUrl())
            .isEqualTo("https://example.test/documents/invite/abc")
        assertThat(invite().inviteUrl())
            .isEqualTo("https://interlinedlist.com/documents/invite/tok-1")
    }

    @Test
    fun `invite roles map to the server sharing vocabulary`() {
        assertThat(InviteRole.VIEWER.apiValue).isEqualTo("watcher")
        assertThat(InviteRole.EDITOR.apiValue).isEqualTo("collaborator")
        assertThat(InviteRole.ADMIN.apiValue).isEqualTo("manager")
    }

    @Test
    fun `fromApi maps known roles and defaults unknown ones to viewer`() {
        assertThat(InviteRole.fromApi("collaborator")).isEqualTo(InviteRole.EDITOR)
        assertThat(InviteRole.fromApi("Manager")).isEqualTo(InviteRole.ADMIN)
        assertThat(InviteRole.fromApi("watcher")).isEqualTo(InviteRole.VIEWER)
        assertThat(InviteRole.fromApi("wat")).isEqualTo(InviteRole.VIEWER)
        assertThat(InviteRole.fromApi(null)).isEqualTo(InviteRole.VIEWER)
    }

    @Test
    fun `email validation accepts ordinary addresses and normalises them`() {
        assertThat(InviteEmail.isValid("Friend@Example.COM")).isTrue()
        assertThat(InviteEmail.normalize("  Friend@Example.COM ")).isEqualTo("friend@example.com")
        assertThat(InviteEmail.isValid("first.last+tag@mail.example.co.uk")).isTrue()
    }

    @Test
    fun `email validation rejects malformed addresses`() {
        listOf("", "   ", "friend", "friend@", "@example.com", "friend@example", "a b@example.com", "friend@@example.com")
            .forEach { assertThat(InviteEmail.isValid(it)).isFalse() }
    }
}

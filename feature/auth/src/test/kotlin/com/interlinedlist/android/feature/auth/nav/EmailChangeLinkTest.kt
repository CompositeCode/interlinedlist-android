package com.interlinedlist.android.feature.auth.nav

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Parsing rules for the two emailed email-change links.
 *
 * These are the entry point for a security action reached from an email, so the
 * "must not match" cases matter as much as the happy paths: a malformed or foreign
 * link has to come back as null rather than as a half-populated link that the screen
 * would then submit.
 */
class EmailChangeLinkTest {

    // ---- the four link shapes ---------------------------------------------

    @Test
    fun `parses the https verify-email-change link`() {
        val link = EmailChangeLink.parse("https://interlinedlist.com/verify-email-change?token=abc123")

        assertThat(link).isEqualTo(EmailChangeLink(EmailChangeAction.VERIFY, "abc123"))
    }

    @Test
    fun `parses the https undo-email-change link`() {
        val link = EmailChangeLink.parse("https://interlinedlist.com/undo-email-change?token=abc123")

        assertThat(link).isEqualTo(EmailChangeLink(EmailChangeAction.UNDO, "abc123"))
    }

    @Test
    fun `parses the custom-scheme verify link`() {
        val link = EmailChangeLink.parse("interlinedlist://verify-email-change?token=abc123")

        assertThat(link).isEqualTo(EmailChangeLink(EmailChangeAction.VERIFY, "abc123"))
    }

    @Test
    fun `parses the custom-scheme undo link`() {
        val link = EmailChangeLink.parse("interlinedlist://undo-email-change?token=abc123")

        assertThat(link).isEqualTo(EmailChangeLink(EmailChangeAction.UNDO, "abc123"))
    }

    // ---- tolerated variations ---------------------------------------------

    @Test
    fun `tolerates a www host, a trailing slash, other query params and a fragment`() {
        val link = EmailChangeLink.parse(
            "https://WWW.InterlinedList.com/verify-email-change/?utm_source=email&token=abc123#top",
        )

        assertThat(link).isEqualTo(EmailChangeLink(EmailChangeAction.VERIFY, "abc123"))
    }

    @Test
    fun `percent-decodes the token`() {
        val link = EmailChangeLink.parse("https://interlinedlist.com/undo-email-change?token=a%2Bb%3Dc")

        assertThat(link?.token).isEqualTo("a+b=c")
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        val link = EmailChangeLink.parse("  https://interlinedlist.com/verify-email-change?token=abc123  ")

        assertThat(link?.action).isEqualTo(EmailChangeAction.VERIFY)
    }

    // ---- malformed / foreign links must not match -------------------------

    @Test
    fun `a link with no token does not parse`() {
        assertThat(EmailChangeLink.parse("https://interlinedlist.com/verify-email-change")).isNull()
    }

    @Test
    fun `a link with a blank token does not parse`() {
        assertThat(EmailChangeLink.parse("https://interlinedlist.com/undo-email-change?token=")).isNull()
        assertThat(EmailChangeLink.parse("https://interlinedlist.com/undo-email-change?token=%20")).isNull()
    }

    @Test
    fun `a look-alike host does not parse`() {
        assertThat(
            EmailChangeLink.parse("https://interlinedlist.com.evil.example/verify-email-change?token=abc"),
        ).isNull()
    }

    @Test
    fun `the plain verify-email link is not mistaken for an email change`() {
        assertThat(EmailChangeLink.parse("https://interlinedlist.com/verify-email?token=abc")).isNull()
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        assertThat(EmailChangeLink.parse("not a uri at all")).isNull()
        assertThat(EmailChangeLink.parse("")).isNull()
        assertThat(EmailChangeLink.parse(null)).isNull()
        assertThat(EmailChangeLink.parse("verify-email-change?token=abc")).isNull()
    }

    // ---- route mapping -----------------------------------------------------

    @Test
    fun `routeForEmailChangeLink maps each link onto its in-app route`() {
        assertThat(
            AuthRoutes.routeForEmailChangeLink("https://interlinedlist.com/verify-email-change?token=abc123"),
        ).isEqualTo("auth/email-change?action=VERIFY&token=abc123")

        assertThat(
            AuthRoutes.routeForEmailChangeLink("interlinedlist://undo-email-change?token=abc123"),
        ).isEqualTo("auth/email-change?action=UNDO&token=abc123")
    }

    @Test
    fun `routeForEmailChangeLink ignores links it does not own`() {
        assertThat(AuthRoutes.routeForEmailChangeLink("https://interlinedlist.com/lists/shared/xyz")).isNull()
        assertThat(AuthRoutes.routeForEmailChangeLink(null)).isNull()
    }
}

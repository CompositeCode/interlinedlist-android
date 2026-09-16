package com.interlinedlist.android.blog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The blog mailing list's two emailed links.
 *
 * The URLs asserted here were established against the live site rather than guessed:
 * `GET https://interlinedlist.com/api/blog/subscribe/confirm?token=probe` and
 * `GET https://interlinedlist.com/api/blog/unsubscribe?token=probe` both answer `307`
 * to `https://interlinedlist.com/blog?subscription=invalid`, while the shorter
 * candidates (`/blog/confirm`, `/blog/subscribe/confirm`, `/blog/unsubscribe`,
 * `/unsubscribe`, `/confirm-subscription`) all 404. An intent filter on a guessed path
 * would never fire, so these cases pin the real ones.
 */
class BlogSubscriptionLinkTest {

    @Test
    fun `parses the confirmation link from the email`() {
        val link = BlogLink.subscriptionLinkFor(
            "https://interlinedlist.com/api/blog/subscribe/confirm?token=abc123",
        )

        assertThat(link).isEqualTo(
            BlogSubscriptionLink(BlogSubscriptionAction.CONFIRM, "abc123"),
        )
    }

    @Test
    fun `parses the unsubscribe link from the email footer`() {
        val link = BlogLink.subscriptionLinkFor(
            "https://interlinedlist.com/api/blog/unsubscribe?token=abc123",
        )

        assertThat(link).isEqualTo(
            BlogSubscriptionLink(BlogSubscriptionAction.UNSUBSCRIBE, "abc123"),
        )
    }

    @Test
    fun `parses the custom-scheme equivalents`() {
        assertThat(BlogLink.subscriptionLinkFor("interlinedlist://blog-subscribe-confirm?token=t"))
            .isEqualTo(BlogSubscriptionLink(BlogSubscriptionAction.CONFIRM, "t"))
        assertThat(BlogLink.subscriptionLinkFor("interlinedlist://blog-unsubscribe?token=t"))
            .isEqualTo(BlogSubscriptionLink(BlogSubscriptionAction.UNSUBSCRIBE, "t"))
    }

    @Test
    fun `tolerates www, casing, http, a trailing slash and extra parameters`() {
        val variants = listOf(
            "https://www.interlinedlist.com/api/blog/unsubscribe?token=t",
            "HTTPS://INTERLINEDLIST.COM/API/BLOG/UNSUBSCRIBE?token=t",
            "http://interlinedlist.com/api/blog/unsubscribe?token=t",
            "https://interlinedlist.com/api/blog/unsubscribe/?token=t",
            "https://interlinedlist.com/api/blog/unsubscribe?utm_source=email&token=t",
            "  https://interlinedlist.com/api/blog/unsubscribe?token=t  ",
        )

        variants.forEach { uri ->
            assertThat(BlogLink.subscriptionLinkFor(uri))
                .isEqualTo(BlogSubscriptionLink(BlogSubscriptionAction.UNSUBSCRIBE, "t"))
        }
    }

    @Test
    fun `percent-decodes the token`() {
        val link = BlogLink.subscriptionLinkFor(
            "https://interlinedlist.com/api/blog/subscribe/confirm?token=a%2Bb%3Dc",
        )

        assertThat(link?.token).isEqualTo("a+b=c")
    }

    @Test
    fun `near-miss URLs are not claimed`() {
        val nearMisses = listOf(
            // The POST subscribe target — no token, nothing to complete.
            "https://interlinedlist.com/api/blog/subscribe?token=t",
            // A deeper path under the real one.
            "https://interlinedlist.com/api/blog/subscribe/confirm/now?token=t",
            // A prefix trap on the unsubscribe path.
            "https://interlinedlist.com/api/blog/unsubscribed?token=t",
            "https://interlinedlist.com/api/blog/unsubscribe-all?token=t",
            // The bare paths without the /api prefix, which 404 on the live site.
            "https://interlinedlist.com/blog/unsubscribe?token=t",
            "https://interlinedlist.com/unsubscribe?token=t",
            // A look-alike host, and a subdomain that is not the site.
            "https://interlinedlist.com.evil.example/api/blog/unsubscribe?token=t",
            "https://evil.example/api/blog/unsubscribe?token=t",
            // The blog itself, and the app's own blog scheme.
            "https://interlinedlist.com/blog",
            "interlinedlist://blog?token=t",
            // Neither a blog link nor a URI.
            "https://interlinedlist.com/verify-email-change?token=t",
            "mailto:reader@example.com",
            "not a uri at all",
            "",
            null,
        )

        nearMisses.forEach { uri ->
            assertThat(BlogLink.subscriptionLinkFor(uri)).isNull()
        }
    }

    @Test
    fun `a recognised link with no token still parses, with an empty token`() {
        // The app claims these URLs, so it owes the user a "that link didn't work"
        // screen rather than silently doing nothing.
        listOf(
            "https://interlinedlist.com/api/blog/unsubscribe",
            "https://interlinedlist.com/api/blog/unsubscribe?token=",
            "https://interlinedlist.com/api/blog/unsubscribe?token=%20",
            "https://interlinedlist.com/api/blog/unsubscribe?other=x",
        ).forEach { uri ->
            assertThat(BlogLink.subscriptionLinkFor(uri))
                .isEqualTo(BlogSubscriptionLink(BlogSubscriptionAction.UNSUBSCRIBE, ""))
        }
    }

    @Test
    fun `the blog reader parser still ignores the mailing-list URLs`() {
        // Claiming these as "open the blog in a browser" would swallow the action.
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/api/blog/unsubscribe?token=t"))
            .isNull()
        assertThat(BlogLink.webUrlFor("interlinedlist://blog-unsubscribe?token=t")).isNull()
    }

    @Test
    fun `the redirect target says what happened`() {
        assertThat(BlogLink.outcomeFor("https://interlinedlist.com/blog?subscription=confirmed"))
            .isEqualTo(BlogSubscriptionOutcome.CONFIRMED)
        assertThat(BlogLink.outcomeFor("https://interlinedlist.com/blog?subscription=unsubscribed"))
            .isEqualTo(BlogSubscriptionOutcome.UNSUBSCRIBED)
        assertThat(BlogLink.outcomeFor("/blog?subscription=CONFIRMED"))
            .isEqualTo(BlogSubscriptionOutcome.CONFIRMED)
    }

    @Test
    fun `an unrecognised redirect is never reported as a success`() {
        listOf(
            "https://interlinedlist.com/blog?subscription=invalid",
            "https://interlinedlist.com/blog?subscription=",
            "https://interlinedlist.com/blog",
            "https://interlinedlist.com/login",
            "not a uri at all",
            "",
            null,
        ).forEach { location ->
            assertThat(BlogLink.outcomeFor(location)).isEqualTo(BlogSubscriptionOutcome.INVALID)
        }
    }
}

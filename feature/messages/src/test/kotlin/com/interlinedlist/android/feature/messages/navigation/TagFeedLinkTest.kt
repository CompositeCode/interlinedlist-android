package com.interlinedlist.android.feature.messages.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The tag deep link.
 *
 * The canonical URL was read off the live site rather than guessed: the web's
 * message card renders each tag as
 * `<Link href={"/?tag=" + encodeURIComponent(tag)}>`, and
 * `https://interlinedlist.com/?tag=lists` really does serve a tag-filtered feed
 * (`/tag/lists` and `/tags/lists` both 404). These tests pin that shape, so the
 * intent filter and the resolver cannot quietly drift apart from it.
 */
class TagFeedLinkTest {

    @Test
    fun `the web tag URL yields its tag`() {
        assertThat(TagFeedLink.parse("https://interlinedlist.com/?tag=lists")).isEqualTo("lists")
    }

    @Test
    fun `the root without a trailing slash is still a tag URL`() {
        assertThat(TagFeedLink.parse("https://interlinedlist.com?tag=lists")).isEqualTo("lists")
    }

    @Test
    fun `a percent-encoded tag comes back decoded, exactly once`() {
        // The web encodes with encodeURIComponent, so a space is %20 and a comma
        // is %2C. Decoding must restore the tag whole — not half-decoded, and not
        // split on the comma or the spaces.
        val decoded = TagFeedLink.parse(
            "https://interlinedlist.com/?tag=life%20is%20short%2C%20o%20brave%20girl",
        )
        assertThat(decoded).isEqualTo("life is short, o brave girl")
    }

    @Test
    fun `case and punctuation in the tag survive untouched`() {
        assertThat(TagFeedLink.parse("https://interlinedlist.com/?tag=Hanabie.")).isEqualTo("Hanabie.")
    }

    @Test
    fun `www, http and extra query parameters are tolerated`() {
        assertThat(TagFeedLink.parse("http://www.interlinedlist.com/?ref=x&tag=lego"))
            .isEqualTo("lego")
    }

    @Test
    fun `the custom-scheme form resolves the same way`() {
        assertThat(TagFeedLink.parse("interlinedlist://tag?tag=lists")).isEqualTo("lists")
    }

    @Test
    fun `a link to any other page is not a tag link`() {
        // Only the site root carries the tag feed; a `tag` query hung off another
        // page must not hijack that page's own deep link.
        assertThat(TagFeedLink.parse("https://interlinedlist.com/lists/shared/xyz?tag=lists")).isNull()
        assertThat(TagFeedLink.parse("https://interlinedlist.com/messages?tag=lists")).isNull()
        assertThat(TagFeedLink.parse("https://interlinedlist.com/verify-email?token=abc")).isNull()
    }

    @Test
    fun `a foreign host is not a tag link`() {
        assertThat(TagFeedLink.parse("https://example.com/?tag=lists")).isNull()
    }

    @Test
    fun `a root link with no usable tag is not a tag link`() {
        assertThat(TagFeedLink.parse("https://interlinedlist.com/")).isNull()
        assertThat(TagFeedLink.parse("https://interlinedlist.com/?tag=")).isNull()
        assertThat(TagFeedLink.parse("https://interlinedlist.com/?tag=%20")).isNull()
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        assertThat(TagFeedLink.parse("not a uri at all")).isNull()
        assertThat(TagFeedLink.parse("")).isNull()
        assertThat(TagFeedLink.parse(null)).isNull()
    }

    // ---- route mapping -----------------------------------------------------

    @Test
    fun `routeForTagLink maps a tag URL onto the in-app tag feed route`() {
        assertThat(MessagesDestinations.routeForTagLink("https://interlinedlist.com/?tag=lists"))
            .isEqualTo("messages/tag/lists")
    }

    @Test
    fun `routeForTagLink percent-encodes a tag with spaces and punctuation`() {
        // %20 rather than +: Navigation decodes the path segment with Uri.decode,
        // which would hand a "+" straight back as a plus sign.
        assertThat(
            MessagesDestinations.routeForTagLink(
                "https://interlinedlist.com/?tag=life%20is%20short%2C%20o%20brave%20girl",
            ),
        ).isEqualTo("messages/tag/life%20is%20short%2C%20o%20brave%20girl")
    }

    @Test
    fun `routeForTagLink ignores links it does not own`() {
        assertThat(MessagesDestinations.routeForTagLink("https://interlinedlist.com/lists/shared/xyz"))
            .isNull()
        assertThat(MessagesDestinations.routeForTagLink(null)).isNull()
    }

    @Test
    fun `the tag feed route pattern and a built route share one path shape`() {
        // A tag containing a slash must not split into two path segments, or the
        // route would stop matching the pattern.
        val route = MessagesDestinations.tagFeedRoute("a/b")
        assertThat(route).isEqualTo("messages/tag/a%2Fb")
        assertThat(route.count { it == '/' })
            .isEqualTo(MessagesDestinations.TAG_FEED.count { it == '/' })
    }
}

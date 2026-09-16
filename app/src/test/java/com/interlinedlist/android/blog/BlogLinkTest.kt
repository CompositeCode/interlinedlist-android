package com.interlinedlist.android.blog

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The blog is server-rendered and has no public JSON listing endpoint, so every blog
 * destination in this app is ultimately "a web URL we hand to a Custom Tab". These
 * tests pin down both halves of that:
 *
 * - the Account-hub **entry point** resolves to the canonical blog index URL, and
 * - a tapped **deep link** resolves to the canonical URL of the post it names, while
 *   anything that is not a blog link resolves to null.
 *
 * The "must not match" cases matter as much as the happy paths: whatever comes back
 * from here is handed to a browser, so a look-alike host or a `/blogroll`-style prefix
 * trap has to come back as null rather than as a URL we would then open.
 */
class BlogLinkTest {

    // ---- entry point --------------------------------------------------------

    @Test
    fun `the entry point routes to the canonical blog index`() {
        assertThat(BlogLink.INDEX_URL).isEqualTo("https://interlinedlist.com/blog")
    }

    @Test
    fun `postUrl builds a canonical post URL from a slug`() {
        assertThat(BlogLink.postUrl("hello-world"))
            .isEqualTo("https://interlinedlist.com/blog/hello-world")
    }

    // ---- deep links the app owns -------------------------------------------

    @Test
    fun `the custom-scheme blog link resolves to the index`() {
        assertThat(BlogLink.webUrlFor("interlinedlist://blog"))
            .isEqualTo("https://interlinedlist.com/blog")
    }

    @Test
    fun `the custom-scheme post link resolves to that post`() {
        assertThat(BlogLink.webUrlFor("interlinedlist://blog/hello-world"))
            .isEqualTo("https://interlinedlist.com/blog/hello-world")
    }

    @Test
    fun `a shared web post link is normalised to its canonical URL`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/blog/hello-world"))
            .isEqualTo("https://interlinedlist.com/blog/hello-world")
    }

    @Test
    fun `a nested post path is preserved`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/blog/2026/hello-world"))
            .isEqualTo("https://interlinedlist.com/blog/2026/hello-world")
    }

    // ---- tolerated variations ----------------------------------------------

    @Test
    fun `tolerates a www host, mixed case, a trailing slash, query and fragment`() {
        assertThat(
            BlogLink.webUrlFor("  https://WWW.InterlinedList.com/blog/hello-world/?utm_source=x#top  "),
        ).isEqualTo("https://interlinedlist.com/blog/hello-world")
    }

    @Test
    fun `the bare web blog path resolves to the index`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/blog/"))
            .isEqualTo("https://interlinedlist.com/blog")
    }

    @Test
    fun `a percent-encoded slug is passed through unchanged`() {
        assertThat(BlogLink.webUrlFor("interlinedlist://blog/caf%C3%A9-notes"))
            .isEqualTo("https://interlinedlist.com/blog/caf%C3%A9-notes")
    }

    // ---- links the app does not own must not resolve ------------------------

    @Test
    fun `a non-blog link does not resolve`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/lists/shared/xyz")).isNull()
        assertThat(BlogLink.webUrlFor("interlinedlist://lists/abc")).isNull()
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/")).isNull()
    }

    @Test
    fun `a path that merely starts with blog does not resolve`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/blogroll")).isNull()
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/blog-archive/post")).isNull()
    }

    @Test
    fun `a look-alike or foreign host does not resolve`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com.evil.example/blog/post")).isNull()
        assertThat(BlogLink.webUrlFor("https://evil.example/blog/post")).isNull()
    }

    @Test
    fun `a traversal segment does not resolve`() {
        assertThat(BlogLink.webUrlFor("https://interlinedlist.com/blog/../admin")).isNull()
        assertThat(BlogLink.webUrlFor("interlinedlist://blog/./x")).isNull()
    }

    @Test
    fun `garbage input returns null instead of throwing`() {
        assertThat(BlogLink.webUrlFor("not a uri at all")).isNull()
        assertThat(BlogLink.webUrlFor("blog")).isNull()
        assertThat(BlogLink.webUrlFor("")).isNull()
        assertThat(BlogLink.webUrlFor(null)).isNull()
    }
}
